package com.liuitemlib.item;

import com.liuitemlib.LiuItemLibrary;
import com.liuitemlib.config.LibConfig;
import com.liuitemlib.db.DatabaseManager;
import com.liuxingyu.liulib.item.ItemService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 物品库核心：内存缓存 + 跨服增量同步 + 增删改查/发放。
 * 缓存读操作线程安全；DB 写操作由调用线程执行（命令/GUI，均为主线程，数据量小）。
 */
public final class ItemLibraryService {
    private final LiuItemLibrary plugin;
    private final ItemService items;
    private final LibConfig cfg;
    private final DatabaseManager db;
    private final Map<String, LibraryItem> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastRevision = new AtomicLong();
    private volatile RuntimeException lastSyncError;

    public ItemLibraryService(LiuItemLibrary plugin, DatabaseManager db, ItemService items, LibConfig cfg) {
        this.plugin = plugin;
        this.db = db;
        this.items = items;
        this.cfg = cfg;
    }

    /** 启动/重载时全量载入缓存。 */
    public void loadCache() throws Exception {
        List<LibraryItem> all = db.loadAll();
        Map<String, LibraryItem> fresh = new ConcurrentHashMap<>();
        for (LibraryItem item : all) {
            fresh.put(item.id(), item);
        }
        cache.clear();
        cache.putAll(fresh);
        lastRevision.set(db.maxRevision());
    }

    /** 轮询同步：只拉 revision > lastSeen 的条目（含删除标记）。 */
    public void syncNow() throws Exception {
        long seen = lastRevision.get();
        long max = db.maxRevision();
        if (max <= seen) {
            return;
        }
        List<LibraryItem> changed = db.changedSince(seen);
        for (LibraryItem item : changed) {
            if (item.deleted()) {
                cache.remove(item.id());
            } else {
                cache.put(item.id(), item);
            }
        }
        lastRevision.set(Math.max(max, changed.isEmpty() ? seen : changed.get(changed.size() - 1).revision()));
    }

    /** 调度器的安全包装：出错只记一次日志，避免刷屏。 */
    public void syncQuietly() {
        try {
            syncNow();
            lastSyncError = null;
        } catch (Exception e) {
            RuntimeException wrapped = new RuntimeException(e.getMessage(), e);
            if (lastSyncError == null || !String.valueOf(lastSyncError.getMessage()).equals(String.valueOf(e.getMessage()))) {
                plugin.getLogger().warning("跨服同步失败: " + e.getMessage());
            }
            lastSyncError = wrapped;
        }
    }

    public long lastRevision() {
        return lastRevision.get();
    }

    public LibraryItem get(String id) {
        return id == null ? null : cache.get(normalizeId(id));
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public Collection<LibraryItem> all() {
        return cache.values();
    }

    public List<LibraryItem> list(String category) {
        List<LibraryItem> out = new ArrayList<>();
        for (LibraryItem item : cache.values()) {
            if (category == null || category.isBlank() || item.category().equalsIgnoreCase(category.trim())) {
                out.add(item);
            }
        }
        out.sort(Comparator.comparing(LibraryItem::id));
        return out;
    }

    public List<LibraryItem> search(String keyword) {
        String k = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<LibraryItem> out = new ArrayList<>();
        for (LibraryItem item : cache.values()) {
            if (item.id().toLowerCase(Locale.ROOT).contains(k)
                    || item.displayName().toLowerCase(Locale.ROOT).contains(k)
                    || item.category().toLowerCase(Locale.ROOT).contains(k)
                    || item.tags().toLowerCase(Locale.ROOT).contains(k)) {
                out.add(item);
            }
        }
        out.sort(Comparator.comparing(LibraryItem::id));
        return out;
    }

    public List<String> categories() {
        List<String> out = new ArrayList<>();
        for (LibraryItem item : cache.values()) {
            if (!out.contains(item.category())) {
                out.add(item.category());
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    public int count() {
        return cache.size();
    }

    /** 反序列化成真实物品（数量按原物品，超堆叠自动拆）。 */
    public ItemStack build(String id) {
        LibraryItem item = get(id);
        if (item == null) {
            return null;
        }
        try {
            return items.codec().decodeClamped(item.data());
        } catch (Exception e) {
            plugin.getLogger().warning("物品库条目 " + item.id() + " 反序列化失败: " + e.getMessage());
            return null;
        }
    }

    public static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (!id.contains(":")) {
            id = "item:" + id;
        }
        return id;
    }

    public static boolean validId(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String id = normalizeId(raw);
        return id.length() <= 64 && id.matches("[a-z0-9_./:-]+");
    }

    /**
     * 保存手持物品。overwrite=false 且已存在时抛 IllegalStateException。
     *
     * @return 新条目的 revision
     */
    public long save(ItemStack stack, String rawId, String category, String displayName, String actor, boolean overwrite) {
        String id = normalizeId(rawId);
        if (!validId(id)) {
            throw new IllegalArgumentException("非法 ID：只允许 a-z 0-9 _ . / : -，且不超过 64 字符，建议 namespace:key");
        }
        String encoded = items.codec().encode(stack);
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("物品序列化失败（无数据）");
        }
        if (encoded.length() > cfg.maxEncodedLength()) {
            throw new IllegalStateException("物品数据过大：" + encoded.length() + " 字符，上限 " + cfg.maxEncodedLength());
        }
        String hash = md5(encoded);
        String cat = category == null || category.isBlank() ? cfg.defaultCategory() : category.trim().toLowerCase(Locale.ROOT);
        String name = displayName == null ? "" : displayName.trim();
        String material = stack.getType().name();
        long now = System.currentTimeMillis();
        try {
            LibraryItem existing = db.find(id);
            if (existing == null || existing.deleted()) {
                long revision = db.nextRevision();
                LibraryItem item = new LibraryItem(id, revision, name, cat, existing == null ? "" : existing.tags(),
                        material, encoded, hash, actor, now, actor, now, false);
                if (existing != null) {
                    // 已删除条目按新条目复写
                    if (!db.update(existing.revision(), item)) {
                        throw new IllegalStateException("写入冲突，请重试");
                    }
                } else {
                    db.insert(item);
                }
                cache.put(id, item);
                bumpRevision(revision);
                return revision;
            }
            if (!overwrite) {
                throw new IllegalStateException("条目已存在：" + id + "（如需覆盖：/itemlib save -f " + id + "）");
            }
            long revision = db.nextRevision();
            LibraryItem item = existing.withContent(name, cat, material, encoded, hash, revision, actor, now);
            if (!db.update(existing.revision(), item)) {
                throw new IllegalStateException("条目刚被其他服修改，请重试");
            }
            cache.put(id, item);
            bumpRevision(revision);
            return revision;
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("入库失败: " + e.getMessage(), e);
        }
    }

    public boolean delete(String rawId, String actor) {
        return setDeleted(rawId, actor, true);
    }

    public boolean restore(String rawId, String actor) {
        return setDeleted(rawId, actor, false);
    }

    private boolean setDeleted(String rawId, String actor, boolean deleted) {
        String id = normalizeId(rawId);
        try {
            LibraryItem existing = db.find(id);
            if (existing == null) {
                throw new IllegalStateException("条目不存在：" + id);
            }
            if (existing.deleted() == deleted) {
                return false;
            }
            long revision = db.nextRevision();
            LibraryItem item = existing.withLifecycle(revision, actor, System.currentTimeMillis(), deleted);
            if (!db.update(existing.revision(), item)) {
                throw new IllegalStateException("条目刚被其他服修改，请重试");
            }
            if (deleted) {
                cache.remove(id);
            } else {
                cache.put(id, item);
            }
            bumpRevision(revision);
            return true;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("操作失败: " + e.getMessage(), e);
        }
    }

    /** 发放物品；返回实际放入背包的数量，剩余掉落在脚下。必须在主线程调用。 */
    public int give(Player player, String rawId, int amount) {
        LibraryItem item = get(rawId);
        if (item == null) {
            throw new IllegalStateException("条目不存在：" + normalizeId(rawId));
        }
        ItemStack base = build(item.id());
        if (base == null) {
            throw new IllegalStateException("物品反序列化失败：" + item.id());
        }
        int maxStack = Math.max(1, base.getMaxStackSize());
        int remaining = Math.max(1, Math.min(amount, cfg.maxGive()));
        List<ItemStack> stacks = new ArrayList<>();
        while (remaining > 0) {
            int n = Math.min(remaining, maxStack);
            ItemStack copy = base.clone();
            copy.setAmount(n);
            stacks.add(copy);
            remaining -= n;
        }
        int total = 0;
        for (ItemStack stack : stacks) {
            total += stack.getAmount();
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stacks.toArray(new ItemStack[0]));
        int dropped = 0;
        Location loc = player.getLocation();
        for (ItemStack left : leftover.values()) {
            dropped += left.getAmount();
            player.getWorld().dropItemNaturally(loc, left);
        }
        return total - dropped;
    }

    private void bumpRevision(long revision) {
        lastRevision.accumulateAndGet(revision, Math::max);
    }

    private static String md5(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}

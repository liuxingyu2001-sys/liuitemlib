package com.liuitemlib.command;

import com.liuitemlib.LiuItemLibrary;
import com.liuitemlib.gui.LibraryGui;
import com.liuitemlib.item.ItemLibraryService;
import com.liuitemlib.item.LibraryItem;
import com.liuitemlib.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /itemlib 指令入口。 */
public final class ItemLibCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
            "save", "give", "list", "search", "info", "delete", "restore", "gui", "sync", "reload", "help");

    private final LiuItemLibrary plugin;

    public ItemLibCommand(LiuItemLibrary plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.ready()) {
            send(sender, "&c物品库尚未就绪（检查数据库连接与启动日志）");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "save" -> save(sender, args);
                case "give" -> give(sender, args);
                case "list" -> list(sender, args);
                case "search" -> search(sender, args);
                case "info" -> info(sender, args);
                case "delete" -> delete(sender, args);
                case "restore" -> restore(sender, args);
                case "gui" -> gui(sender);
                case "sync" -> sync(sender);
                case "reload" -> reload(sender);
                default -> help(sender, label);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            send(sender, "&c" + e.getMessage());
        } catch (Exception e) {
            send(sender, "&c操作失败：" + e.getMessage());
            plugin.getLogger().warning("指令执行失败: " + e);
        }
        return true;
    }

    private void save(CommandSender sender, String[] args) {
        if (!has(sender, "liuitemlib.admin.save")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "&c只有玩家可以保存手持物品");
            return;
        }
        boolean force = false;
        int i = 1;
        if (args.length > 1 && (args[1].equals("-f") || args[1].equals("--force"))) {
            force = true;
            i = 2;
        }
        if (args.length <= i) {
            send(sender, "&c用法: /itemlib save [-f] <id> [分类] [显示名...]");
            return;
        }
        String rawId = args[i];
        String category = args.length > i + 1 ? args[i + 1] : null;
        String display = args.length > i + 2 ? String.join(" ", Arrays.copyOfRange(args, i + 2, args.length)) : "";
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            send(sender, "&c主手没有物品");
            return;
        }
        long revision = plugin.service().save(hand, rawId, category, display, sender.getName(), force);
        String id = ItemLibraryService.normalizeId(rawId);
        send(sender, "&a已保存 &f" + id + " &7(分类 " + (category == null ? plugin.config().defaultCategory() : category)
                + ", revision " + revision + ", 数据 " + hand.getType() + ")");
    }

    private void give(CommandSender sender, String[] args) {
        if (!has(sender, "liuitemlib.give")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "&c用法: /itemlib give <id> [数量] [玩家]");
            return;
        }
        String id = args[1];
        int amount = 1;
        String targetName = null;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                targetName = args[2];
            }
        }
        if (args.length >= 4) {
            targetName = args[3];
        }
        Player target;
        if (targetName == null) {
            if (!(sender instanceof Player p)) {
                send(sender, "&c控制台发放需要指定玩家：/itemlib give <id> [数量] <玩家>");
                return;
            }
            target = p;
        } else {
            target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                send(sender, "&c玩家不在线：" + targetName);
                return;
            }
        }
        if (amount <= 0) {
            send(sender, "&c数量必须为正整数");
            return;
        }
        int placed = plugin.service().give(target, id, amount);
        String normalized = ItemLibraryService.normalizeId(id);
        String suffix = placed < amount ? " &7(背包已满，其余掉落在脚下)" : "";
        send(sender, "&a已发放 &f" + normalized + " &7x" + amount + " &a→ &f" + target.getName() + suffix);
    }

    private void list(CommandSender sender, String[] args) {
        if (!has(sender, "liuitemlib.use")) {
            return;
        }
        String category = args.length > 1 ? args[1] : null;
        List<LibraryItem> items = plugin.service().list(category);
        if (items.isEmpty()) {
            send(sender, "&7没有条目" + (category == null ? "" : "（分类 " + category + "）"));
            return;
        }
        send(sender, "&8—— &f物品库 &8(" + (category == null ? "全部" : category) + ", " + items.size() + " 条) &8——");
        for (int i = 0; i < Math.min(items.size(), 30); i++) {
            LibraryItem item = items.get(i);
            send(sender, "&7- &f" + item.id() + " &8| &f" + label(item) + " &8| &7" + item.category());
        }
        if (items.size() > 30) {
            send(sender, "&7…… 共 " + items.size() + " 条，仅显示前 30 条，可用 /itemlib search 精确查找");
        }
    }

    private void search(CommandSender sender, String[] args) {
        if (!has(sender, "liuitemlib.use")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "&c用法: /itemlib search <关键字>");
            return;
        }
        List<LibraryItem> items = plugin.service().search(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        if (items.isEmpty()) {
            send(sender, "&7没有匹配的条目");
            return;
        }
        send(sender, "&8—— &f搜索结果 &8(" + items.size() + " 条) &8——");
        for (int i = 0; i < Math.min(items.size(), 30); i++) {
            LibraryItem item = items.get(i);
            send(sender, "&7- &f" + item.id() + " &8| &f" + label(item) + " &8| &7" + item.category());
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (!has(sender, "liuitemlib.use")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "&c用法: /itemlib info <id>");
            return;
        }
        LibraryItem item = plugin.service().get(args[1]);
        if (item == null) {
            send(sender, "&c条目不存在：" + ItemLibraryService.normalizeId(args[1]));
            return;
        }
        send(sender, "&8—— &f" + item.id() + " &8——");
        send(sender, "&7显示名: &f" + (item.displayName().isBlank() ? "(无)" : item.displayName()));
        send(sender, "&7分类: &f" + item.category() + (item.tags().isBlank() ? "" : " &7标签: &f" + item.tags()));
        send(sender, "&7材质: &f" + item.material() + " &7数据长度: &f" + item.data().length() + " 字符");
        send(sender, "&7创建: &f" + item.createdBy() + " &7@ &f" + time(item.createdAt()));
        send(sender, "&7修改: &f" + item.updatedBy() + " &7@ &f" + time(item.updatedAt()));
        send(sender, "&7版本: &f" + item.revision() + " &7哈希: &f" + item.dataHash());
        if (item.deleted()) {
            send(sender, "&c该条目已删除，可用 /itemlib restore " + item.id() + " 恢复");
        }
    }

    private void delete(CommandSender sender, String[] args) {
        if (!has(sender, "liuitemlib.admin.delete")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "&c用法: /itemlib delete <id>");
            return;
        }
        boolean changed = plugin.service().delete(args[1], sender.getName());
        String id = ItemLibraryService.normalizeId(args[1]);
        send(sender, changed ? "&a已删除 &f" + id + " &7（软删除，可用 /itemlib restore 恢复）" : "&7条目不存在或已是删除状态");
    }

    private void restore(CommandSender sender, String[] args) {
        if (!has(sender, "liuitemlib.admin.delete")) {
            return;
        }
        if (args.length < 2) {
            send(sender, "&c用法: /itemlib restore <id>");
            return;
        }
        boolean changed = plugin.service().restore(args[1], sender.getName());
        String id = ItemLibraryService.normalizeId(args[1]);
        send(sender, changed ? "&a已恢复 &f" + id : "&7条目不存在或未处于删除状态");
    }

    private void gui(CommandSender sender) {
        if (!has(sender, "liuitemlib.use")) {
            return;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "&c只有玩家可以打开物品库界面");
            return;
        }
        LibraryGui.openMain(plugin, player);
    }

    private void sync(CommandSender sender) {
        if (!has(sender, "liuitemlib.use")) {
            return;
        }
        try {
            plugin.service().syncNow();
            send(sender, "&a同步完成：物品 &f" + plugin.service().count() + " &a条，revision &f" + plugin.service().lastRevision());
        } catch (Exception e) {
            send(sender, "&c同步失败：" + e.getMessage());
        }
    }

    private void reload(CommandSender sender) {
        if (!has(sender, "liuitemlib.admin.reload")) {
            return;
        }
        plugin.reloadAll();
        if (plugin.ready()) {
            send(sender, "&a重载完成：物品 &f" + plugin.service().count() + " &a条，revision &f" + plugin.service().lastRevision());
        } else {
            send(sender, "&c重载失败，请检查控制台日志（数据库连接）");
        }
    }

    private void help(CommandSender sender, String label) {
        send(sender, "&8—— &f物品库 LiuItemLibrary &8——");
        send(sender, "&f/" + label + " save [-f] <id> [分类] [显示名] &7手持存入库");
        send(sender, "&f/" + label + " give <id> [数量] [玩家] &7发放物品");
        send(sender, "&f/" + label + " list [分类] &7列出条目");
        send(sender, "&f/" + label + " search <关键字> &7搜索");
        send(sender, "&f/" + label + " info <id> &7查看详情");
        send(sender, "&f/" + label + " delete|restore <id> &7软删除/恢复");
        send(sender, "&f/" + label + " gui &7打开界面");
        send(sender, "&f/" + label + " sync &7立即跨服同步");
        send(sender, "&f/" + label + " reload &7重载配置");
        send(sender, "&7ID 规则：namespace:key，如 &fgift:spring_2026 &7（省略 namespace 时补 &fitem:&7）");
    }

    private static String label(LibraryItem item) {
        if (!item.displayName().isBlank()) {
            return item.displayName();
        }
        return item.material();
    }

    private static String time(long millis) {
        if (millis <= 0) {
            return "-";
        }
        return java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static boolean has(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        send(sender, "&c没有权限：" + permission);
        return false;
    }

    private static void send(CommandSender sender, String raw) {
        sender.sendMessage(Items.text(raw));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(sub);
                }
            }
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!plugin.ready()) {
            return List.of();
        }
        if (args.length == 2) {
            return switch (sub) {
                case "save", "give", "info", "delete", "restore" -> ids(args[1]);
                case "list" -> categories(args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && sub.equals("give")) {
            return List.of("1", "16", "64");
        }
        if (args.length == 3 && sub.equals("save")) {
            return categories(args[2]);
        }
        return List.of();
    }

    private List<String> ids(String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (LibraryItem item : plugin.service().all()) {
            if (item.id().startsWith(p)) {
                out.add(item.id());
            }
            if (out.size() >= 50) {
                break;
            }
        }
        return out;
    }

    private List<String> categories(String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix.toLowerCase(Locale.ROOT);
        for (String category : plugin.service().categories()) {
            if (category.startsWith(p)) {
                out.add(category);
            }
        }
        return out;
    }
}

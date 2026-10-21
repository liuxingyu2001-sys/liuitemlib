package com.liuitemlib.gui;

import com.liuitemlib.LiuItemLibrary;
import com.liuitemlib.item.LibraryItem;
import com.liuitemlib.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 物品库 GUI：分类 → 条目列表（分页）。 */
public final class LibraryGui {
    private static final int PAGE_SIZE = 45;

    private LibraryGui() {
    }

    public static void openMain(LiuItemLibrary plugin, Player player) {
        if (plugin.service() == null) {
            player.sendMessage(Items.text("&c物品库尚未就绪"));
            return;
        }
        List<String> categories = plugin.service().categories();
        CategoryHolder holder = new CategoryHolder(categories);
        Inventory inv = Bukkit.createInventory(holder, 54, Items.text("&8物品库 &7· 分类"));
        holder.attach(inv);
        int slot = 0;
        for (String category : categories) {
            if (slot >= 45) {
                break;
            }
            List<LibraryItem> items = plugin.service().list(category);
            ItemStack icon = items.isEmpty() ? null : plugin.service().build(items.get(0).id());
            if (icon == null) {
                icon = new ItemStack(Material.CHEST);
            }
            icon = icon.clone();
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(Items.text("&b" + category));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Items.text("&7共 &f" + items.size() + " &7件物品"));
                lore.add(Items.text("&7左键浏览"));
                meta.lore(lore);
                icon.setItemMeta(meta);
            }
            inv.setItem(slot++, icon);
        }
        if (categories.isEmpty()) {
            inv.setItem(22, button(Material.BARRIER, "&7物品库还是空的", "&7用 /itemlib save <id> 保存手持物品"));
        }
        inv.setItem(49, button(Material.BARRIER, "&c关闭"));
        player.openInventory(inv);
    }

    public static void openCategory(LiuItemLibrary plugin, Player player, String category, int page) {
        if (plugin.service() == null) {
            player.sendMessage(Items.text("&c物品库尚未就绪"));
            return;
        }
        List<LibraryItem> items = plugin.service().list(category);
        int pages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        ItemHolder holder = new ItemHolder(category, current);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Items.text("&8物品库 &7· " + category + " &8(" + (current + 1) + "/" + pages + ")"));
        holder.attach(inv);
        int start = current * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < items.size(); i++) {
            LibraryItem item = items.get(start + i);
            ItemStack icon = plugin.service().build(item.id());
            if (icon == null) {
                icon = new ItemStack(Items.material(item.material()) == null ? Material.CHEST : Items.material(item.material()));
            }
            inv.setItem(i, Items.icon(icon, item));
        }
        inv.setItem(45, button(Material.ARROW, "&7返回分类"));
        if (current > 0) {
            inv.setItem(48, button(Material.PAPER, "&e上一页", "&7第 " + current + " 页"));
        }
        if (current + 1 < pages) {
            inv.setItem(50, button(Material.PAPER, "&e下一页", "&7第 " + (current + 2) + " 页"));
        }
        inv.setItem(49, button(Material.BARRIER, "&c关闭"));
        player.openInventory(inv);
    }

    private static ItemStack button(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Items.text(name));
            List<net.kyori.adventure.text.Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Items.text(line));
            }
            if (!lines.isEmpty()) {
                meta.lore(lines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 分类总览持有者。 */
    public static final class CategoryHolder implements InventoryHolder {
        private final List<String> categories;
        private Inventory inventory;

        CategoryHolder(List<String> categories) {
            this.categories = categories;
        }

        void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        public List<String> categories() {
            return categories;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** 条目列表持有者。 */
    public static final class ItemHolder implements InventoryHolder {
        private final String category;
        private final int page;
        private Inventory inventory;

        ItemHolder(String category, int page) {
            this.category = category;
            this.page = page;
        }

        void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        public String category() {
            return category;
        }

        public int page() {
            return page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** 事件监听。 */
    public static final class Listener implements org.bukkit.event.Listener {
        private final LiuItemLibrary plugin;

        public Listener(LiuItemLibrary plugin) {
            this.plugin = plugin;
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            InventoryHolder holder = event.getView().getTopInventory().getHolder();
            if (holder instanceof CategoryHolder || holder instanceof ItemHolder) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            Inventory top = event.getView().getTopInventory();
            InventoryHolder holder = top.getHolder();
            if (!(holder instanceof CategoryHolder) && !(holder instanceof ItemHolder)) {
                return;
            }
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top) {
                return;
            }
            if (plugin.service() == null) {
                return;
            }
            int slot = event.getRawSlot();
            if (holder instanceof CategoryHolder categories) {
                if (slot == 49) {
                    player.closeInventory();
                    return;
                }
                if (slot >= 0 && slot < categories.categories().size()) {
                    openCategory(plugin, player, categories.categories().get(slot), 0);
                }
                return;
            }
            ItemHolder menu = (ItemHolder) holder;
            if (slot == 45) {
                openMain(plugin, player);
                return;
            }
            if (slot == 48 && menu.page() > 0) {
                openCategory(plugin, player, menu.category(), menu.page() - 1);
                return;
            }
            if (slot == 50) {
                openCategory(plugin, player, menu.category(), menu.page() + 1);
                return;
            }
            if (slot == 49) {
                player.closeInventory();
                return;
            }
            List<LibraryItem> items = plugin.service().list(menu.category());
            int index = menu.page() * PAGE_SIZE + slot;
            if (slot < PAGE_SIZE && index < items.size()) {
                LibraryItem item = items.get(index);
                if (event.isRightClick()) {
                    player.sendMessage(Items.text("&8ID: &f" + item.id() + " &8| &f" + item.material()
                            + " &8| &7分类 " + item.category() + " &8| &7数据 " + item.data().length() + " 字符"));
                    return;
                }
                if (!player.hasPermission("liuitemlib.give")) {
                    player.sendMessage(Items.text("&c没有权限发放物品（liuitemlib.give）"));
                    return;
                }
                try {
                    int placed = plugin.service().give(player, item.id(), 1);
                    player.sendMessage(Items.text("&a已发放 &f" + item.id() + " &7x1"
                            + (placed < 1 ? " &7(背包已满，已掉落)" : "")));
                } catch (IllegalStateException e) {
                    player.sendMessage(Items.text("&c发放失败：" + e.getMessage()));
                }
            }
        }
    }
}

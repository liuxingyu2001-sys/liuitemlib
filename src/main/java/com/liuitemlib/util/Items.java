package com.liuitemlib.util;

import com.liuitemlib.item.LibraryItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 物品显示 / 颜色文本工具。 */
public final class Items {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Items() {
    }

    public static Component text(String raw) {
        return LEGACY.deserialize(raw == null ? "" : raw);
    }

    public static Material material(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return Material.matchMaterial(name.trim());
    }

    public static ItemStack icon(ItemStack real, LibraryItem item) {
        if (real == null) {
            return null;
        }
        ItemStack copy = real.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(text("&8ID: &f" + item.id()));
            lore.add(text("&8分类: &f" + item.category()));
            if (!item.displayName().isBlank()) {
                lore.add(text("&8名称: &f" + item.displayName()));
            }
            lore.add(text("&8发送人: &f" + (item.createdBy().isBlank() ? "-" : item.createdBy())));
            lore.add(text("&8版本: &f" + item.revision() + (item.deleted() ? " &c(已删除)" : "")));
            lore.add(text(" "));
            lore.add(text("&e左键 &7发放 1 个"));
            meta.lore(lore);
            copy.setItemMeta(meta);
        }
        return copy;
    }
}

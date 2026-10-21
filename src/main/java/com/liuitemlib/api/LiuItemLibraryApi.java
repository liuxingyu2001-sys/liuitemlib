package com.liuitemlib.api;

import com.liuitemlib.LiuItemLibrary;
import com.liuitemlib.item.ItemLibraryService;
import com.liuitemlib.item.LibraryItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 供其他插件调用的静态 API。
 *
 * <pre>
 *   LibraryItem item = LiuItemLibraryApi.get("gift:spring");
 *   ItemStack stack  = LiuItemLibraryApi.build("gift:spring");
 *   LiuItemLibraryApi.give(player, "gift:spring", 1);
 *   String id = LiuItemLibraryApi.save(heldItem, "gift:spring", "gift", "春节礼包", "console", true);
 * </pre>
 */
public final class LiuItemLibraryApi {
    private static volatile LiuItemLibrary plugin;

    private LiuItemLibraryApi() {
    }

    public static void install(LiuItemLibrary instance) {
        plugin = instance;
    }

    public static boolean available() {
        return plugin != null && plugin.service() != null;
    }

    private static ItemLibraryService service() {
        LiuItemLibrary p = plugin;
        if (p == null || p.service() == null) {
            throw new IllegalStateException("LiuItemLibrary 未就绪");
        }
        return p.service();
    }

    public static LibraryItem get(String id) {
        return service().get(id);
    }

    public static boolean exists(String id) {
        return service().exists(id);
    }

    public static ItemStack build(String id) {
        return service().build(id);
    }

    public static List<LibraryItem> list(String category) {
        return service().list(category);
    }

    public static List<LibraryItem> search(String keyword) {
        return service().search(keyword);
    }

    public static List<String> categories() {
        return service().categories();
    }

    public static int count() {
        return service().count();
    }

    public static String save(ItemStack stack, String id, String category, String displayName, String actor, boolean overwrite) {
        service().save(stack, id, category, displayName, actor, overwrite);
        return ItemLibraryService.normalizeId(id);
    }

    public static boolean delete(String id, String actor) {
        return service().delete(id, actor);
    }

    public static boolean restore(String id, String actor) {
        return service().restore(id, actor);
    }

    /** 主线程调用。 */
    public static int give(Player player, String id, int amount) {
        return service().give(player, id, amount);
    }

    public static long lastRevision() {
        return service().lastRevision();
    }
}

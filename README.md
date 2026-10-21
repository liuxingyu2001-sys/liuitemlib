# LiuItemLibrary 物品库

自定义物品集中保存 + **跨服共享** + 统一发放。物品数据用 LiuLib `ItemCodec` 完整序列化（原版 / CraftEngine / 自定义 NBT / PDC 全保真），
所有服务器共用一份 MySQL 数据，改一次、全服生效。

## 前置

- Paper / Leaf 1.21+
- **LiuLib 1.0.4+**（硬依赖，`depend: [LiuLib]`）
- 跨服共享用 MySQL（MariaDB 8+/10+ 均可）；单服调试可用 SQLite
- 各服必须使用**相同版本的 LiuLib**（物品编码格式一致）和**同一套 CraftEngine 物品定义**，否则本服反序列化会失败

## 安装

1. 把 `Liu-LiuItemLibrary-<版本>.jar` 放进 `plugins/`，启动一次生成 `plugins/liuitemlib/config.yml`
2. 修改数据库配置（见下），`/itemlib reload` 或重启生效
3. `/itemlib save <id>` 保存手持物品即开始使用

## 配置

```yaml
db:
  type: mysql                    # sqlite = 单服调试；mysql = 跨服共享
  host: 127.0.0.1
  port: 3306
  database: liuitemlib           # 不存在会自动创建（auto-create-database: true）
  user: root
  password: "***"
  table-prefix: liuitemlib       # 表名：{prefix}_items / {prefix}_meta
  pool-size: 5

settings:
  sync-interval-seconds: 5       # 跨服轮询间隔
  max-encoded-length: 60000      # 单个物品编码长度上限（一般物品 100~2000 字符）
  default-category: default
  max-give: 2304                 # 单次发放上限
```

多台服务器指向同一个 `database`（或同一实例的不同库都行，但必须是同一份数据）即完成共享。

## 指令

| 指令 | 说明 | 权限 |
|---|---|---|
| `/itemlib save [-f] <id> [分类] [显示名...]` | 把主手物品存入库；`-f` 覆盖已存在条目 | `liuitemlib.admin.save` |
| `/itemlib give <id> [数量] [玩家]` | 发放物品（背包满自动掉落脚下） | `liuitemlib.give` |
| `/itemlib list [分类]` | 列出条目（最多显示 30 条） | `liuitemlib.use` |
| `/itemlib search <关键字>` | 按 ID / 显示名 / 分类 / 标签搜索 | `liuitemlib.use` |
| `/itemlib info <id>` | 详情：分类、材质、数据长度、创建人、revision、哈希 | `liuitemlib.use` |
| `/itemlib delete <id>` / `restore <id>` | 软删除 / 恢复 | `liuitemlib.admin.delete` |
| `/itemlib gui` | 分类 → 条目界面；左键发放 1 个，右键看 ID | `liuitemlib.use` |
| `/itemlib sync` | 立即跨服同步 | `liuitemlib.use` |
| `/itemlib reload` | 重载配置 + 重建连接 + 全量同步 | `liuitemlib.admin.reload` |

别名：`/il`、`/liuitemlib`。

**ID 规则**：`namespace:key`，只允许 `a-z 0-9 _ . / : -`，≤64 字符；省略 namespace 时自动补 `item:`。
例如 `gift:spring_2026`、`shop:vip_card`、`item:custom_paper`。

## 跨服同步机制

- 每条记录带全局自增 `revision`，写入方提交后**立即**更新本服缓存
- 其它服每 `sync-interval-seconds` 秒查一次 `MAX(revision)`，有变化就增量拉取 `revision > lastSeen`
- 删除是软删除（`deleted=1`），同步到其它服后从缓存移除，不会误发
- 并发写同一条：`UPDATE ... WHERE revision = ?` 乐观锁，冲突提示"条目刚被其他服修改，请重试"
- 因此：A 服改完，B 服最迟 5 秒可见；`/itemlib sync` 可强制立即拉取

## 给其他插件用（Java API）

```java
// 取物品数据
LibraryItem item = LiuItemLibraryApi.get("gift:spring_2026");
ItemStack stack  = LiuItemLibraryApi.build("gift:spring_2026");

// 发放（主线程）
LiuItemLibraryApi.give(player, "gift:spring_2026", 3);

// 存物品（返回规范化后的 id）
LiuItemLibraryApi.save(heldItem, "shop:vip_card", "vip", "VIP 卡", "console", true);

// 列表 / 搜索
List<LibraryItem> items = LiuItemLibraryApi.search("春");
```

商店等插件后续可通过 `ItemProvider` 前缀 `lib` 直接引用：`item: "lib:gift:spring_2026"`（v2 计划）。

## 数据表

```sql
{prefix}_items(id, revision, display_name, category, tags, material, data, data_hash,
               created_by, created_at, updated_by, updated_at, deleted)
{prefix}_meta(k, v)   -- k='revision' 全局版本计数器
```

- `data` 是 LiuLib ItemCodec 编码串（Base64，含完整物品 NBT），不要手改
- `data_hash` 是数据 MD5，可用来查重/校验
- 备份：直接 `mysqldump` 整库即可；恢复后 `revision` 保持单调即可继续同步

## 限制

- 所有服 LiuLib 版本必须一致；跨版本请先升级所有服再改物品库
- CE 物品需要各服 CraftEngine 加载同名物品，否则本服 `build` 失败并告警
- 物品库只存物品数据，不含价格/发放逻辑；经济与商店逻辑仍由各插件自己负责

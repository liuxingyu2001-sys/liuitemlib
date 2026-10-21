# LiuItemLibrary 项目约定

## 定位
物品库基础设施插件：**只做物品存取 + 跨服同步 + 发放**，不引入经济/商店/奖励逻辑。
其它插件（liushop、liupointshop、签到、礼包等）通过 `LiuItemLibraryApi` 或（后续）`lib:` ItemProvider 引用。

## 技术栈
- Java 17，Maven，Paper API 1.21.11（`provided`）
- 硬依赖 LiuLib（`provided`，`depend: [LiuLib]`），物品编码统一走 `LiuLib.items().codec()`
- HikariCP + mysql-connector-j / sqlite-jdbc，均由 `plugin.yml` 的 `libraries` 在运行时加载（pom 里 `provided`）
- 线程：GUI/发放/背包操作在主线程；同步轮询用异步任务，只访问缓存与数据库

## 代码结构
- `item/ItemLibraryService`：缓存 + 同步 + 增删改查/发放的核心；新增业务方法放这里
- `db/DatabaseManager`：SQL 与连接池；schema 变更要同时兼容 MySQL 与 SQLite
- `config/LibConfig` + `config/DbConfig`：配置解析，新增配置项要同步 `config.yml` 默认值与 README
- `command/ItemLibCommand`：指令 + Tab 补全；新指令要加权限并在 README 表格登记
- `gui/LibraryGui`：分类/条目界面，holder 区分菜单类型
- `api/LiuItemLibraryApi`：对外静态 API，保持向后兼容（只加不删）

## 数据与兼容
- `data` 列 = LiuLib ItemCodec 编码串，**不要**手写解析；编解码只经由 `ItemService`
- 写入必须分配新的全局 `revision`（`DatabaseManager.nextRevision`），更新用乐观锁 `WHERE revision = ?`
- 删除一律软删除（`deleted=1`），保留数据便于恢复与审计
- 表名统一 `{prefix}_items` / `{prefix}_meta`；MySQL 不支持 `CREATE INDEX IF NOT EXISTS`，建索引要 try/catch
- 跨服可见性 ≤ `sync-interval-seconds`；写操作立即更新本服缓存并推进 `lastRevision`

## 构建与验证
- `mvn -o clean package`；产物 `target/Liu-LiuItemLibrary-<version>.jar`
- 单服验证：SQLite（默认配置）→ 保存/发放/GUI/`sync`
- 跨服验证：两台服（或临时 MariaDB + 直接改库模拟别服写入）确认增量同步与删除传播
- 交付前检查 `plugin.yml` 的 `libraries`、`depend`、权限子节点与实际代码权限字符串一致

## 不做的事
- 不自动递增版本/分发/提交，除非用户明确要求（按 `/mvnp` 技能流程）
- 不把数据库密码写进日志、README 或提交内容

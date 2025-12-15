## Rust 基础

### Widnows 系统 Rust 环境安装

> 前置安装 [Visual C++ Build Tools](https://visualstudio.microsoft.com/zh-hans/visual-cpp-build-tools/)

`rustup` 是 Rust 的安装程序，也是它的版本管理程序。

下载 `rustup` 安装包：[Microsoft C++ Build Tools](https://www.rust-lang.org/tools/install)

下载完成后双击 `rustup-init.exe` 安装 `rustup`

如果前面没安装 [Microsoft C++ Build Tools](https://www.rust-lang.org/tools/install)，安装过程中可能会提示缺少该工具，直接回车应用推荐安装即可。

安装完成后会自带安装 `visual studio`，这个会被公司监控警告。

验证是否安装成功：

| 命令        | 说明            |
| ----------- | --------------- |
| `rustup -V` | Rust 版本管理器 |
| `rustc -V`  | Rust 编译器     |
| `cargo -V`  | Rust 包管理器   |

正常来说如果安装完成，这三个都会有版本号输出。

### Rust 编辑器

推荐 VSCode 插件：`rust-analyzer`

### Rust 包管理器 Cargo

常用命令示例：

| 命令                                | 说明                                     |
| ----------------------------------- | ---------------------------------------- |
| `cargo new hello_world`             | 创建新项目（含新目录）                   |
| `cargo new hello_lib --lib`         | 创建库项目                               |
| `cargo init`                        | 在现有空目录中初始化项目                 |
| `cargo init --lib`                  | 初始化库项目                             |
| `cargo build`                       | 构建 debug 版本 (target/debug)           |
| `cargo build --release`             | 构建 release 版本 (target/release)       |
| `cargo run`                         | 构建并运行 (debug)                       |
| `cargo run --release`               | 构建并运行 (release)                     |
| `cargo run --bin server`            | 运行指定二进制 (workspace 或多 bin 场景) |
| `cargo run --example demo`          | 运行 examples/ 下示例                    |
| `cargo check`                       | 仅类型检查，速度快                       |
| `cargo test`                        | 运行测试                                 |
| `cargo test -- --nocapture`         | 显示测试输出                             |
| `cargo bench`                       | 运行基准（需 nightly 或 criterion）      |
| `cargo fmt`                         | 使用 rustfmt 格式化                      |
| `cargo fmt -- --check`              | 仅检查是否已格式化                       |
| `cargo clippy`                      | 运行 clippy 代码静态分析                 |
| `cargo clippy -- -D warnings`       | 将所有警告视为错误                       |
| `cargo doc`                         | 生成文档 (target/doc)                    |
| `cargo doc --open`                  | 生成后自动在浏览器打开                   |
| `cargo add serde`                   | 添加依赖（需安装 cargo-edit）            |
| `cargo add serde --features derive` | 添加依赖并启用特性                       |
| `cargo rm serde`                    | 移除依赖                                 |
| `cargo upgrade`                     | 升级依赖版本                             |
| `cargo update`                      | 根据 Cargo.lock 规则更新依赖             |
| `cargo update -p serde`             | 更新指定包                               |
| `cargo tree`                        | 查看依赖树（需 cargo-tree）              |
| `cargo tree -i serde`               | 反向依赖查询                             |
| `cargo metadata`                    | 输出元数据（JSON）                       |
| `cargo clean`                       | 清理构建产物                             |
| `cargo fix`                         | 自动应用编译器建议                       |
| `cargo install cargo-watch`         | 安装二进制工具                           |
| `cargo uninstall cargo-watch`       | 卸载二进制工具                           |
| `cargo install --path .`            | 安装当前项目为二进制                     |
| `cargo publish`                     | 发布 crate（需配置 crates.io 账号）      |
| `cargo package`                     | 打包检查（不发布）                       |
| `cargo login <token>`               | 登录 crates.io                           |
| `cargo config get`                  | 查看当前配置                             |
| `cargo config edit`                 | 编辑 .cargo/config.toml                  |
| `cargo build --features foo,bar`    | 启用特性                                 |
| `cargo build --no-default-features` | 不启用默认特性                           |
| `cargo build --all-features`        | 启用全部特性                             |
| `cargo workspace list`              | 列出成员（1.70+）                        |
| `cargo build -p crate_name`         | 构建指定成员                             |

## Rust 基础

### Windows 系统 Rust 环境安装

> 前置安装 [Visual C++ Build Tools](https://visualstudio.microsoft.com/zh-hans/visual-cpp-build-tools/)

`rustup` 是 Rust 的安装程序，也是它的版本管理程序。

下载并运行 `rustup-init.exe` 按提示安装 `rustup`（也可使用官网一键命令）。如果你没有安装 Visual C++ Build Tools，安装过程中会提示并建议一并安装。

验证是否安装成功：

| 命令        | 说明            |
| ----------- | --------------- |
| `rustup -V` | Rust 版本管理器 |
| `rustc -V`  | Rust 编译器     |
| `cargo -V`  | Rust 包管理器   |

正常来说如果安装完成，这三个都会有版本号输出。

### Rust 编辑器

推荐 VSCode 插件：`rust-analyzer`

### Rust 包管理器 Cargo

常用命令示例：

| 命令                                | 说明                                     |
| ----------------------------------- | ---------------------------------------- |
| `cargo new hello_world`             | 创建新项目（含新目录）                   |
| `cargo new hello_lib --lib`         | 创建库项目                               |
| `cargo init`                        | 在现有空目录中初始化项目                 |
| `cargo init --lib`                  | 初始化库项目                             |
| `cargo build`                       | 构建 debug 版本 (target/debug)           |
| `cargo build --release`             | 构建 release 版本 (target/release)       |
| `cargo run`                         | 构建并运行 (debug)                       |
| `cargo run --release`               | 构建并运行 (release)                     |
| `cargo run --bin server`            | 运行指定二进制 (workspace 或多 bin 场景) |
| `cargo run --example demo`          | 运行 examples/ 下示例                    |
| `cargo check`                       | 仅类型检查，速度快                       |
| `cargo test`                        | 运行测试                                 |
| `cargo test -- --nocapture`         | 显示测试输出                             |
| `cargo bench`                       | 运行基准（需 nightly 或 criterion）      |
| `cargo fmt`                         | 使用 rustfmt 格式化                      |
| `cargo fmt -- --check`              | 仅检查是否已格式化                       |
| `cargo clippy`                      | 运行 clippy 代码静态分析                 |
| `cargo clippy -- -D warnings`       | 将所有警告视为错误                       |
| `cargo doc`                         | 生成文档 (target/doc)                    |
| `cargo doc --open`                  | 生成后自动在浏览器打开                   |
| `cargo add serde`                   | 添加依赖（需安装 cargo-edit）            |
| `cargo add serde --features derive` | 添加依赖并启用特性                       |
| `cargo rm serde`                    | 移除依赖                                 |
| `cargo upgrade`                     | 升级依赖版本                             |
| `cargo update`                      | 根据 Cargo.lock 规则更新依赖             |
| `cargo update -p serde`             | 更新指定包                               |
| `cargo tree`                        | 查看依赖树（需 cargo-tree）              |
| `cargo tree -i serde`               | 反向依赖查询                             |
| `cargo metadata`                    | 输出元数据（JSON）                       |
| `cargo clean`                       | 清理构建产物                             |
| `cargo fix`                         | 自动应用编译器建议                       |
| `cargo install cargo-watch`         | 安装二进制工具                           |
| `cargo uninstall cargo-watch`       | 卸载二进制工具                           |
| `cargo install --path .`            | 安装当前项目为二进制                     |
| `cargo publish`                     | 发布 crate（需配置 crates.io 账号）      |
| `cargo package`                     | 打包检查（不发布）                       |
| `cargo login <token>`               | 登录 crates.io                           |
| `cargo config get`                  | 查看当前配置                             |
| `cargo config edit`                 | 编辑 .cargo/config.toml                  |
| `cargo build --features foo,bar`    | 启用特性                                 |
| `cargo build --no-default-features` | 不启用默认特性                           |
| `cargo build --all-features`        | 启用全部特性                             |
| `cargo workspace list`              | 列出成员（1.70+）                        |
| `cargo build -p crate_name`         | 构建指定成员                             |

说明（部分要点）：

- 需额外安装的子命令：cargo-add / rm / upgrade 属于 cargo-edit；cargo tree 属于 cargo-tree。
- 常用三阶段：check（快） -> clippy（质量） -> test（正确性） -> build/run。
- 发布前建议：cargo fmt && cargo clippy -D warnings && cargo test && cargo package。
- 生产构建使用 --release 以获得优化后的二进制。
- features 用于按需裁剪功能，减少体积与编译时间。
- 工作空间 (workspace) 适合管理多 crate 单体仓库。
- 监听自动重建可用 cargo watch -x run （需安装 cargo-watch）。
- 文档注释使用 ///，示例用代码块；文档测试将随 cargo test 执行。
- 依赖冲突或体积分析可结合 cargo tree 与 cargo metadata。
- CARGO_TARGET_DIR 可指定构建目录以复用缓存。

#### Cargo.toml

Cargo.toml 是 Rust 项目的配置文件，使用 TOML 格式编写。它定义了项目的元数据、依赖项、构建脚本等信息。

示例（含常见段落与注释）：

```toml
[package]
name = "hello_world"
version = "0.1.0"          # 语义化版本：主.次.补丁
edition = "2021"           # Rust 版本特性集
description = "A simple learning project"
license = "MIT"

[dependencies]
serde = { version = "1.0", features = ["derive"] }   # 序列化/反序列化
tokio = { version = "1", features = ["rt-multi-thread", "macros"], optional = true } # 需要异步再启用

[dev-dependencies]
serde_json = "1.0"          # 仅测试或示例使用

[features]
default = []                # 默认尽量精简
runtime = ["tokio"]         # 使用异步：cargo run --features runtime
```

| 段落                     | 说明                                                                               |
| ------------------------ | ---------------------------------------------------------------------------------- |
| `[package]`              | 定义基础元数据，rust-version 指定最低兼容编译器版本便于 CI 检查                    |
| `[features]`             | 用 optional 依赖 + 组合特性实现可裁剪功能与按需加载                                |
| `profile.*`              | 调优构建：发布二进制可启用 LTO / 减少 codegen-units / panic = abort 以减小体积     |
| `workspace.dependencies` | 统一版本，减少重复（1.64+）                                                        |
| `patch.crates-io`        | 用于临时覆盖上游依赖（热修复、本地调试）                                           |
| `package.metadata.*`     | 常被第三方工具读取（docs.rs、发布自动化、安全审计等）                              |
| `[[bin]]`                | 分离 bin 与 lib 方便复用逻辑；多二进制可追加多个                                   |
| `build-dependencies`     | 仅 build.rs 可见；dev-dependencies 测试与示例可见，不进入最终发布 API              |
| `features`               | 最佳实践：默认最小集合（default 精简）+ 可选扩展（如 full / extras）               |
| 工具集成                 | 使用 cargo deny / cargo audit / cargo machete 等工具时，上述 metadata 可增强自动化 |

#### Cargo.lock

Cargo.lock 是由 Cargo 自动生成的文件，用于锁定项目依赖项的具体版本。它确保在不同环境中构建项目时使用相同的依赖版本，从而保证一致性和可重复性。

```
.
├── Cargo.lock
├── Cargo.toml
├── src/
│   ├── lib.rs
│   ├── main.rs
│   └── bin/
│       ├── named-executable.rs
│       ├── another-executable.rs
│       └── multi-file-executable/
│           ├── main.rs
│           └── some_module.rs
├── benches/
│   ├── large-input.rs
│   └── multi-file-bench/
│       ├── main.rs
│       └── bench_module.rs
├── examples/
│   ├── simple.rs
│   └── multi-file-example/
│       ├── main.rs
│       └── ex_module.rs
└── tests/
    ├── some-integration-tests.rs
    └── multi-file-test/
        ├── main.rs
        └── test_module.rs
```

#### 官方资源

- 官方仓库镜像: https://crates.io/
- 官方入门教程: https://course.rs/first-try/hello-world.html

### 变量的声明
```rust
let x = 5; // 声明一个不可变变量 x，类型为 i32
let mut y = 10; // 声明一个可变变量 y，类型为 i32
y = 15; // 修改变量 y 的值
```

### Rust 数据类型

#### 基本类型

Rust 有四种标量类型：整数类型、浮点数类型、布尔类型和字符类型。

##### 整数类型

整数类型有符号和无符号两种，分别表示正数和负数。常见的整数类型有 i8、i16、i32、i64、i128 和 isize（有符号），以及 u8、u16、u32、u64、u128 和 usize（无符号）。其中，isize 和 usize 的大小取决于目标平台的指针大小（32 位或 64 位）。Rust 默认的整数类型是 i32。

```rust
let x: i32 = 42; // 有符号整数
let y: u32 = 42; // 无符号整数
```

| 长度     | 有符号类型 | 无符号类型 | 取值范围                                             |
| -------- | ---------- | ---------- | ---------------------------------------------------- |
| 8 位     | i8         | u8         | -128 到 127 / 0 到 255                               |
| 16 位    | i16        | u16        | -32,768 到 32,767 / 0 到 65,535                      |
| 32 位    | i32        | u32        | -2,147,483,648 到 2,147,483,647 / 0 到 4,294,967,295 |
| 64 位    | i64        | u64        | -9.22×10¹⁸ 到 9.22×10¹⁸ / 0 到 1.84×10¹⁹             |
| 128 位   | i128       | u128       | -1.70×10³⁸ 到 1.70×10³⁸ / 0 到 3.40×10³⁸             |
| 架构相关 | isize      | usize      | 取决于目标平台的指针大小（32 位或 64 位）            |

##### 浮点类型

Rust 有两种浮点数类型：f32（32 位）和 f64（64 位）。默认的浮点数类型是 f64。

```rust
let x: f64 = 3.14; // 64 位浮点数
let y: f32 = 3.14; // 32 位浮点数
```

##### 布尔类型

布尔类型表示真（true）或假（false）。布尔类型在条件判断中非常常用。

```rust
let is_active: bool = true; // 布尔类型
let is_inactive: bool = false; // 布尔类型
```

##### 字符类型

字符类型表示单个 Unicode 字符。Rust 的字符类型是 char，占用 4 个字节。

```rust
let letter: char = 'A'; // 字符类型
let emoji: char = '😊'; // 字符类型
```

#### 单元类型

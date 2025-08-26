## Rust 基础

### Widnows 系统 Rust 环境安装

> 前置安装 https://visualstudio.microsoft.com/zh-hans/visual-cpp-build-tools/

`rustup` 是 Rust 的安装程序，也是它的版本管理程序。

下载 `rustup` 安装包 [Microsoft C++ Build Tools](https://www.rust-lang.org/tools/install)

下载完成后双击`rustup-init.exe`安装`rustup`

如果前面没安装[Microsoft C++ Build Tools](https://www.rust-lang.org/tools/install)， 那么安装`rustup`安装过程中可能胡提示缺少`Microsoft C++ Build Tools`缺失，直接回车应用推荐安装即可。

安装完成后会自带安装`visual studio`,这个会被公司监控警告

验证是否安装成功：

```bash
rustup -V # Rust 版本管理器
rustc -V  # Rust 编译器
cargo -V  # Rust 包管理器
```

正常来说如果按照完成，这三个都会有版本号输出。

### Rust 编辑器

推荐 `VSCode` 插件推荐 `rust-analyzer`

### Rust 包管理器 Cargo

常用命令示例：

```bash
cargo new hello_world            # 创建新项目（含新目录）
cargo new hello_lib --lib        # 创建库项目

cargo init                       # 在现有空目录中初始化项目
cargo init --lib                 # 初始化库项目

cargo build                      # 构建 debug 版本 (target/debug)
cargo build --release            # 构建 release 版本 (target/release)

cargo run                        # 构建并运行 (debug)
cargo run --release              # 构建并运行 (release)
cargo run --bin server           # 运行指定二进制 (workspace 或多 bin 场景)
cargo run --example demo         # 运行 examples/ 下示例

cargo check                      # 仅类型检查，速度快
cargo test                       # 运行测试
cargo test -- --nocapture        # 显示测试输出
cargo bench                      # 运行基准（需 nightly 或 criterion）

cargo fmt                        # 使用 rustfmt 格式化
cargo fmt -- --check             # 仅检查是否已格式化
cargo clippy                     # 运行 clippy 代码静态分析
cargo clippy -- -D warnings      # 将所有警告视为错误

cargo doc                        # 生成文档 (target/doc)
cargo doc --open                 # 生成后自动在浏览器打开

cargo add serde                  # 添加依赖（需安装 cargo-edit）
cargo add serde --features derive
cargo rm serde                   # 移除依赖
cargo upgrade                    # 升级依赖版本
cargo update                     # 根据 Cargo.lock 规则更新依赖
cargo update -p serde            # 更新指定包

cargo tree                       # 查看依赖树（需 cargo-tree）
cargo tree -i serde              # 反向依赖查询
cargo metadata                   # 输出元数据（JSON）

cargo clean                      # 清理构建产物
cargo fix                        # 自动应用编译器建议

cargo install cargo-watch        # 安装二进制工具
cargo uninstall cargo-watch      # 卸载二进制工具
cargo install --path .           # 安装当前项目为二进制

cargo publish                    # 发布 crate（需配置 crates.io 账号）
cargo package                    # 打包检查（不发布）
cargo login <token>              # 登录 crates.io

# 环境与配置
cargo config get                 # 查看当前配置
cargo config edit                # 编辑 .cargo/config.toml

# 运行时特性控制
cargo build --features foo,bar   # 启用特性
cargo build --no-default-features
cargo build --all-features

# 工作空间
cargo workspace list             # 列出成员（1.70+）
cargo build -p crate_name        # 构建指定成员
```

说明：

- 需额外安装的子命令：cargo-add / rm / upgrade 属于 cargo-edit；cargo tree 属于 cargo-tree。
- 常用三阶段：check（快） -> clippy（质量） -> test（正确性） -> build/run。
- 发布前建议：cargo fmt && cargo clippy -D warnings && cargo test && cargo package。
- 生产构建使用 --release 以获得优化后的二进制。
- features 用于按需裁剪功能，减少体积与编译时间。
- 工作空间 (workspace) 适合管理多 crate 单体仓库。
- 缩短编译迭代可用 cargo check 与增量编译（默认开启）。
- 监听自动重建可用 cargo watch -x run （需安装 cargo-watch）。
- 文档注释使用 ///，示例用代码块；文档测试将随 cargo test 执行。
- 依赖冲突或体积分析可结合 cargo tree 与 cargo metadata。
- 可用 CARGO_TARGET_DIR 指定构建目录，提高多项目缓存复用。
- RUSTFLAGS / .cargo/config.toml 可统一指定编译优化或 lint 行为。
- crates.io 发布前务必确认版本号递增且不包含敏感文件（通过 .gitignore 与 include/exclude 管理）。
- 通过 cfg(feature = "...") 进行条件编译实现可选功能。
- 结合 clippy + rustfmt + cargo deny（需安装）实现质量与合规检查。
- build.rs 可在编译阶段生成代码或探测系统环境。
- 使用 criterion 进行更稳定的基准测试（替代已弃用的内置 bench）。
- 可结合 cargo flamegraph（需 Linux 工具）进行性能火焰图分析。
- 适度拆分模块与启用 incremental + sccache 提升大型项目构建速度。
- 批量执行命令脚本化可使用 Justfile 或 Makefile。
- crates.io 令牌保存在本地 credentials.toml，勿提交仓库。
- 版本策略建议遵循语义化版本：MAJOR.MINOR.PATCH。
- 通过 cargo vendor 可离线缓存依赖（企业内网环境常用）。
- 采用 workspace + [patch.crates-io] 可替换上游依赖做临时修复。
- 利用 cargo audit（需安装）检测安全漏洞。
- 通过 cargo expand（需安装）查看宏展开有助调试复杂宏。
- 组合 RUST_LOG 与 tracing / log 库实现灵活日志调试。
- 交叉编译可配置 target 与安装对应目标工具链：rustup target add x86_64-unknown-linux-musl。
- 构建最小镜像可结合 musl + distroless / scratch。
- 使用 cargo llvm-lines（需安装）分析函数编译后代码行数辅助性能优化。
- 通过 minimal-versions 模式测试最小依赖兼容性：cargo update -Z minimal-versions。
- workspace 中共享依赖放在根 Cargo.toml 的 [workspace.dependencies] 简化维护（1.64+）。
- Run 时常见错误可用 RUST_BACKTRACE=1 cargo run 获取调用栈。
- 长时间构建建议启用 sccache 缓存：export RUSTC_WRAPPER=sccache。
- CI 中拆分步骤：fmt -> clippy -> test -> doc (warnings as errors) -> build。
- 可结合 cargo miri（需安装）进行未定义行为检测（UB 检查）。
- 发布库时使用 #[deny(rust_2018_idioms)] 等增强健壮性。
- 自动语义化版本与 Changelog 可集成 release-plz 或 cargo-release。
- 可通过 cargo features 拆分：默认核心功能 + 可选扩展/后端/驱动。
- 提供 examples/ 展示使用场景，有助用户理解 API。
- 保持依赖精简，避免编译时间膨胀与供应链风险。

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

# 提示：
# 1. src/main.rs 存在即为二进制程序；如需库，加一个 src/lib.rs。
# 2. 增加依赖：修改这里后执行 cargo build（或用 cargo add <crate>）。
# 3. 只想试编译更快：cargo check
# 4. 运行：cargo run
# 5. 测试：cargo test
```

要点：

- [package] 定义基础元数据，rust-version 指定最低兼容编译器版本便于 CI 检查。
- [features] 用 optional 依赖 + 组合特性实现可裁剪功能与按需加载。
- profile.\* 调优构建：发布二进制可启用 LTO / 减少 codegen-units / panic = abort 以减小体积。
- workspace.dependencies 统一版本，减少重复（1.64+）。
- patch.crates-io 用于临时覆盖上游依赖（热修复、本地调试）。
- package.metadata.\* 常被第三方工具读取（docs.rs、发布自动化、安全审计等）。
- 分离 bin 与 lib 方便复用逻辑；多二进制可追加多个 [[bin]]。
- build-dependencies 仅 build.rs 可见；dev-dependencies 测试与示例可见，不进入最终发布 API。
- features 最佳实践：默认最小集合（default 精简）+ 可选扩展（如 full / extras）。
- 使用 cargo deny / cargo audit / cargo machete 等工具时，上述 metadata 可增强自动化。

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

https://course.rs/first-try/hello-world.html
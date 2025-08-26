## Rust 基础

### Widnows 系统 Rust 环境安装

`rustup` 是 Rust 的安装程序，也是它的版本管理程序。

下载`rustup`安装包 https://www.rust-lang.org/tools/install

下载完成后双击`rustup-init.exe`安装`rustup`

安装过程中可能胡提示缺少`Microsoft C++ Build Tools`缺失，直接回车应用推荐安装即可。

安装完成后会自带安装`visual studio`,这个会被公司监控警告，卸载了即可，反正用不上

验证是否安装成功：
```bash
rustup -V # Rust 版本管理器
rustc -V  # Rust 编译器
cargo -V  # Rust 包管理器
```
正常来说如果按照完成，这三个都会有版本号输出。

### Rust编辑器
推荐 `VSCode` 插件推荐 `rust-analyzer`

### Rust包管理器 Cargo



---
title: pyenv 教程
---

# pyenv 介绍
pyenv 是一个用于管理多版本 Python 的工具，支持在同一台机器上安装、切换多个 Python 版本，适用于 macOS、Linux 以及 Windows（需配合 pyenv-win）。

# 安装方法

## macOS
推荐使用 Homebrew 安装：

```bash
brew update
brew install pyenv
```

安装完成后，将以下内容加入 `~/.bashrc` 或 `~/.zshrc`：

```bash
export PYENV_ROOT="$HOME/.pyenv"
export PATH="$PYENV_ROOT/bin:$PATH"
eval "$(pyenv init --path)"
eval "$(pyenv init -)"
```

## Linux
可以使用 curl 或 git 安装：

```bash
curl https://pyenv.run | bash
```

或者

```bash
git clone https://github.com/pyenv/pyenv.git ~/.pyenv
```

然后将以下内容加入 `~/.bashrc` 或 `~/.zshrc`：

```bash
export PYENV_ROOT="$HOME/.pyenv"
export PATH="$PYENV_ROOT/bin:$PATH"
eval "$(pyenv init --path)"
eval "$(pyenv init -)"
```

## Windows
Windows 推荐使用 Chocolatey 安装 pyenv-win，简单快捷：

```bash
choco install pyenv-win
```

安装完成后，重启命令提示符或 PowerShell 即可使用。

如需手动安装，也可参考 [pyenv-win](https://github.com/pyenv-win/pyenv-win)：
1. 下载并解压 pyenv-win 到 `%USERPROFILE%/.pyenv`。
2. 将 `%USERPROFILE%/.pyenv/pyenv-win/bin` 和 `%USERPROFILE%/.pyenv/pyenv-win/shims` 加入系统 PATH。
3. 重新启动命令提示符或 PowerShell。

# 常用命令

```bash
# 查看可安装的 Python 版本
pyenv install --list

# 安装指定版本
pyenv install 3.11.4

# 列出已安装的版本
pyenv versions

# 设置全局 Python 版本
pyenv global 3.11.4

# 设置目录（项目）下的 Python 版本
pyenv local 3.8.10

# 当前使用的 Python 版本
pyenv version

# 卸载某个版本
pyenv uninstall 3.8.10
```

# 常见问题

- 安装失败时，请确认已安装必要的依赖（如 build-essential、openssl、zlib 等）。
- macOS 用 Homebrew 安装时，建议先安装 Xcode Command Line Tools：`xcode-select --install`
- Windows 请务必使用 pyenv-win，不要直接用 Linux 版 pyenv。

# 参考链接
- [pyenv 官方文档](https://github.com/pyenv/pyenv)
- [pyenv-win 官方文档](https://github.com/pyenv-win/pyenv-win)

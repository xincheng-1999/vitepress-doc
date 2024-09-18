# Chocolatey Windows 安装器

[Chocolatey Software Docs | Chocolatey - Software Management for Windows](https://docs.chocolatey.org/en-us/)

常用命令：

```sh
choco upgrade chocolatey # 更新自身

choco search  # xxx，查找 xxx 安装包
choco info    # xxx，查看 xxx 安装包信息
choco install # xxx，安装 xxx 软件
choco upgrade # xxx，升级 xxx 软件
choco uninstall # xxx， 卸载 xxx 软件

```

```sh
# 备份软件列表  获得到一个 packages.config 文件，是xml类型
choco export --output-file-path="'d:\packages.config'" --include-version-numbers

# 安装软件列表
choco install packages.config
```


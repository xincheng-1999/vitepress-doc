# Homebrew的安装使用

Homebrew是一款Mac OS平台下的软件包管理工具，拥有安装、卸载、更新、查看、搜索等很多实用的功能。简单的一条指令，就可以实现包管理，而不用你关心各种依赖和文件路径的情况，十分方便快捷。

## 安装

```
命令1:/usr/bin/ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"（这个命令不好使会报错，如果报错尝试使用命令2）
命令2:/bin/zsh -c "$(curl -fsSL https://gitee.com/cunkai/HomebrewCN/raw/master/Homebrew.sh)"（这是一个脚本，同样可以安装Homebrew选中科大）
```

## 使用

```
1.查看Homebrew命令：brew help  
2.安装任意包：brew install <packageName>，eg：brew install node  
3.卸载任意包：brew uninstall <packageName>，eg：brew uninstall git  
4.查询可用包：brew search <packageName>  
5.查询已安装包列表：brew list  
6.查看任意包信息：brew info <packageName>  
7.更新Homebrew：brew update  
8.Homebrew帮助信息：brew -h  
8.查看brew版本：brew -v  
10.更新brew版本：brew update  
11.整理重复语句：open ~/.zshrc -e、open ~/.bash_profile -e
```



[Homebrew介绍和使用](https://www.jianshu.com/p/de6f1d2d37bf)  
[Mac中Homebrew的安装和使用](https://links.jianshu.com/go?to=https%3A%2F%2Fblog.csdn.net%2Fa823007573%2Farticle%2Fdetails%2F106098098)  
[Homebrew国内如何自动安装](https://links.jianshu.com/go?to=%255Bhttps%3A%2F%2Fzhuanlan.zhihu.com%2Fp%2F111014448%255D%28https%3A%2F%2Fzhuanlan.zhihu.com%2Fp%2F111014448%29)

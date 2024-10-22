# Git

Husky 的使用：[前端项目中使用 husky 做预检查 - 简书](https://www.jianshu.com/p/e1529a313e8b?utm_campaign=studygolang.com&utm_medium=studygolang.com&utm_source=studygolang.com)

# 常用命令

## 1. 初始化全局配置

- git config --global user.name "name"
- git config --global user.email "email"
- git config --list 查看.gitconfig 配置

```sh
git config --global alias.cm "commit -m" # 给命令配别名 如此commit即可git cm 'message'
```

## 2. 分支操作

```shell
git branch # 查看当前所在分支

git branch -r # 列出所有远程分支

git branch -a # 查看所有分支

git checkout <branch> # 创建分支

git checkout -b <branch> # 创建分支并跳转到新分支

git push -u origin <branch>  # 新建的分支推送到远程仓库

git branch -d <branch> # 删除本地分支

git branch -D <branch> # 强制删除本地分支

git push origin --delete <name> # 删除远程仓库分支

git merge <branch> # 分支合并

# 多个文件一键解决冲突
git checkout --ours ./ # 解决当前目录下所有的冲突，应用当前修改
git checkout --theirs ./ # 解决当前目录下所有的冲突，应用传入修改

git branch -m <oldname> <newname> # 重命名分支

git branch -M <oldname> <newname> # 强制重命名
```

## 3. 基础操作

```sh
git add <filename> # 暂存

git commit -m "meaasge" # 提交

git commit --amend -m <message> # 更改上次提交的描述信息

git add ./ + git commit --amend --no-edit # 将新暂存的文件合并到上次提交中

git stash
git stash push
git stash push -m "<stash message>" # 存储库命令

git stash list # 查看存储列表

git stash apply # 应用最新存储
git stash pop # 应用最新存储并删除最新存储记录

git stash apply stash@{N}
git stash apply <n>       # 皆可用来应用第n个存储

git cherry-pick <commit hash> # 合并另一个分支上的指定commit，建议添加 -x 能生成标准化消息

git log # 查看commit记录

git show <commithash> # 查看某次更改对比
```

## 4. 远程操作

```sh
git remote # 查看远程仓库名，一般为origin

git remote -v # 查看远程仓库地址

git remote add <remote_name> <remote_url> # 添加远程仓库

git remote rm origin # 移除远程仓库

git remote update origin --prune # 同步远程分支信息

git push origin --tags # 把tag同步到远程

git push origin tag_name # 把指定tag名同步到远程
```

## 5.配置 ssh 公钥

用以下命令生成 ssh 公钥和私钥，把公钥丢到远程账号下的 ssh 就好

```sh
ssh-keygen -t rsa -C "你的邮箱" # rsa算法目前不被最新的git接受
ssh-keygen -t ed25519 -C "xxx@xx.com" # 建议使用ed25519算法
```

## 6. 扩展功能
### 1. worktree功能
作用： 开发过程中需要切到其他分支不想影响当前分支，增加效率，就可以新增一个worktree

```bash
# 创建一个新的工作区，用于 hotfix 分支
git worktree add ../hotfix-path hotfix

# 现在你可以在 hotfix-path 目录中修复bug
cd ../hotfix-path

# 修复bug后，切换回 dev 分支的工作区
cd - # 返回到原始工作区
git worktree remove ../hotfix-path # 完成后删除 hotfix 工作区

git worktree list # 查看所有工作区
```
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

git tag <tag_name> # 创建标签

git tag -a <tag_name> -m "tag message" # 添加带注释的标签

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

# Git Submodule 精简操作文档

## 一、创建子仓库并推送（主仓库引入子模块）

### 核心目标

在主仓库中新增子模块，并将配置推送到远程。

### 操作步骤

```bash
# 1. 主仓库根目录执行，添加子模块（<子仓库地址> 替换为实际Git地址，<本地路径> 如 submodule/iflyplot-fabric）
git submodule add <子仓库地址> <本地路径>

# 2. 提交子模块配置（主仓库会生成 .gitmodules 文件和子模块路径记录）
git add .gitmodules <本地路径>
git commit -m "feat: 添加子模块 xxx"

# 3. 推送到主仓库远程
git push
```

## 二、拉取已有子仓库（克隆主仓库后同步子模块）

### 核心目标

克隆包含子模块的主仓库后，拉取子模块代码。

### 操作步骤

```bash
# 方式1：克隆时直接拉取（推荐）
git clone --recurse-submodules <主仓库地址>

# 方式2：已克隆主仓库，补全子模块
cd <主仓库目录>
git submodule init  # 初始化子模块配置
git submodule update  # 拉取子模块到主仓库记录的版本
```

## 三、子仓库更新 → 同步到主仓库

### 核心目标

子模块代码修改后，更新主仓库对其提交哈希的记录。

### 操作步骤

```bash
# 1. 进入子模块目录，提交并推送子模块代码
cd <子模块本地路径>
git add .
git commit -m "fix: 子模块修改内容"
git push origin <子模块分支>  # 如 main/dev

# 2. 回到主仓库，更新并提交子模块哈希记录
cd ../..
git add <子模块本地路径>
git commit -m "chore: 同步子模块 xxx 最新提交"
git push
```

## 四、主仓库更新 → 同步到子仓库

### 核心目标

主仓库切换分支/拉取最新代码后，同步子模块到主仓库记录的版本。

### 操作步骤

```bash
# 1. 主仓库拉取最新代码（确保子模块哈希记录最新）
cd <主仓库目录>
git pull origin <主仓库分支>

# 2. 同步子模块到主仓库记录的提交版本
git submodule update --checkout  # --checkout 强制检出对应版本，覆盖子模块本地临时修改

# 3. （可选）子模块切换到对应开发分支（如需继续开发）
cd <子模块本地路径>
git checkout <子模块分支>  # 如和主仓库分支同名的 dev/main
git pull origin <子模块分支>
cd ../..
```

## 五、分支切换：主/子仓库双向同步

### 场景1：主仓库切分支 → 子仓库切对应分支

```bash
# 1. 主仓库切换目标分支
git checkout <目标分支>  # 如 dev/test
git pull origin <目标分支>

# 2. 子模块同步到主仓库该分支记录的版本
git submodule update --checkout

# 3. 子模块切换到同名分支（开发用）
cd <子模块本地路径>
git checkout <目标分支>
git pull origin <目标分支>
cd ../..
```

### 场景2：子仓库切分支 → 主仓库切对应分支

```bash
# 1. 子模块切换并拉取目标分支最新代码
cd <子模块本地路径>
git checkout <目标分支>
git pull origin <目标分支>
cd ../..

# 2. 主仓库切换到同名分支
git checkout <目标分支>
git pull origin <目标分支>

# 3. 同步主仓库对子模块的哈希记录（若不一致）
git add <子模块本地路径>
git commit -m "chore: 同步子模块 xxx 分支最新提交"
git push origin <目标分支>
```

## 关键总结

- 子模块核心：主仓库仅记录子模块提交哈希，所有修改需双仓库同步（子模块提交+主仓库更新记录）。
  
- 拉取子模块：优先用`git clone --recurse-submodules`，补全用 `init + update`。
  
- 更新子模块：主仓库同步用 `git submodule update --checkout`。
  
- 分支切换：核心是「分支名一致」，切换后需同步哈希记录，高频操作建议封装脚本。
  
  > （注：文档部分内容可能由 AI 生成）
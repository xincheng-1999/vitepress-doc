# Linux 服务器必会

> 你过去写前端，部署可能就是 `git push` 一下、等 CI 变绿，或者把 `dist/` 拖进对象存储。线上出问题，你看的是浏览器 DevTools。
> 但后端不一样：你的 `svc-ai`、`svc-canvas` 是一个个跑在**Linux 服务器**上的进程。它崩了、卡了、占满磁盘了，没有图形界面给你点，只有一个黑乎乎的终端等你敲命令。
> 这一章的目标只有一个——**让你敢于 SSH 上一台陌生的 Linux 服务器，并且摸清它的状况**。不求精通,但求遇到事故时不慌。

本章是 Part 5「服务器与运维」的第一站。它不教你写代码，而是教你「代码跑起来之后，所在的那台机器长什么样、怎么操作」。后面的 [第二十三章 Docker 实战](/back-end/frontend-backend-guide/23-docker-in-practice)、[第二十六章 看懂日志](/back-end/frontend-backend-guide/26-reading-logs)、[第二十九章 诊断工具箱](/back-end/frontend-backend-guide/29-diagnostic-toolbox) 全都建立在你会用 Linux 命令行的基础上。

> 💡 **前端类比**：Linux 终端就是「服务器版的 DevTools Console」。你在浏览器里敲 `document.querySelector(...)` 实时查 DOM，在服务器里敲 `ps -ef | grep java` 实时查进程。区别只是：浏览器有图形辅助，服务器全靠命令。命令记不住没关系，本章最后有一份「上机第一分钟清单」，照着敲就行。

---

## 21.1 登录服务器：SSH 与传文件

后端的一切都从「连上服务器」开始。Windows 上推荐用自带的 `ssh`（Windows 10+ 已内置 OpenSSH，PowerShell 里直接敲就行）。

### 用密码登录

```bash
# 语法：ssh 用户名@服务器IP或域名
ssh deploy@8.135.12.34
```

**预期输出（第一次连接会问你要不要信任这台机器的指纹）：**

```text
The authenticity of host '8.135.12.34' can't be established.
ED25519 key fingerprint is SHA256:Xa9k...Lp0.
Are you sure you want to continue connecting (yes/no/[fingerprint])? yes
Warning: Permanently added '8.135.12.34' (ED25519) to the list of known hosts.
deploy@8.135.12.34's password:
```

**怎么读**：第一次连接，SSH 会把对方的公钥指纹记到本地 `~/.ssh/known_hosts`，下次就不再问。输入 `yes` 回车后输密码（输密码时屏幕**不会显示任何字符**，这是正常的，不是卡了）。登录成功后提示符会变成类似 `deploy@svc-prod-01:~$`。

### 指定端口

很多生产服务器为了安全不用默认的 22 端口。用 `-p` 指定：

```bash
ssh -p 2222 deploy@8.135.12.34
```

> ⚠️ 注意 `scp` 指定端口用的是大写 `-P`，`ssh` 用小写 `-p`，这是新手最常踩的坑。

### 密钥登录（推荐，免密码）

每次输密码又慢又不安全。后端标配是**密钥登录**：你本地生成一对密钥，把公钥放到服务器上，之后免密进入。

```bash
# 1. 本地生成密钥对（一路回车即可；-C 是注释，方便区分）
ssh-keygen -t ed25519 -C "gaoxincheng@laptop"
```

**预期输出：**

```text
Generating public/private ed25519 key pair.
Enter file in which to save the key (/home/you/.ssh/id_ed25519):
Your identification has been saved in /home/you/.ssh/id_ed25519
Your public key has been saved in /home/you/.ssh/id_ed25519.pub
```

**怎么读**：生成了两个文件——`id_ed25519`（**私钥，绝不外传**）和 `id_ed25519.pub`（公钥，可以给别人）。

```bash
# 2. 把公钥拷到服务器（一条命令搞定，会要你输一次密码）
ssh-copy-id -i ~/.ssh/id_ed25519.pub deploy@8.135.12.34
```

它本质上是把你 `.pub` 的内容追加到服务器的 `~/.ssh/authorized_keys` 文件里。之后再 `ssh deploy@8.135.12.34` 就直接进去了，不再问密码。

> 💡 **前端类比**：私钥/公钥就像 OAuth 里的 `client_secret`（你私藏的）和 `client_id`（可以公开的）。服务器认得你公钥，就像 GitHub 认得你存在它那儿的 SSH key——所以你 `git push` 不用每次输密码。原理一模一样。

### 传文件：scp 和 rsync

部署时你常要把本地的 jar 包、配置文件传上去。

```bash
# scp：简单粗暴的拷贝（大写 -P 指定端口）
# 本地 → 服务器
scp -P 2222 ./svc-ai.jar deploy@8.135.12.34:/opt/app/

# 服务器 → 本地（把线上日志拉下来分析）
scp deploy@8.135.12.34:/opt/app/logs/svc-ai.log ./
```

`rsync` 比 `scp` 聪明，**只传有变化的部分**，传整个目录时快得多，断了还能续：

```bash
# -a 保留权限/时间等属性，-z 压缩传输，-P 显示进度并支持断点续传
rsync -azP ./dist/ deploy@8.135.12.34:/opt/app/static/
```

**预期输出（rsync 会列出实际传了哪些文件）：**

```text
sending incremental file list
index.html
assets/app-3f9a.js
        1,204,338 100%   12.4MB/s    0:00:00 (xfr#2, to-chk=0/3)
```

> 💡 **前端类比**：`rsync` 的「只传变化的部分」就是增量构建的思路——Vite 的 HMR、CDN 的差量更新，都是「别全量重来，只动改了的」。

---

## 21.2 文件与目录：在没有资源管理器的世界里导航

登录后第一件事是「我在哪、这儿有啥」。

```bash
pwd                 # print working directory：我当前在哪个目录
ls -lah             # 列出当前目录所有文件（含隐藏文件），带详细信息和人类可读的大小
cd /opt/app         # 进入目录；cd .. 上一级；cd ~ 回家目录；cd - 回到上一个目录
```

**`ls -lah` 的预期输出：**

```text
total 152M
drwxr-xr-x  4 deploy deploy 4.0K Jun  1 10:22 .
drwxr-xr-x 12 root   root   4.0K May 28 09:00 ..
-rw-r--r--  1 deploy deploy 151M Jun  1 10:20 svc-ai.jar
drwxr-xr-x  2 deploy deploy 4.0K Jun  1 10:22 logs
-rw-r--r--  1 deploy deploy 1.2K May 30 14:00 application.yml
```

**怎么读**：每一列从左到右——权限（下一节细讲）、硬链接数、属主、属组、大小、修改时间、文件名。`.` 是当前目录，`..` 是上一级。开头是 `d` 的是目录，`-` 是普通文件。

### 看文件内容（重点：看日志）

```bash
cat application.yml        # 一次性把整个文件吐到屏幕，适合小文件
less svc-ai.log            # 分页查看大文件（按 q 退出，/ 搜索，G 跳到末尾）
head -n 50 svc-ai.log      # 看头 50 行
tail -n 100 svc-ai.log     # 看末尾 100 行（最新的日志在文件末尾）
tail -f svc-ai.log         # ★ 实时跟踪：日志一有新内容就刷出来
```

`tail -f` 是后端**最高频**的命令之一。重启服务、复现 bug 时，你会一直开着它盯着日志滚动。

> 💡 **前端类比**：`tail -f svc-ai.log` 就是「服务器端的 console，开着实时打印」。你在浏览器看 Network 面板里请求实时进来，在服务器就是看日志实时刷出来。看日志的进阶（怎么从一坨日志里捞出有用信息）是 [第二十六章](/back-end/frontend-backend-guide/26-reading-logs) 的主题。

### 查找文件、搜索内容

```bash
# find：按名字/类型找文件
find /opt/app -name "*.log"           # 找所有 .log 文件
find /opt/app -name "*.jar" -mtime -1 # 找一天内修改过的 jar

# grep：在文件内容里搜文字（后端排查的命脉）
grep "ERROR" svc-ai.log               # 找出含 ERROR 的行
grep -rn "RtData.fail" /opt/app/src   # -r 递归搜目录，-n 显示行号
tail -f svc-ai.log | grep "OutOfMemory"  # 实时跟踪 + 只看含特定关键字的行
```

**`grep -rn` 的预期输出：**

```text
/opt/app/src/UserController.java:42:        return RtData.fail("配额不足");
/opt/app/src/AuthService.java:88:        return RtData.fail("token 已过期");
```

**怎么读**：每行格式是 `文件路径:行号:匹配到的内容`。这就是你在服务器上「全局搜索」的方式，等价于 VS Code 里的 `Ctrl+Shift+F`。

> 💡 **前端类比**：`grep` 就是命令行版的「编辑器全局搜索」。`grep -rn "RtData.fail"` ≈ 在 VS Code 里搜 `RtData.fail` 并显示文件和行号。

### vim 最小操作（只要会进出和保存）

服务器上改配置躲不开 vim。你**不需要精通**，记住这几步保命操作即可：

```text
vim application.yml      # 打开文件

打开后默认在「普通模式」，不能直接打字：
  i          → 进入「插入模式」，此时才能像普通编辑器一样打字
  Esc        → 退回「普通模式」
  :wq        → 保存并退出（write & quit）
  :q!        → 不保存强制退出（改错了想反悔）
  /关键字     → 搜索，按 n 跳下一个
```

> 💡 **前端类比**：vim 的「模式切换」很反直觉——记住一句话：**一进去先按 `Esc` 让自己处于普通模式，要打字按 `i`，改完按 `Esc` 再 `:wq`**。改坏了不要慌，`:q!` 一定能不保存退出，相当于关掉文件没点保存。

### 软链接 ln -s

部署时常用软链接做「版本切换」：让 `current` 指向某个具体版本目录，回滚时改指向即可。

```bash
ln -s /opt/app/releases/v2.3.1 /opt/app/current   # current 是指向 v2.3.1 的快捷方式
ls -l /opt/app/current
# lrwxrwxrwx 1 deploy deploy 26 Jun 1 10:30 /opt/app/current -> /opt/app/releases/v2.3.1
```

> 💡 **前端类比**：软链接就是文件系统里的「快捷方式」，也很像 `pnpm` 的 `node_modules` 里那些指向全局 store 的符号链接——本体只存一份，链接到处用。

---

## 21.3 权限：rwx、755 与 644

Linux 每个文件都有「谁能读、谁能写、谁能执行」的权限。前端机器上你是管理员，想干啥干啥；服务器上权限错了，服务会**直接起不来**。

回看 `ls -l` 第一列那串 `-rwxr-xr-x`，它分四段读：

```text
   -        rwx        r-x        r-x
   │         │          │          │
 类型     属主权限    属组权限   其他人权限
(- 文件   (owner)    (group)    (others)
 d 目录
 l 链接)

r = 读 (read,  值 4)
w = 写 (write, 值 2)
x = 执行(execute,值 1)   ← 对目录而言，x 表示「能进入这个目录」
```

把每段的 `rwx` 当成二进制相加，就得到那串数字：

```text
rwx = 4+2+1 = 7      r-x = 4+0+1 = 5      r-- = 4+0+0 = 4      rw- = 4+2+0 = 6

所以：
755 = rwxr-xr-x   属主全权，其他人可读可执行   ← 目录、可执行脚本常用
644 = rw-r--r--   属主可读写，其他人只读        ← 普通配置文件、文本常用
600 = rw-------   只有属主能读写                ← 私钥、密码文件必须这样
```

### chmod 改权限、chown 改属主

```bash
chmod 755 deploy.sh          # 让脚本可执行
chmod 600 ~/.ssh/id_ed25519  # 私钥必须 600，否则 ssh 会拒绝使用它
chmod -R 644 logs/           # -R 递归，把 logs 目录下所有文件设成 644

chown deploy:deploy svc-ai.jar   # 把属主和属组都改成 deploy
chown -R deploy:deploy /opt/app  # 递归改整个目录的属主
```

**一个真实事故**：你用 `scp` 传上去的私钥，权限默认可能是 `644`（别人可读），SSH 会拒绝用它并报：

```text
Permissions 0644 for '/home/deploy/.ssh/id_ed25519' are too open.
This private key will be ignored.
```

**怎么读 → 结论**：报错说私钥权限太开放被忽略了。`chmod 600 ~/.ssh/id_ed25519` 改成只有自己能读写，问题立解。

### sudo：临时借用管理员权限

普通用户（如 `deploy`）干不了的事（改系统配置、装软件、操作别人的文件），前面加 `sudo` 以管理员（root）身份执行：

```bash
sudo systemctl restart nginx        # 重启系统服务，需要 root
sudo chown deploy:deploy /var/www   # 把本属于 root 的目录改给 deploy
```

> 💡 **前端类比**：`sudo` 就像 `npm install -g` 需要的管理员权限——日常开发用普通权限，只有装全局工具/改系统级东西才升权。生产上**绝不要图省事什么都加 sudo**，这和「别拿 root 跑业务进程」是一个道理：权限越小，误操作的破坏面越小。

---

## 21.4 进程：服务到底跑没跑、占了多少资源

「服务挂了吗？」「为什么 CPU 飙到 100%？」——这些都靠进程命令回答。

### 看进程：ps 与 grep

```bash
ps -ef | grep java           # 列出所有进程，筛出含 java 的（最常用）
```

**预期输出：**

```text
deploy   12934     1 12 10:20 ?  00:03:21 java -Xmx512m -jar svc-ai.jar
deploy   13201 13180  0 11:05 pts/0 00:00:00 grep --color=auto java
```

**怎么读**：第二列 `12934` 是**进程 ID（PID）**，后面是启动命令。第一行是我们的 `svc-ai` 服务（PID 12934），第二行是 `grep` 命令本身（它也含 java 字样，可忽略）。**这个 PID 是后面 kill 进程、查内存的关键。**

```bash
jps                          # ★ Java 专用：只列 Java 进程，更清爽
```

**预期输出：**

```text
12934 svc-ai.jar
14002 svc-canvas.jar
14555 Jps
```

`jps`（JVM Process Status）是 JDK 自带的，比 `ps | grep java` 干净，一眼看出哪些 Java 服务在跑。

### 看资源占用：top / htop

```bash
top                          # 实时刷新的进程资源排行（按 q 退出）
```

**预期输出（节选）：**

```text
top - 11:10:03 up 30 days,  load average: 0.85, 0.91, 0.88
%Cpu(s): 23.1 us,  4.2 sy,  0.0 ni, 72.0 id
MiB Mem :  3934.0 total,   210.5 free,  2890.1 used,   833.4 buff/cache

  PID USER     %CPU  %MEM    TIME+ COMMAND
12934 deploy   45.3  18.7  3:21.04 java
14002 deploy   12.1  22.4  1:02.55 java
```

**怎么读**：
- `load average: 0.85, 0.91, 0.88`——过去 1/5/15 分钟的系统负载。经验法则：这个值如果**长期超过 CPU 核数**（比如 4 核机器 load 长期 >4），说明系统在排队、扛不住了。
- `%Cpu(s)` 里 `us` 是用户进程占用，`id` 是空闲。`id` 低（如 <10）说明 CPU 快满了。
- 进程列表按 `%CPU` 排序，PID 12934 的 java 占了 45.3% CPU、18.7% 内存——锁定它就是排查对象。

`htop` 是 `top` 的彩色增强版（可能需 `sudo apt install htop` 安装），能用方向键选进程、F9 直接杀，更友好。

> 💡 **前端类比**：`top` 就是「服务器版的浏览器任务管理器（Shift+Esc）」——哪个 Tab 吃 CPU/内存一目了然。区别是服务器上一个 java 进程往往就是你的一个微服务。

### 杀进程：kill -15 vs kill -9

```bash
kill 12934        # 默认发 SIGTERM(15)：礼貌地请进程"善后后退出"
kill -15 12934    # 同上，显式写出信号 15
kill -9 12934     # 发 SIGKILL(9)：强制立刻杀死，不给善后机会
```

**区别很重要**：
- `kill -15`（SIGTERM）：Java 进程能捕获它，触发 Spring 的优雅停机——把正在处理的生图请求做完、关掉数据库连接、注销服务注册，再退出。**生产首选**。
- `kill -9`（SIGKILL）：操作系统直接砍，进程没机会善后。可能导致正在写的文件损坏、连接没关闭、RocketMQ 消息处理到一半丢失。**只在 `-15` 杀不掉时才用**。

> 💡 **前端类比**：`kill -15` 像 `beforeunload` 事件——给页面机会做清理（保存草稿、提示用户）；`kill -9` 像直接拔电源——啥都来不及做。能温柔就别粗暴。

### 后台运行：nohup 与 &

直接 `java -jar svc-ai.jar` 会占着你的终端，一旦 SSH 断开进程就被杀。生产上用 `nohup ... &` 让它在后台长跑（不过更规范的方式是用 systemd，见 21.8）：

```bash
nohup java -jar svc-ai.jar > svc-ai.log 2>&1 &
```

**拆解这行**：
- `nohup`：忽略「挂断信号」，SSH 断开也不杀它。
- `> svc-ai.log`：把标准输出写进日志文件。
- `2>&1`：把标准错误（2）也合并到标准输出（1）里，错误日志一起进文件。
- 结尾 `&`：放到后台运行，立刻把终端还给你。

### 查端口占用：lsof 与 ss

「8080 端口被占了，服务起不来」是高频问题：

```bash
lsof -i:8080          # 哪个进程占用了 8080 端口
```

**预期输出：**

```text
COMMAND   PID   USER   FD   TYPE  DEVICE  NODE NAME
java    12934 deploy  45u  IPv6  98321   TCP  *:http-alt (LISTEN)
```

**怎么读 → 结论**：PID 12934 的 java 进程正监听 8080（`http-alt` 是 8080 的别名）。如果你想换它，就 `kill 12934`；如果这是个残留的旧进程没杀干净，也是这么找出来再清掉。

---

## 21.5 网络：连不连得通、端口开没开

微服务之间靠网络互相调用。`svc-gateway` 调不通 `svc-auth`、连不上 MongoDB——这些都靠网络命令定位。

```bash
ss -lntp              # ★ 看本机所有监听中的端口（哪些服务开着）
```

**预期输出：**

```text
State   Local Address:Port   Process
LISTEN  0.0.0.0:8080         users:(("java",pid=12934))
LISTEN  127.0.0.1:6379       users:(("redis-server",pid=880))
LISTEN  0.0.0.0:22           users:(("sshd",pid=701))
```

**怎么读**：`-l` listening、`-n` 显示数字端口不解析名字、`-t` 只看 TCP、`-p` 显示是哪个进程。重点看 `Local Address`——`0.0.0.0:8080` 表示对**所有网卡**开放（外部能访问），`127.0.0.1:6379` 表示 Redis 只对**本机**开放（外部连不上，这通常是故意的安全设置）。

> 旧服务器上可能没有 `ss`，等价命令是 `netstat -lntp`。

### curl：命令行版的 axios

```bash
curl http://localhost:8080/api/health           # 发个 GET，看服务活没活
curl -v http://localhost:8080/api/health         # -v 显示完整请求/响应头，排查必备
# 带 header 和 body 的 POST（测登录接口）
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"123456"}'
```

**`curl -v` 的预期输出（节选）：**

```text
> GET /api/health HTTP/1.1
> Host: localhost:8080
>
< HTTP/1.1 200
< Content-Type: application/json
<
{"code":0,"msg":"ok","data":"UP"}
```

**怎么读**：`>` 开头是你**发出**的请求行和头，`<` 开头是服务器**返回**的状态码、响应头和 body。看到 `HTTP/1.1 200` 和 `{"code":0,...}`（我们项目的 `RtData` 结构）就说明服务正常。

> 💡 **前端类比**：`curl` 就是命令行里的 axios / fetch / Postman。`-v` 等于打开浏览器 Network 面板看完整的 Request Headers 和 Response Headers。后端排查接口，第一反应往往是「先 curl 一下看返回啥」。

### 测连通性：ping、telnet、nc

排查「A 服务连不上 B 服务」时，要分层确认：先看**能不能 ping 通**（网络层通不通），再看**端口开没开**（服务有没有在监听）。

```bash
ping 10.0.1.20                    # 测网络层能否到达对方主机
telnet 10.0.1.20 27017            # 测对方 27017（MongoDB）端口通不通
nc -zv 10.0.1.20 27017            # 同上，nc 更现代（-z 只测不发数据，-v 显示结果）
```

**`nc -zv` 通和不通的两种输出：**

```text
# 通：
Connection to 10.0.1.20 27017 port [tcp/*] succeeded!

# 不通（端口没开或被防火墙挡）：
nc: connect to 10.0.1.20 port 27017 (tcp) failed: Connection refused
```

**怎么读 → 结论**：
- `ping` 通但端口测**不通** → 网络没问题，是对方服务没起来或防火墙挡了端口。
- `ping` 都**不通** → 网络层就断了，查路由/安全组/网线，跟具体服务无关。
- `Connection refused`（拒绝）和 `timeout`（超时）含义不同：refused 通常是「机器在但没人监听这个端口」，timeout 通常是「被防火墙/安全组默默丢包」。

> 💡 **前端类比**：这套「分层排查」就像调试接口 404——先确认域名 DNS 解析对不对（≈ping 主机），再看端口/路径对不对（≈telnet 端口）。一层层缩小范围。网络更系统的内容在 [第二十二章 后端要懂的网络](/back-end/frontend-backend-guide/22-networking-for-backend)。

### 看本机 IP 与 hosts

```bash
ip addr               # 查本机所有网卡的 IP（云服务器看内网 IP 常用）
cat /etc/hosts        # 本机的「域名→IP」静态映射表
```

`/etc/hosts` 让你能把某个域名手动指到某个 IP，调试时很有用：

```text
127.0.0.1       localhost
10.0.1.30       svc-auth.internal     # 把内部域名手动指向某台机器
```

> 💡 **前端类比**：`/etc/hosts` 就是你本地开发时为了把 `api.test.com` 指到 `localhost` 而改的那个 hosts 文件——Windows/Mac 上你可能用 SwitchHosts 改过，原理完全一样。

---

## 21.6 磁盘与内存：事故重灾区

**线上事故里，「磁盘满」排前三。** 日志疯涨、临时文件没清，磁盘一满，服务写不了日志、数据库写不了数据，整个服务雪崩。

```bash
df -h                 # ★ 看各分区磁盘使用率（-h 人类可读）
```

**预期输出：**

```text
Filesystem      Size  Used Avail Use% Mounted on
/dev/vda1        40G   38G  1.2G  98% /
/dev/vdb1       100G   45G   50G  48% /data
```

**怎么读 → 结论**：根分区 `/` 已用 98%（`Use%`），只剩 1.2G——这就是定时炸弹。要赶紧找出谁占了空间。

```bash
du -sh *              # 看当前目录下每个文件/子目录占多大（-s 汇总，-h 可读）
du -sh /opt/app/logs  # 看某个目录占多大
```

**预期输出：**

```text
151M    svc-ai.jar
28G     logs            ← 元凶找到了！日志目录占了 28G
4.0K    application.yml
```

**怎么读 → 结论**：`logs` 目录占了 28G，多半是日志没做轮转（rotate）一直堆。处理方式：先 `tail` 确认不是正在排查的关键日志，再清理或归档旧日志，长期方案是配置日志轮转。

### 找大文件

```bash
# 在 / 下找出大于 500MB 的文件，按大小帮你揪出"磁盘刺客"
find / -type f -size +500M -exec ls -lh {} \; 2>/dev/null
```

### 看内存

```bash
free -h               # 看内存使用情况
```

**预期输出：**

```text
              total        used        free      shared  buff/cache   available
Mem:           3.8Gi       2.8Gi       210Mi        12Mi       833Mi       720Mi
Swap:          2.0Gi       1.1Gi       0.9Gi
```

**怎么读**：别只看 `free`（真正空闲）那列偏小就慌——Linux 会把暂时不用的内存拿去做 `buff/cache`（缓存），需要时随时让出来。**真正该看 `available`（可用）这列**，720Mi 表示还能给新程序分配这么多。如果 `available` 也很低、`Swap` 的 `used` 又涨得快，说明内存真的吃紧了——这往往是 Java 服务内存泄漏的前兆，深入排查见 [第二十章 OOM 与内存泄漏](/back-end/frontend-backend-guide/20-oom-memory-leak)。

> 💡 **前端类比**：`buff/cache` 就像浏览器的磁盘缓存——看着占了空间，但需要时立刻能腾出来，不是真的被占死。看内存别被 `free` 那列吓到，看 `available`。

---

## 21.7 环境变量：配置藏在哪

后端服务的数据库密码、运行模式（dev/prod）、`JAVA_HOME` 等，很多通过**环境变量**注入，不写死在代码里。

```bash
env                   # 列出当前所有环境变量
echo $JAVA_HOME       # 看某个变量的值（注意变量名前加 $）
echo $PATH            # 看可执行文件的搜索路径
export SPRING_PROFILES_ACTIVE=prod   # 设一个环境变量（仅当前会话有效）
```

**`echo $JAVA_HOME` 的预期输出：**

```text
/usr/lib/jvm/java-17-openjdk
```

`export` 设的变量**只在当前终端会话有效**，关掉就没了。要永久生效，写进用户的 `~/.bashrc`（每次开终端自动加载）：

```bash
# 编辑 ~/.bashrc，在末尾追加
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH

# 改完让它立刻生效（不用重开终端）
source ~/.bashrc
```

> 💡 **前端类比**：环境变量就是后端版的 `.env` 文件 + `process.env`。你在 Node 里用 `process.env.NODE_ENV` 区分环境，后端用 `SPRING_PROFILES_ACTIVE` 区分 dev/prod；`~/.bashrc` 则像是「全局生效的 .env，每开一个终端自动加载」。环境与配置的完整体系在 [第二十五章 配置与环境](/back-end/frontend-backend-guide/25-config-and-env)。

---

## 21.8 服务管理：systemctl 与 journalctl

生产上不会用 `nohup` 裸跑（崩了不会自动重启、开机不会自启）。规范做法是把服务交给 Linux 的 **systemd** 托管，用 `systemctl` 操作。Nginx、MySQL、你自己注册的 `svc-ai` 服务都可以这么管。

```bash
systemctl status svc-ai       # ★ 看服务状态（活着吗？什么时候启动的？最近日志几行）
sudo systemctl start svc-ai   # 启动
sudo systemctl stop svc-ai    # 停止（会发 SIGTERM，优雅停机）
sudo systemctl restart svc-ai # 重启
sudo systemctl enable svc-ai  # 设为开机自启
```

**`systemctl status` 的预期输出：**

```text
● svc-ai.service - AI Image Generation Service
   Loaded: loaded (/etc/systemd/system/svc-ai.service; enabled)
   Active: active (running) since Sun 2026-06-01 10:20:11 CST; 1h 5min ago
 Main PID: 12934 (java)
   Memory: 612.0M
   CGroup: /system.slice/svc-ai.service
           └─12934 java -Xmx512m -jar /opt/app/svc-ai.jar

Jun 01 10:20:14 svc-prod-01 java[12934]: Started SvcAiApplication in 3.2 seconds
```

**怎么读**：
- `Active: active (running)`——绿色圆点 + running，服务正常。若是 `failed`（红点）就是崩了，下面会显示退出码和错误。
- `enabled`——已设开机自启。
- `Main PID: 12934`——和前面 `ps`/`jps` 看到的对得上。

### journalctl：看系统服务的日志

systemd 托管的服务，日志统一进 journal，用 `journalctl` 查：

```bash
journalctl -u svc-ai -f          # ★ 实时跟踪 svc-ai 的日志（-u 指定服务，-f 跟踪）
journalctl -u svc-ai --since "10 min ago"   # 看最近 10 分钟的
journalctl -u svc-ai -n 200      # 看最后 200 行
```

> 💡 **前端类比**：`systemctl` 就像 PM2（你部署 Node 服务可能用过）——`pm2 start/stop/restart/status`、崩了自动拉起、开机自启，systemd 是它的「系统原生加强版」。`journalctl -u svc-ai -f` 则等价于 `pm2 logs svc-ai`。

---

## 21.9 上机第一分钟清单：SSH 上一台陌生服务器先敲什么

事故来了，你刚 SSH 上一台没见过的服务器，脑子一片空白。照着这个顺序敲，一分钟摸清状况：

```bash
# 1. 我是谁、在哪、这是什么机器
whoami && hostname && uptime
#   → 当前用户、主机名、开机多久 + load average（系统忙不忙）

# 2. 磁盘满没满（事故高频项，先排除）
df -h
#   → 看根分区 / 的 Use%，>90% 就要警惕

# 3. 内存还够吗
free -h
#   → 看 available 那列

# 4. 我关心的服务跑没跑（以 java 服务为例）
jps                    # 或 ps -ef | grep java
#   → 服务进程在不在、PID 是多少

# 5. 服务状态和监听端口
systemctl status svc-ai
ss -lntp | grep 8080
#   → 服务是 running 吗、8080 端口监听着吗

# 6. CPU 谁在烧
top                    # 看一眼按 q 退出
#   → 有没有进程把 CPU 占满

# 7. 最近的日志说了啥
journalctl -u svc-ai -n 100 --no-pager   # systemd 托管的
tail -n 100 /opt/app/logs/svc-ai.log      # 文件日志
#   → 有没有刷 ERROR / OutOfMemory / Connection refused
```

**怎么用这份清单**：1–3 步排除「机器层面」的问题（磁盘满、内存爆、负载高）；4–5 步确认「服务层面」（进程在不在、端口通不通）；6–7 步深入「现场」（资源谁占的、日志报了啥）。从外到内、从机器到服务到日志，一步步缩小范围——这正是 [第二十七章 排查方法论](/back-end/frontend-backend-guide/27-troubleshooting-methodology) 要系统讲的思路，这里先给你一个能照抄的起手式。

> 💡 **前端类比**：这就像线上出 bug 时你的固定动作——先看 Sentry 有没有报错、再看 Network 面板哪个请求挂了、再看 Console 日志。只是战场从浏览器换到了服务器，工具从 DevTools 换成了这几条命令。

---

## 小结

- **登录靠 SSH**：`ssh user@host`、`-p` 指定端口（`scp` 是大写 `-P`）；用 `ssh-keygen` 生成密钥、公钥放进服务器 `~/.ssh/authorized_keys` 即可免密。传文件用 `scp`（简单）或 `rsync`（增量、可续传）。
- **文件操作**记住 `ls -lah` / `cd` / `tail -f`（实时看日志）/ `grep -rn`（全局搜内容）；vim 保命三招：`i` 打字、`Esc` 退出编辑、`:wq` 保存或 `:q!` 放弃。
- **权限**用数字记最快：`755`=rwxr-xr-x（脚本/目录）、`644`=rw-r--r--（配置）、`600`=私钥。`chmod` 改权限、`chown` 改属主，权限最小化是安全底线。
- **进程**：`jps`/`ps -ef|grep` 找服务和 PID，`top` 看谁烧 CPU/内存，`lsof -i:8080` 查端口占用；停服务优先 `kill -15`（优雅），杀不掉才 `kill -9`（强制）。
- **资源事故**：`df -h` 查磁盘（满了是高频事故）、`du -sh *` 揪出大目录、`free -h` 看内存（看 `available` 而非 `free`）。
- **网络**：`ss -lntp` 看监听端口、`curl -v` 当命令行 axios、`ping`/`nc -zv` 分层测连通（区分 refused 与 timeout）。
- **服务托管**用 `systemctl status/start/stop/restart`，日志用 `journalctl -u 服务名 -f`——思路同前端的 PM2。
- 上机先敲「第一分钟清单」：从机器（磁盘/内存/负载）到服务（进程/端口）再到日志，从外到内缩小范围。

### 自测

1. 你要在服务器上排查一个「服务突然没响应」的问题。按本章的「第一分钟清单」，你会依次敲哪几条命令？每条分别想确认什么？
2. `kill -15` 和 `kill -9` 有什么本质区别？为什么生产环境停一个 Java 服务时应该优先用 `-15`？
3. `df -h` 显示根分区 `/` 已用 97%，你怀疑是某个目录的日志太大撑爆了。接下来用哪条命令、怎么一步步定位到具体是哪个目录占的空间？

### 下一章

机器会操作了，但微服务之间「连不连得通、慢在哪」还需要懂网络。进入 [第二十二章 后端要懂的网络](/back-end/frontend-backend-guide/22-networking-for-backend)。

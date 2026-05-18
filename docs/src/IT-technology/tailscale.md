# Tailscale 组网：让旧笔记本变成自己的小服务器

## 先说结论

可以。你的旧笔记本完全可以当一台“家用小服务器”。

比如你有这些设备：

- 旧笔记本：放在家里，负责跑服务
- 主力电脑：平时写代码、办公
- 手机 / 平板：出门在外也想访问家里的服务

装上 Tailscale 后，这几台设备会进入同一个私人网络。哪怕它们不在同一个 Wi-Fi 下，也可以像在一个局域网里一样互相访问。

简单理解：

```text
主力电脑 / 手机 / 平板
        |
        | 通过 Tailscale 私人网络访问
        v
家里的旧笔记本
```

你可以把旧笔记本当成：

- 个人文件服务器
- 下载机
- 代码测试服务器
- 本地网页服务
- 远程桌面机器
- 家庭自动化或小工具运行机器

## Tailscale 是什么

Tailscale 是一个帮你“安全组网”的工具。

它做的事情可以这样理解：

以前你想从外面访问家里的电脑，通常要折腾这些东西：

- 路由器端口转发
- 公网 IP
- 动态域名
- 防火墙规则
- 内网穿透工具

Tailscale 把这些复杂步骤大幅简化了。你只需要在每台设备上安装 Tailscale，并登录同一个账号，这些设备就会自动组成一个私人网络。

Tailscale 底层使用 WireGuard，但你不需要手动配置 WireGuard。它把比较麻烦的配置、设备发现、登录认证都处理好了。

## 它能做什么

### 1. 跨网络访问自己的设备

你的手机用 5G，旧笔记本在家里 Wi-Fi，主力电脑在公司网络，只要它们都登录了同一个 Tailscale 账号，就可以互相访问。

每台设备都会有一个 Tailscale IP，一般长这样：

```text
100.x.y.z
```

你可以用这个 IP 访问旧笔记本上的服务。

### 2. 访问本地服务

假设旧笔记本上跑了一个网页服务：

```bash
python -m http.server 8000
```

旧笔记本的 Tailscale IP 是：

```text
100.64.12.34
```

那么其他设备可以打开：

```text
http://100.64.12.34:8000
```

这样就能访问旧笔记本上的网页服务。

### 3. 远程 SSH 或远程桌面

如果旧笔记本装的是 Linux，可以用 SSH 连接：

```bash
ssh 用户名@100.64.12.34
```

如果旧笔记本装的是 Windows，可以用远程桌面连接它的 Tailscale IP：

```text
100.64.12.34
```

前提是旧笔记本开启了对应的 SSH 或远程桌面服务。

### 4. 只给自己访问，不暴露到公网

Tailscale 默认更适合“自己设备之间互相访问”。

也就是说，你旧笔记本上的服务不需要直接暴露到公网。只有加入你 Tailscale 网络的设备，才可以访问它。

这比直接把端口暴露到公网更适合个人使用。

### 5. 也可以把服务分享给公网

Tailscale 有一个叫 Funnel 的功能，可以把本地服务公开到互联网。

不过个人使用时，建议先用普通的 Tailscale 私人访问。只有你明确需要“别人不用装 Tailscale 也能访问”时，再考虑 Funnel。

## 旧笔记本当服务器的准备

旧笔记本能不能当服务器，关键看三件事：

### 1. 它能不能长期运行

建议设置：

- 接电源使用
- 关闭自动睡眠
- 合上盖子不休眠
- 网络保持连接

如果旧笔记本一睡眠，其他设备就访问不到它。

### 2. 系统尽量稳定

旧笔记本可以装：

- Windows：上手简单，适合远程桌面、文件共享、跑一些桌面工具
- Ubuntu / Debian：更适合长期跑服务、Docker、SSH、自动化脚本

如果你只是先试试，Windows 也完全可以。

如果你后面想长期当服务器，Linux 会更省心。

### 3. 服务要正常启动

Tailscale 只负责把设备连起来。

真正提供能力的，还是旧笔记本上运行的服务。

比如：

- 想访问网页：旧笔记本要跑 Web 服务
- 想远程命令行：旧笔记本要开启 SSH
- 想远程桌面：旧笔记本要开启远程桌面
- 想访问文件：旧笔记本要开启文件共享、NAS 软件或其他文件服务

## 实战：把旧笔记本加入 Tailscale

下面用“旧笔记本作为服务器，其他设备作为访问端”的方式来做。

## 第一步：注册或登录 Tailscale

打开官网：

[https://tailscale.com/](https://tailscale.com/)

可以用 GitHub、Google、Microsoft 等账号登录。

登录后，你会有一个自己的 Tailnet。

Tailnet 可以理解成你的 Tailscale 私人网络。后面加入的设备，都会在这个网络里。

## 第二步：旧笔记本安装 Tailscale

### Windows 旧笔记本

1. 打开 Tailscale Windows 安装页面：

   [https://tailscale.com/docs/install/windows](https://tailscale.com/docs/install/windows)

2. 下载 `.exe` 安装包
3. 安装完成后，右下角托盘会出现 Tailscale 图标
4. 右键图标，点击登录
5. 用你的账号登录

登录成功后，这台旧笔记本就加入你的 Tailnet 了。

### Linux 旧笔记本

常见 Linux 可以执行：

```bash
curl -fsSL https://tailscale.com/install.sh | sh
```

然后启动并登录：

```bash
sudo tailscale up
```

终端会给你一个登录链接，打开后用你的账号登录。

如果你不喜欢 `curl | sh` 这种方式，可以去官方 Linux 安装页按系统选择手动安装：

[https://tailscale.com/docs/install/linux](https://tailscale.com/docs/install/linux)

## 第三步：查看旧笔记本的 Tailscale IP

在旧笔记本上查看：

```bash
tailscale ip -4
```

会得到类似：

```text
100.64.12.34
```

这个 IP 就是其他设备访问旧笔记本时要用的地址。

也可以在 Tailscale 管理后台查看设备和 IP：

[https://login.tailscale.com/admin/machines](https://login.tailscale.com/admin/machines)

## 第四步：其他设备也安装 Tailscale

在主力电脑、手机、平板上安装 Tailscale，并登录同一个账号。

安装入口：

[https://tailscale.com/docs/install](https://tailscale.com/docs/install)

常见设备：

- Windows：下载安装包，登录账号
- macOS：下载安装包或从 App Store 安装
- iPhone / iPad：App Store 搜索 Tailscale
- Android：应用商店搜索 Tailscale

只要这些设备都显示在线，就说明它们已经在同一个私人网络里。

## 第五步：测试能不能连通旧笔记本

在主力电脑上执行：

```bash
ping 100.64.12.34
```

把 `100.64.12.34` 换成你的旧笔记本 Tailscale IP。

如果能 ping 通，说明组网已经成功。

有些系统默认禁止 ping，不通也不一定代表失败。更实用的方式是直接测试具体服务。

## 场景一：旧笔记本跑一个网页服务

在旧笔记本上找一个测试目录，执行：

```bash
python -m http.server 8000
```

然后在主力电脑或手机浏览器打开：

```text
http://100.64.12.34:8000
```

如果能看到目录列表，说明访问成功。

注意：有些服务默认只监听 `127.0.0.1`，这样其他设备访问不到。最好让服务监听 `0.0.0.0`。

比如很多开发服务可以这样启动：

```bash
npm run dev -- --host 0.0.0.0
```

或者：

```bash
vite --host 0.0.0.0
```

## 场景二：用 SSH 管理 Linux 旧笔记本

如果旧笔记本是 Linux，先确认 SSH 服务已安装并启动。

Ubuntu / Debian 可以这样安装：

```bash
sudo apt update
sudo apt install openssh-server
sudo systemctl enable --now ssh
```

然后在主力电脑连接：

```bash
ssh 用户名@100.64.12.34
```

如果你想用 Tailscale 自带的 SSH 功能，也可以参考官方说明：

[https://tailscale.com/docs/features/tailscale-ssh](https://tailscale.com/docs/features/tailscale-ssh)

不过刚开始建议先用系统自带 SSH，更容易理解。

## 场景三：远程桌面连接 Windows 旧笔记本

如果旧笔记本是 Windows，可以开启远程桌面。

大致路径：

```text
设置 -> 系统 -> 远程桌面 -> 启用远程桌面
```

然后在主力电脑打开“远程桌面连接”，输入旧笔记本的 Tailscale IP：

```text
100.64.12.34
```

注意：

- Windows 家庭版通常不能作为远程桌面被连接
- Windows 专业版、企业版更适合
- 旧笔记本需要设置登录密码
- 防火墙可能需要允许远程桌面

如果不想折腾 Windows 自带远程桌面，也可以用 RustDesk、向日葵这类工具。Tailscale 负责提供安全的设备连接通道。

## 场景四：把旧笔记本变成文件服务器

最简单的方式是先跑一个临时文件服务：

```bash
python -m http.server 8000
```

然后其他设备访问：

```text
http://100.64.12.34:8000
```

这适合临时下载文件，不适合长期管理文件。

长期使用可以考虑：

- Windows 文件共享
- Samba
- Syncthing
- File Browser
- Nextcloud

如果你只是想在自己的设备之间传文件，Tailscale 还有 Taildrop，可以在设备之间直接传文件。

## 场景五：访问旧笔记本所在局域网的其他设备

如果家里还有其他设备不能安装 Tailscale，比如：

- 路由器后台
- NAS
- 摄像头
- 打印机

可以让旧笔记本作为“子网路由器”。

这样外面的设备先连到旧笔记本，再通过旧笔记本访问家里局域网的其他设备。

这个功能比普通组网多一步配置，建议等你先把旧笔记本自身访问跑通后再做。

官方说明：

[https://tailscale.com/docs/route](https://tailscale.com/docs/route)

子网路由配置示例：

[https://tailscale.com/kb/1406/quick-guide-subnets](https://tailscale.com/kb/1406/quick-guide-subnets)

## 私人访问和公网访问的区别

这里很容易混。

### 普通 Tailscale 访问

只有加入你 Tailnet 的设备能访问。

例如：

```text
http://100.64.12.34:8000
```

适合：

- 自己访问
- 家人设备访问
- 私人开发服务
- 家庭服务器

这是最推荐的方式。

### Tailscale Funnel

Funnel 可以让没有安装 Tailscale 的人，也从公网访问你的本地服务。

适合：

- 临时展示一个网页
- 给别人看一个 Demo
- 临时开放一个小服务

但它会把服务公开出去，使用前要确认服务本身没有敏感内容。

官方说明：

[https://tailscale.com/docs/features/tailscale-funnel](https://tailscale.com/docs/features/tailscale-funnel)

## 常见问题

### 旧笔记本关机后还能访问吗？

不能。

旧笔记本必须开机、联网、Tailscale 在线，其他设备才能访问它。

### 旧笔记本睡眠后还能访问吗？

通常不能。

建议在电源设置里关闭睡眠。

### 一定要公网 IP 吗？

不需要。

这正是 Tailscale 好用的地方。大部分家庭宽带没有公网 IP，也能用 Tailscale 访问自己的设备。

### 需要路由器端口转发吗？

普通 Tailscale 访问不需要。

你不用在路由器里开放端口。

### 手机在外面能访问家里的旧笔记本吗？

可以。

手机安装 Tailscale 并登录同一个账号后，用旧笔记本的 Tailscale IP 访问即可。

### 访问不到服务怎么办？

按这个顺序排查：

1. 旧笔记本有没有开机
2. 旧笔记本 Tailscale 是否在线
3. 访问端是否也登录了同一个 Tailscale 账号
4. IP 是否写对
5. 服务是否真的在旧笔记本上运行
6. 服务是否监听 `0.0.0.0`，而不是只监听 `127.0.0.1`
7. 旧笔记本防火墙是否放行对应端口
8. 换一个简单服务测试，比如 `python -m http.server 8000`

## 安全建议

虽然 Tailscale 比直接暴露公网端口安全很多，但还是建议注意：

- Tailscale 账号开启双重验证
- 不用的设备及时从后台移除
- 旧笔记本设置强密码
- 不要随便开启 Funnel
- 不要把重要服务直接裸奔，至少要有登录验证
- 定期更新系统和 Tailscale 客户端

## 推荐使用路线

如果你是第一次用，可以按这个顺序来：

1. 旧笔记本和主力电脑都安装 Tailscale
2. 确认两台设备都在线
3. 查看旧笔记本的 Tailscale IP
4. 用 `python -m http.server 8000` 测试网页访问
5. 再尝试 SSH、远程桌面或文件服务
6. 最后再研究子网路由、Funnel 等进阶功能

先把“自己的设备能访问旧笔记本”跑通，后面就很好扩展了。


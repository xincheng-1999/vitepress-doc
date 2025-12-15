# MySQL 安装与环境配置

这一节我们将安装 MySQL 数据库服务，并使用你熟悉的 **DBeaver** 作为图形化管理工具。

## 1. 下载与安装 MySQL

对于 Windows 用户，推荐使用 **MySQL Installer**，它可以一键安装所有必要的组件。

1.  **下载**：访问 [MySQL Community Downloads](https://dev.mysql.com/downloads/installer/)，下载 **Windows (x86, 32-bit), MSI Installer** (虽然写着 32-bit，但也包含 64-bit 组件)。
2.  **安装步骤**：
    *   运行安装包，选择 **"Server only"** (如果你只需要数据库服务) 或者 **"Developer Default"** (包含 Workbench 等工具，但既然你用 DBeaver，Server only 足够了)。
    *   **Type and Networking**: 默认即可 (端口 3306)。
    *   **Authentication Method**: 推荐选择 **Use Legacy Authentication Method** (兼容性更好)，或者默认的 Strong Password Encryption。
    *   **Accounts and Roles**: 设置 **Root Password** (超级管理员密码)。**请务必记住这个密码！** (比如设置成 `root` 或者 `123456` 用于本地测试)。
    *   **Windows Service**: 勾选 "Start the MySQL Server at System Startup"，这样开机就会自动启动数据库。
    *   一路 Next 直到 Execute 安装完成。

## 2. 验证安装

打开你的终端 (PowerShell 或 CMD)，输入：

```bash
mysql --version
```

如果提示“不是内部或外部命令”，你需要配置环境变量：
1.  找到 MySQL 安装目录 (通常在 `C:\Program Files\MySQL\MySQL Server 8.0\bin`)。
2.  将这个路径添加到系统的 **Path** 环境变量中。

再次输入 `mysql -u root -p`，输入密码，如果出现 `mysql>` 提示符，说明安装成功！

## 3. 使用 DBeaver 连接

既然你熟悉 DBeaver，那我们就跳过 MySQL Workbench，直接用 DBeaver 连接本地数据库。

1.  打开 DBeaver。
2.  点击左上角的 **"新建数据库连接"** (插头图标)。
3.  选择 **MySQL**，点击下一步。
4.  **Server Host**: `localhost`
5.  **Port**: `3306`
6.  **Database**: 留空 (或者填 `sys`)
7.  **Username**: `root`
8.  **Password**: 你刚才设置的密码。
9.  点击 **"测试连接 (Test Connection)"**。如果提示 "Connected"，点击完成。

现在，你已经拥有了一个运行在本地的 MySQL 服务器，并且通过 DBeaver 连接上了它。接下来我们可以开始敲代码了！

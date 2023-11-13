# Mac常用知识

## 设置环境变量

在路径`/Users/gaoxincheng/.bash_profile`中可以导出bin中的可执行文件：

```
export PATH=${PATH}:/usr/local/mongodb/bin
```

以上配置可以把MongoDB文件夹中的可执行文件置为全局

如果有多个需要配置可以在后面添加：后添加路径

配置生效需要执行

`source ~/.bash_profile`

## 赋予文件夹操作权限

`sudo chown 【用户名】 /usr/local/mongodb/data`

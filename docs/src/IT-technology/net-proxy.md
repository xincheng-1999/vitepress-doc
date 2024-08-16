# 终端shell网络代理

开启梯子后大部分请求会走代理，但是终端可能不会走代理无法访问github

需要手动设置代理：

```
# bash 命令
export http_proxy=http://your_proxy_ip:your_proxy_port
export https_proxy=http://your_proxy_ip:your_proxy_port # 访问https的就需要设置这个
# 比如常用的梯子 export https_proxy=http://localhost:7890 && export http_proxy=http://localhost:7890

```



# Clash Verge Rev：让 Claude/Anthropic 流量走专用订阅

## 目标

现在有两个 Clash Verge 订阅：

- 普通订阅：给日常网站、开发、浏览器等其他流量使用。
- Claude 专用订阅：流量不多，只希望 Claude / Anthropic 相关请求使用。

最终效果是：

```text
claude.ai / anthropic.com 等请求 -> Claude 策略组 -> Claude 专用订阅节点
其他请求 -> 普通订阅原本的规则和节点
```

如果 Claude 专用节点失败，请求会失败，不会自动跑到普通订阅，前提是 `Claude` 策略组里只引用 Claude 专用订阅集合。

## 适用版本

本文以 Clash Verge Rev v2.4.7 为例。

这个版本里，“全局扩展覆写配置”的角标是 `merge`，但新建配置时可能只看到 `Local` 和 `Remote`，没有单独的 `Merge` 选项。实际配置时需要配合当前普通订阅的右键菜单：

- 全局扩展覆写配置：添加 Claude 专用订阅集合。
- 编辑代理组：创建 `Claude` 策略组。
- 编辑规则：把 Claude / Anthropic 域名规则前置到 `Claude` 策略组。

## 第一步：添加普通订阅

先在 Clash Verge 里添加普通订阅，并把它作为当前正在使用的配置。

普通订阅负责除 Claude / Anthropic 以外的其他流量。后面的 Claude 分流是在这个普通订阅的基础上增强，不需要把普通订阅重写成一个新的本地配置。

## 第二步：编辑全局扩展覆写配置

打开“全局扩展覆写配置”，默认内容可能类似这样：

```yaml
# Profile Enhancement Merge Template for Clash Verge

profile:
  store-selected: true

dns:
  use-system-hosts: false
```

在下面追加 `proxy-providers`，把 Claude 专用订阅作为一个代理集合引入：

```yaml
# Profile Enhancement Merge Template for Clash Verge

profile:
  store-selected: true

dns:
  use-system-hosts: false

proxy-providers:
  claude-sub:
    type: http
    url: "这里填你的Claude专用订阅链接"
    interval: 86400
    path: ./providers/claude-sub.yaml
    health-check:
      enable: true
      url: https://www.gstatic.com/generate_204
      interval: 300
```

这里的关键名字是：

```text
claude-sub
```

它只是“代理集合”，表示 Claude 专用订阅里的节点来源。它还不是策略组，所以在代理页面的“代理集合”里看到剩余流量是正常的。

保存后重新应用配置。如果能在“代理集合”里看到 Claude 专用订阅的剩余流量，说明这一步已经成功。

## 第三步：右键普通订阅，编辑代理组

回到“配置 / Profiles”页面，右键当前正在使用的普通订阅，选择“编辑代理组”。

添加下面内容：

```yaml
prepend:
  - type: 'select'
    name: 'Claude'
    use:
      - 'claude-sub'

append: []
delete: []
```

这一步创建了一个策略组：

```text
Claude
```

它的节点来源是上一步创建的代理集合：

```yaml
use:
  - 'claude-sub'
```

也就是说：

```text
Claude 策略组 -> 使用 claude-sub 代理集合里的节点
```

`select` 表示手动选择节点。保存并重新应用配置后，可以在代理页面找到 `Claude` 组，并在里面手动选择一个 Claude 专用节点。

如果写成下面这样也能用，但 `interval`、`timeout`、`max-failed-times`、`lazy` 主要给 `url-test`、`fallback` 这类自动测速组使用，对 `select` 不是必要字段：

```yaml
prepend:
  - type: 'select'
    name: 'Claude'
    interval: 300
    timeout: 5000
    max-failed-times: 5
    lazy: true
    use:
      - 'claude-sub'

append: []
delete: []
```

## 第四步：右键普通订阅，编辑规则

仍然在“配置 / Profiles”页面，右键当前普通订阅，选择“编辑规则”。

添加下面内容：

```yaml
prepend:
  - 'DOMAIN-SUFFIX,anthropic.com,Claude'
  - 'DOMAIN-SUFFIX,claude.ai,Claude'
  - 'DOMAIN-SUFFIX,anthropic.systems,Claude'
  - 'DOMAIN-SUFFIX,claudeusercontent.com,Claude'
  - 'DOMAIN,api.anthropic.com,Claude'
  - 'DOMAIN,console.anthropic.com,Claude'

append: []
delete: []
```

注意缩进，每一条规则都要和第一条同级。

`DOMAIN-SUFFIX,anthropic.com,Claude` 已经能覆盖 `api.anthropic.com`、`console.anthropic.com`、`docs.anthropic.com` 等子域名，所以后面两条 `DOMAIN` 规则保留也可以，但不是必须。

## 两段配置是怎么关联的

它们靠名字关联。

代理组里定义了一个叫 `Claude` 的策略组：

```yaml
name: 'Claude'
```

规则里的最后一个字段引用这个策略组：

```yaml
'DOMAIN-SUFFIX,claude.ai,Claude'
```

这条规则的含义是：

```text
只要目标域名是 claude.ai 或它的子域名，就交给 Claude 策略组处理。
```

而 `Claude` 策略组又通过 `use` 引用了 `claude-sub`：

```yaml
use:
  - 'claude-sub'
```

完整链路就是：

```text
claude-sub
= Claude 专用订阅的节点集合

Claude 策略组
= 使用 claude-sub 里的节点

DOMAIN-SUFFIX,claude.ai,Claude
= claude.ai 的请求交给 Claude 策略组

Claude 策略组中手动选中的节点
= 请求最终使用的具体节点
```

访问 Claude 时，Clash 的处理路径是：

```text
访问 https://claude.ai
-> 命中 DOMAIN-SUFFIX,claude.ai,Claude
-> 进入 Claude 策略组
-> 使用 Claude 组里手动选择的具体节点
```

## 失败时是否会走普通节点

不会自动走普通节点，前提是 `Claude` 策略组里只有 Claude 专用订阅集合：

```yaml
use:
  - 'claude-sub'
```

不要在 `Claude` 组里加入普通订阅节点、普通策略组或 `DIRECT`。这样 Claude 专用节点失败时，请求会失败或超时，而不是回落到普通订阅。

真正需要注意的是规则是否命中。如果某些 Claude 页面请求了第三方域名，例如登录、统计、验证码、公共 CDN，这些不属于 `anthropic.com` 或 `claude.ai` 的请求仍然会按普通订阅原规则处理。

## 第五步：验证

保存所有配置后，重新应用当前普通订阅，并确保模式是：

```text
Rule / 规则
```

然后检查：

1. 打开“代理”页面，找到 `Claude` 策略组。
2. 进入 `Claude` 组，选择 Claude 专用订阅里的具体节点。
3. 打开“连接 / Connections”页面。
4. 访问 `https://claude.ai`。
5. 查看连接记录里 `claude.ai` 是否走 `Claude` 策略组。

如果连接里显示 `Claude`，说明分流成功。

## 常见问题

### 代理集合里只看到剩余流量，正常吗

正常。“代理集合”显示的是 `proxy-providers`，也就是 `claude-sub` 这种节点来源。策略组需要在“编辑代理组”里创建，名字是 `Claude`。

### Rule 模式可以吗

可以，而且必须用 `Rule / 规则` 模式。`Global / 全局` 模式会让所有流量走同一个选择的节点，不适合这种按域名分流的场景。

### 为什么规则要写在 prepend 里

Clash 的规则是从上往下匹配的。普通订阅里通常最后会有 `MATCH` 规则，如果 Claude 规则放在后面，就可能永远匹配不到。

所以 Claude / Anthropic 规则要放到 `prepend`，也就是前置规则里。

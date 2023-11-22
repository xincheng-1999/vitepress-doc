# node包管理器

# npm

### 查看源

`npm get registry`

### 临时修改

`npm --registry https://registry.npm.taobao.org install any-touch`

### 还原

`npm config set registry https://registry.npmjs.org`

# yarn

### 查看源

`yarn config get registry`

### 临时修改

`yarn add any-touch@latest --registry=https://registry.npmjs.org/`

### 持久修改

`yarn config set registry https://registry.npm.taobao.org/`

### 还原

`yarn config set registry https://registry.yarnpkg.com`

### 清除缓存

`yarn cache clean`

# pnpm

### 查看源

`pnpm get registry`

### 临时修改

`pnpm --registry https://registry.npm.taobao.org install any-touch`

### 持久使用

`pnpm config set registry https://registry.npm.taobao.org`

### 还原

`pnpm config set registry https://registry.npmjs.org`

### 下载

```
pnpm i package1 --filter package2 # Monorepo格式下把package1安装到package2中

pnpm i --force # 强制重新安装，不使用缓存
```

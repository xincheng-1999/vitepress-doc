// One learning order, shared by the course pages. Old filenames remain stable URLs.
const page = (text, slug) => ({ text, link: `/back-end/frontend-backend-guide/${slug}.md` });

export const backendStartSidebar = [
  {
    text: "前端转后端",
    items: [
      { text: "后端学习入口", link: "/back-end/index.md" },
      page("主线学习地图", "index"),
      page("后端功能地图", "39-backend-feature-map"),
      page("场景术语词典", "90-glossary"),
    ],
  },
];

export const backendGuideSidebar = [
  {
    text: "开始学习 / 随时查词",
    items: [
      page("学习地图：从这里开始", "index"),
      page("学习节奏与检查点", "93-learning-path"),
      page("170 条场景术语", "90-glossary"),
    ],
  },
  {
    text: "A · 请求与网络（先学）",
    collapsed: false,
    items: [
      page("A1. 后端究竟负责什么", "01-backend-mindset"),
      page("A2. 系统地图：先从单体开始", "02-architecture-overview"),
      page("A3. IP、端口、DNS、TCP 与 TLS", "22-networking-for-backend"),
      page("A4. HTTP、Cookie、CORS 与缓存", "22a-http-and-browser"),
      page("A5. 代理、网关与服务发现", "22b-proxy-and-discovery"),
      page("A6. 内网、堡垒机与访问权限", "22c-private-network-and-access"),
      page("A7. 请求进入 Spring 之后", "03-request-lifecycle"),
    ],
  },
  {
    text: "B · 写出第一个接口",
    collapsed: true,
    items: [
      page("B1. Java 最小知识集", "05-java-crash-course"),
      page("B2. Spring、Bean 与注入", "06-spring-boot-ioc-di"),
      page("B3. 三层分工与工程阅读", "04-three-layer-and-structure"),
      page("B4. 可运行任务 CRUD", "07-build-a-crud-api"),
      page("B5. 接口契约与 API 设计", "34-api-design"),
    ],
  },
  {
    text: "C · 数据与权限（核心）",
    collapsed: true,
    items: [
      page("C1. 数据建模、SQL 与索引", "10-sql-and-indexes"),
      page("C2. 事务与并发更新", "11-transactions-consistency"),
      page("C3. 登录、安全与数据归属", "32-security"),
      page("C4. 实验：Session 登录与隔离", "32a-session-login-lab"),
      page("C5. 后端测试与失败验收", "35-testing"),
    ],
  },
  {
    text: "D · 真实功能与按需扩展",
    collapsed: true,
    items: [
      page("D1. 上传、导出、调度与回调", "39-backend-feature-map"),
      page("D2. Redis：缓存、限流与锁", "12-redis-in-practice"),
      page("D3. 异步任务与 MQ 可靠性", "33-mq-reliability"),
      page("D4. 连接池与资源上限", "13-connection-pools"),
    ],
  },
  {
    text: "E · 部署、排错与交付",
    collapsed: true,
    items: [
      page("E1. Linux：进程、权限与端口", "21-linux-server-essentials"),
      page("E2. 配置、环境与密钥", "25-config-and-env"),
      page("E3. Docker + MySQL 单体部署", "23-docker-in-practice"),
      page("E4. 日志与第一次排错", "26-reading-logs"),
      page("E5. 综合实战与交付验收", "38-capstone-feature-to-prod"),
    ],
  },
  {
    text: "查阅 · 语言、存储与命令",
    collapsed: true,
    items: [
      page("注解速查（旧场景示例）", "08-annotations-cheatsheet"),
      page("SQL / NoSQL 选型", "09-sql-vs-nosql"),
      { text: "Java 细节专题", link: "/back-end/java/01-environment.md" },
      { text: "数据库细节专题", link: "/back-end/database/index.md" },
      page("命令速查（按环境使用）", "91-command-cheatsheet"),
      page("学习资源", "92-learning-resources"),
    ],
  },
  {
    text: "选修 · 并发与 JVM",
    collapsed: true,
    items: [
      page("并发与多线程", "14-single-thread-to-multithread"),
      page("线程池与 Executor", "15-thread-pools-executor"),
      page("线程安全与锁", "16-thread-safety-locks"),
      page("Java 异步编程", "17-async-programming"),
      page("JVM 内存模型", "18-jvm-memory-model"),
      page("垃圾回收 GC", "19-garbage-collection"),
      page("OOM 与内存泄漏", "20-oom-memory-leak"),
    ],
  },
  {
    text: "选修 · 规模化与线上专题",
    collapsed: true,
    items: [
      page("Kubernetes（不作入门前置）", "24-kubernetes-in-practice"),
      page("排查方法论", "27-troubleshooting-methodology"),
      page("排查实战手册", "28-troubleshooting-playbook"),
      page("诊断工具箱", "29-diagnostic-toolbox"),
      page("可观测性", "30-observability"),
      page("性能与容量", "31-performance-concurrency"),
      page("CI/CD 与部署", "36-cicd-deployment"),
      page("选修练习（旧场景片段）", "37-hands-on-exercises"),
    ],
  },
];

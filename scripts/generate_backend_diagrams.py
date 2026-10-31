"""Generate the editable SVG diagrams used by the backend learning spine."""
from html import escape
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / 'docs/src/public/backend-guide'
FONT = "system-ui, -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif"


def text(x, y, value, size=18, color='#334155', weight='400', anchor='start'):
    return f'<text x="{x}" y="{y}" font-size="{size}" fill="{color}" font-weight="{weight}" text-anchor="{anchor}">{escape(value)}</text>'


def box(x, y, w, h, heading, lines=(), fill='#eff6ff', stroke='#93c5fd'):
    result = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{fill}" stroke="{stroke}" stroke-width="1.5"/>'
    result += text(x+20, y+32, heading, 21, '#0f172a', '650')
    for i, line in enumerate(lines):
        result += text(x+20, y+60+i*25, line, 17)
    return result


def arrow(points, label=None, lx=0, ly=0, dashed=False):
    dash = ' stroke-dasharray="7 5"' if dashed else ''
    result = f'<polyline points="{points}" fill="none" stroke="#475569" stroke-width="2" marker-end="url(#arrow)"{dash}/>'
    return result + (text(lx, ly, label, 16) if label else '')


def save(name, title, description, height, body, subtitle=''):
    OUT.mkdir(parents=True, exist_ok=True)
    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="960" height="{height}" viewBox="0 0 960 {height}" role="img" aria-labelledby="title desc">
<title id="title">{escape(title)}</title><desc id="desc">{escape(description)}</desc>
<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10 Z" fill="#475569"/></marker></defs>
<rect width="960" height="{height}" rx="18" fill="#f8fafc"/>
<g font-family="{FONT}">{text(32, 45, title, 28, '#0f172a', '700')}{text(32, 76, subtitle, 17)}{body}</g></svg>'''
    (OUT / name).write_text(svg, encoding='utf-8')


def generate():
    body = ''
    stages = [
        ('A · 请求与网络', 'IP / DNS / TCP / TLS / HTTP / 代理', '产物：画出浏览器到数据库的路径'),
        ('B · 最小语言与接口', 'Java 17 / Spring / 分层 / CRUD', '产物：本机跑通任务 API 和测试'),
        ('C · 数据与权限', 'SQL / 索引 / 事务 / 登录 / 归属', '产物：别人不能改我的任务'),
        ('D · 真实功能与异步', '附件 / 导出 / 调度 / 缓存 / 消息', '产物：解释需求、失败与恢复'),
        ('E · 部署与排错', '配置 / Docker / 日志 / 备份恢复', '产物：可复现交付与故障证据'),
    ]
    for i, (heading, topic, output) in enumerate(stages):
        y = 105+i*114
        body += box(40, y, 420, 87, heading, [topic])
        body += arrow(f'460,{y+43} 500,{y+43}')
        body += box(510, y+8, 410, 71, output, [], '#ecfdf5', '#6ee7b7')
        if i < 4:
            body += arrow(f'250,{y+87} 250,{y+109}')
    body += text(40, 705, '先完成一个小单体；JVM 调优、微服务与 Kubernetes 放到选修。', 19)
    save('core-learning-map.svg', '一条主线，五个可以验收的阶段', '依次学习网络、接口、数据权限、功能异步、部署排错，每阶段以能交付的产物检验。', 735, body, '旧文件编号保留地址；阅读顺序以阶段 A → E 为准。')

    body = box(40, 115, 400, 220, '前端 · 用户体验边界', ['表单提示、交互反馈、loading', '页面状态、渲染与错误展示', '可以防误操作，但不能防绕过'], '#eff6ff', '#93c5fd')
    body += box(520, 115, 400, 220, '后端 · 可信业务边界', ['验证身份、动作权限与数据归属', '重新校验输入，执行业务规则', '并发、持久化、故障与恢复'], '#ecfdf5', '#6ee7b7')
    body += arrow('440,225 510,225', 'HTTP', 449, 205)
    body += box(270, 420, 420, 110, '共享契约', ['方法、路径、状态、数据、权限、幂等', '体验校验 ≠ 授权；超时 ≠ 没执行'], '#fff7ed', '#fdba74')
    body += arrow('240,335 240,390 370,390 370,412')
    body += arrow('720,335 720,390 590,390 590,412')
    save('core-responsibility.svg', '同一个功能，两侧不同的责任', '前端负责用户体验，后端提供可信身份、业务和数据边界，两者通过明确接口契约协作。', 570, body)

    body = box(35, 110, 260, 110, '1 · 先跑通', ['浏览器 / curl', 'HTTP → task-api:8080'])
    body += box(370, 110, 555, 110, 'H2 内存库 · 同一 Java 进程', ['免安装，理解完整链路', '重启丢数据；不是生产持久化'])
    body += arrow('295,165 360,165')
    body += box(35, 280, 260, 110, '2 · 再持久化', ['task-api 独立进程', 'JDBC / TCP 访问数据库'])
    body += box(370, 280, 555, 110, 'MySQL · 事实源', ['数据库独立运行，数据卷持久化', '还要做约束、迁移、备份与恢复'], '#ecfdf5', '#6ee7b7')
    body += arrow('295,335 360,335')
    body += box(35, 450, 260, 110, '3 · 按问题引入', ['先测量，不先堆工具', '单体也可以多实例部署'])
    body += box(370, 450, 555, 110, '缓存 / 后台任务 / 对象存储', ['Redis 加速，MQ 解耦，Worker 做重活', '每多一个依赖，就多一组故障边界'], '#fff7ed', '#fdba74')
    body += arrow('295,505 360,505')
    save('core-system-map.svg', '从最小单体开始，不从全家桶开始', '系统分三步演进：内嵌H2跑通链路、独立MySQL提供持久数据、需要时引入缓存消息和存储。', 610, body)

    body = ''
    steps = [('浏览器 / curl / 可选代理', '传输 HTTP 字节；代理与应用是不同连接'),
             ('Tomcat + Servlet Filter', '监听、请求 ID、日志与安全过滤器链'),
             ('DispatcherServlet + MVC', '路由匹配 → Interceptor → JSON 绑定 / 校验'),
             ('Controller → Service', '协议适配 → 授权、业务规则与事务边界'),
             ('Repository / JDBC → 数据库', '参数化 SQL、借还连接、提交或回滚')]
    for i, (h, line) in enumerate(steps):
        y = 110+i*105
        body += box(40, y, 625, 83, h, [line])
        if i < 4:
            body += arrow(f'345,{y+83} 345,{y+100}')
    body += box(700, 110, 220, 150, '网络 / 代理错误', ['可能没有 HTTP', '502 / 504 看上游', '不能推断已回滚'], '#fff7ed', '#fdba74')
    body += box(700, 320, 220, 195, 'MVC 异常边界', ['Advice 映射异常', '不兜所有 Filter', '不兜后台 Worker', '响应保留请求 ID'], '#f5f3ff', '#c4b5fd')
    body += text(40, 675, '提交成功后仍可能响应丢失：客户端超时，不代表业务没有执行。', 19)
    save('core-request-flow.svg', '一次同步请求，从字节到数据再返回', '按网络、过滤器、MVC、业务、数据库五层跟踪请求，区分异常处理边界。', 710, body, '基线是同步 Spring MVC；不是所有后端都使用相同线程模型。')

    body = box(40, 120, 260, 160, 'Controller', ['输入 Request DTO', '校验、方法 / 状态 / 头', '输出 Response DTO'])
    body += box(350, 120, 260, 160, 'Service', ['可信身份与数据权限', '业务规则、事务编排', '不保存当前用户字段'], '#ecfdf5', '#6ee7b7')
    body += box(660, 120, 260, 160, 'Repository', ['参数化 SQL / JDBC', '存取与结果映射', '连接借还由框架协助'], '#f5f3ff', '#c4b5fd')
    body += arrow('300,205 340,205') + arrow('610,205 650,205')
    body += box(350, 390, 570, 125, '数据库是事实源，约束不是 UI 校验', ['主键、唯一约束、外键、事务与并发规则', '同一 Java 进程内三层是方法调用，不是三个微服务'], '#fff7ed', '#fdba74')
    body += arrow('790,280 790,380')
    body += text(40, 570, 'DTO / Entity / VO 是边界工具，不要机械增设无意义的层和 Impl。', 19)
    save('core-layer-map.svg', '三层分工：分离责任，不制造仪式', 'Controller负责HTTP，Service负责业务与事务，Repository负责SQL；只有到独立数据库才跨网络。', 610, body)

    body = box(40, 115, 260, 140, 'users', ['id · 主键', 'email · 唯一', 'password_hash · 不外露'])
    body += box(350, 115, 260, 190, 'tasks', ['id · 主键', 'owner_id → users.id', 'title / status / version', 'created_at / updated_at'], '#ecfdf5', '#6ee7b7')
    body += arrow('300,180 340,180', '1 : N', 305, 160)
    body += box(660, 115, 260, 140, 'tags', ['id · 主键', 'name · 标签名'])
    body += box(660, 365, 260, 115, 'task_tags', ['task_id + tag_id', '组合唯一，连接多对多'], '#f5f3ff', '#c4b5fd')
    body += arrow('540,305 540,420 650,420')
    body += arrow('790,255 790,355')
    body += box(40, 365, 460, 150, '联合索引 · 为查询维护有序目录', ['(owner_id, status, created_at, id)', '先找到用户和状态范围，再读取时间顺序', '索引会占空间并增加写入维护成本'], '#fff7ed', '#fdba74')
    save('core-data-model.svg', '从页面关系到表、约束和索引', '用户任务一对多，任务标签通过关联表多对多，联合索引对应实际列表查询。', 560, body, '这是扩展阶段模型；第一版 H2 只有任务小表。')

    body = box(40, 115, 420, 250, '本地数据库事务', ['BEGIN', '修改 tasks + 写入 task_audit', '成功 → COMMIT；失败 → ROLLBACK', '范围：同一事务资源，不是所有副作用'], '#ecfdf5', '#6ee7b7')
    body += box(520, 115, 400, 250, '并发控制 · 另一个问题', ['A / B 同时读 version=0', 'UPDATE ... WHERE version=0', '一方成功，另一方识别冲突', '事务 ≠ 自动防止所有覆盖'], '#eff6ff', '#93c5fd')
    body += box(40, 430, 880, 125, 'HTTP / 邮件 / MQ 不在这个普通本地事务里', ['数据库提交后，外部发送仍可能失败；外部成功后，数据库也可能失败。', '跨边界要设计 outbox、幂等、状态机、补偿和对账，而不是只贴注解。'], '#fff7ed', '#fdba74')
    body += arrow('250,365 250,420', dashed=True)
    save('core-transaction.svg', '三个问题：原子提交、并发冲突、重复执行', '本地事务保护数据库内原子提交，版本条件识别并发更新，跨边界副作用需要另外设计可靠性。', 600, body, '幂等防重复业务副作用；它也不是事务的同义词。')

    body = box(40, 140, 270, 145, '应用 · 先认证授权', ['key 包含用户 / 租户', '设置回源与等待上限'])
    body += box(610, 140, 310, 145, 'Redis 缓存', ['命中：返回允许展示的结果', '未命中 / 过期：有限回源'], '#f5f3ff', '#c4b5fd')
    body += arrow('310,185 600,185', '1 · GET', 415, 165)
    body += arrow('610,245 320,245', '命中则返回', 400, 268)
    body += box(40, 420, 410, 145, 'MySQL · 事实源', ['2 · miss 时读库，回填带 TTL 的缓存', '写操作：先数据库事务提交'], '#ecfdf5', '#6ee7b7')
    body += arrow('175,285 175,410', 'miss', 190, 350)
    body += arrow('450,460 765,460 765,295', '3 · 回填 / 提交后失效', 500, 440)
    body += text(40, 625, 'DB 提交与缓存失效不是一个原子操作；删失败、慢读回填都要有恢复策略。', 18)
    save('core-cache-flow.svg', '缓存是旁路，不是正确性的唯一来源', '应用授权后先读Redis，未命中读MySQL并回填，数据库提交后失效缓存但仍需处理失败与竞态。', 670, body)

    body = box(40, 120, 285, 145, '你的电脑 / 浏览器', ['127.0.0.1:8080', '只映射本机，避免公网暴露'])
    body += box(390, 120, 530, 145, 'api 容器 · task-api:8080', ['容器内部监听 0.0.0.0', 'mysql profile → jdbc:mysql://mysql:3306/tasks'])
    body += arrow('325,190 380,190')
    body += box(390, 370, 530, 145, 'mysql 容器 · 内部 3306', ['不发布宿主数据库端口', '命名卷 mysql-data → /var/lib/mysql'], '#ecfdf5', '#6ee7b7')
    body += arrow('650,265 650,360', 'Compose 网络 / 服务名', 670, 320)
    body += box(40, 370, 285, 145, '镜像 ≠ 数据', ['镜像：程序与用户态依赖', '卷：独立保存数据库文件', '备份：需要另存且测试恢复'], '#fff7ed', '#fdba74')
    body += text(40, 575, '容器内 localhost 指向自己；down 保留卷，down -v 会删卷，重启测试不等于备份。', 18)
    save('core-deploy-topology.svg', 'Compose 部署：地址、端口与数据卷', '宿主回环端口转发到API容器，API使用mysql服务名连接内部数据库，数据库文件存于命名卷。', 620, body)

    body = ''
    rows = [('没有 HTTP 响应', 'DNS → TCP → TLS', 'curl 分阶段；查解析、监听、路由与证书'),
            ('有 4xx 响应', '请求 / 身份 / 业务前置条件', '核对方法、Content-Type、认证与归属'),
            ('502 / 504', '代理到上游的连接 / 响应', '代理日志 + 应用日志，不能只猜进程死活'),
            ('500 / 慢请求', '业务 / SQL / 资源 / 下游', 'requestId → 根因堆栈 → 分段耗时证据')]
    for i, (h, kind, action) in enumerate(rows):
        y = 115+i*125
        body += box(40, y, 290, 100, h, [kind])
        body += arrow(f'330,{y+50} 380,{y+50}')
        body += box(390, y, 530, 100, '下一步：收集证据', [action], '#ecfdf5', '#6ee7b7')
    body += text(40, 665, '先记录范围与证据，再做低风险修复；日志不记录密码、token、完整请求体。', 18)
    save('core-troubleshooting.svg', '第一次排错：先分层，不先重启', '按是否有HTTP响应、4xx、代理错误、应用错误分层收集证据，关联请求ID与根因。', 710, body)
    print('Generated 10 core SVG diagrams')


if __name__ == '__main__':
    generate()

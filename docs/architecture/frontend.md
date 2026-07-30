# 前端架构

## 技术与职责

前端使用 Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios 和 ECharts。同一个 SPA 同时承载学习者页面与 `/admin` 管理页面。

```text
frontend/src/
├── api/          # 按业务域封装请求和 TypeScript 契约
├── assets/       # 全局样式与静态资源
├── components/   # 可复用组件与布局
├── router/       # 路由、登录和角色守卫
├── stores/       # 用户等跨页面状态
├── types/        # 共享类型
├── utils/        # 请求、格式化和领域辅助函数
└── views/        # 用户端与管理端页面
```

目录清单只描述稳定边界，不枚举每个页面文件；真实文件以源码为准。

## 页面分层

- `views/` 负责页面级数据编排、路由参数和业务状态。
- `components/` 负责可复用交互和展示，不直接复制页面级请求逻辑。
- `api/` 统一方法、路径和参数，页面不得散落 Axios URL。
- `stores/` 只保存跨页面状态；局部表单和弹窗状态留在页面或组合函数中。
- 大页面优先拆出领域组件、组合函数和纯展示映射，不建立无意义的包装层。

## 请求链路

```mermaid
sequenceDiagram
    participant V as View
    participant A as API module
    participant X as Axios/fetch
    participant B as Backend
    V->>A: typed request
    A->>X: method/path/params
    X->>B: JWT request
    B-->>X: R<T> or SSE
    X-->>A: normalized result
    A-->>V: typed data/error
```

普通接口经过统一 Axios 层处理 Token、业务错误和未登录跳转。AI 流式接口使用带 JWT 的 `fetch` 与 `ReadableStream`，单独处理取消、错误和完成事件。

## 路由与权限

- 路由守卫根据登录状态和用户角色控制导航。
- 管理页面隐藏只是体验优化，真正权限由后端 `/api/admin/**` 校验。
- 页面不得通过修改 Pinia 或 localStorage 获得管理权限。
- 登录失效由统一请求层清理本地状态并引导重新认证。

## 内容安全

- AI 和 Markdown 内容先解析，再使用 DOMPurify 净化。
- 不直接使用未经净化的 `v-html`。
- 题目详情、考试作答页在提交前不展示正确答案。
- 外部链接、代码块、Mermaid 和可视化组件必须限制可执行内容。

## 质量边界

- API 模块使用契约测试验证方法、路径和参数。
- 组合函数和纯工具使用 Vitest。
- 关键页面状态使用组件或页面测试。
- 登录、练习、考试、投稿审核等真实闭环使用隔离 Docker Playwright E2E。
- Element Plus 和 ECharts 按需加载；大依赖继续按页面边界拆包。

前端视觉和交互规则见项目 `frontend-design` Skill，测试分层见[测试策略](../development/testing.md)。

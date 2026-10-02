# ADR-0010：Token 化 Quiet Digital Textbook 与 Vue/CSS 动效基线

- 状态：Accepted
- 记录日期：2026-10-02
- 类型：前端架构

## 背景

学习端和管理端需要共享清晰、低干扰的界面基础，同时让加载、错误、披露和列表变化可理解。
过去若由页面自行决定字号、间距、圆角、阴影、焦点与过渡，Element Plus 和自定义组件会逐渐产生不一致的控制密度与状态表达。

数字、进度和奖励还需要在真实服务端数据更新后提供适度连续性，但不能通过前端动效推断掌握程度、判分或奖励。

## 决策

采用 Quiet Digital Textbook 作为共享工作区基线：暖纸表面、低饱和蓝的主操作与键盘焦点、克制的语义状态色。
`tokens.css` 统一管理字号、间距、圆角、阴影、控制高度、焦点、表面状态、骨架色与动效节奏；`element-plus.css` 负责将同一语义映射到 Element Plus。页面和领域组件不另建并行视觉变量。

`components/ui/` 提供可复用原语。容器、列表交互与状态面板分别承接语义分组、真实 button/RouterLink 行为、以及 mutually exclusive 的 loading、empty、error 与 ready 内容。加载失败不得伪装成零数据；错误在原位提供恢复路径。

共享动效使用 Vue `<Transition>`、`<TransitionGroup>` 与 token 化 CSS：内容只作短暂进入，披露面板使用条件过渡，键控列表使用 FLIP move 类。过渡只声明需要的 `opacity` 与 `transform` 属性。`useAnimatedNumber` 使用可取消 `requestAnimationFrame`：初值直接显示，后续有限数值更新才短暂过渡；组件卸载时取消，reduced-motion 改变时取消并显示目标值。

全局 `prefers-reduced-motion` 规则把 CSS 过渡降为即时（`0.01ms`）；共享 `motion.css` 类同时移除自身位移，不推断所有组件 transform 都被重置。JavaScript 数字过渡通过 `useReducedMotion` 遵从同一偏好。动效不延迟操作、焦点管理、验证或考试作答。

## 备选方案

Motion for Vue 的 `MotionConfig` 与 `LazyMotion` 提供全局配置和按需特性加载，但当前没有复杂时间线、手势或物理运动需求，故不引入新依赖。此处未作包体测量或性能基准；后续如出现具体需求，须补兼容性、生命周期、reduced-motion 与实际包体证据。

原生 `Document.startViewTransition()` 作为后续候选，尚未接入路由或状态切换；采用前须确认浏览器覆盖、导航生命周期和无障碍行为。

## 影响与不变量

- 学习数据、判分、经验、等级与成就仍由真实后端事件和响应提供；动效只表达已返回的事实。
- 限时考试提交前保持静默，不因共享动效泄露正确性、奖励、连击或庆祝。
- 经验、等级与成就在顶栏、个人页与总结中原位低调展示；不以奖励飘字、全屏庆祝、彩纸、连击火焰、成就弹窗或常驻 pulse 表达。
- 新页面先复用 tokens、Element Plus 映射和 UI 原语；重复需求才扩展共享层。
- 状态语义、焦点和键盘交互优先于视觉连续性；reduced-motion 下保留结果与操作。

## 参考

- [Vue Transition](https://vuejs.org/guide/built-ins/transition)
- [Vue TransitionGroup](https://vuejs.org/guide/built-ins/transition-group)
- [MotionConfig for Vue](https://motion.dev/docs/vue-motion-config)
- [LazyMotion for Vue](https://motion.dev/docs/vue-lazymotion)
- [MDN: Document.startViewTransition()](https://developer.mozilla.org/en-US/docs/Web/API/Document/startViewTransition)

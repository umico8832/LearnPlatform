---
name: borrow-first
description: 借鉴优先 (borrow first). Use before building or substantially reworking anything non-trivial in LearnPlatform — a page, component, animation, product flow, algorithm, backend pattern, learning content, or test setup — to find excellent existing implementations, borrow them, and adapt them into the project instead of reinventing the wheel. Skip for trivial edits and fixes with an obvious solution.
---

# 借鉴优先

很多开发是在重复造轮子。本项目的做法是：动手前先找别人已经做好的优秀实现，拿来后改进成我们自己的；从零编写是最后手段。
本项目为个人使用，不讨论版权、许可证或商用授权；借鉴时只关心效果、质量和维护成本。

## 流程

1. **定范围和“够用”标准**：一句话写清要做的东西和约束（Vue 3、设计基线、桌面端等），再写下候选必须满足的条件。按影响决定调研深浅：小事凭经验直接借；中等任务调研一轮；影响架构或主线体验的大事可以多轮深入，不限时间。
2. **找**：先查项目内部的同类实现、已有依赖和测试做法，能直接复用就不再向外搜；已经调研过或方案已定的，直接沿用结论，不重复调研。重做中的页面从零设计（见 [frontend-design](../frontend-design/SKILL.md#two-baselines-during-the-learner-redesign)），旧页面、Element Plus 组件和旧样式不算可复用的内部实现。外部每类只去最好的一两处，不铺开：代码首选 GitHub，界面看真实产品，讲法以大纲和主流教材为准，用户的真实想法看社区讨论；社区帖子不作为对错依据。整理出 2–4 个候选，附链接或源码路径、维护状态、与本项目的契合度和改造成本。调研在后台进行，不阻塞其他工作。先读文档和介绍筛选，确认契合且实现细节会影响选择时，才深入源码或做小范围试验；星数、下载量只作线索，不代替契合度和质量判断。
   **何时停**：找到满足标准的候选就停，不为追求完美继续搜；一轮下来没有新的有效候选时，改为只学思路或自研。
3. **选与改**：先定结果——直接使用、薄封装、只学思路或自研；改造成本明显高于重写时只学思路。外观统一换成本项目的设计参数与动效预设（见 [frontend-design](../frontend-design/SKILL.md) 与 [interaction-motion](../interaction-motion/SKILL.md)），不直接沿用别处的样式值；讲解内容按[学习内容讲解口径](../../../docs/product/explanation-conventions.md)统一位序、术语和伪代码表示。小段代码直接复制改写，不为它新增依赖；必须引入的依赖选有人维护的项目，并核对包体。
4. **验证**：按[测试策略](../../../docs/development/testing.md)的风险分层选择验证范围。涉及界面时，按影响做浏览器验收，重点看键盘操作和加载、空、错误状态；讲解内容能模拟的用模拟器自检，并按[教学内容审核口径](../../../docs/product/explanation-conventions.md#教学内容审核)完成独立 AI 审核。
5. **记录**：在提交正文中加一条“- 借鉴：来源 → 我们改了什么”；影响架构的取舍写入 ADR，附关键的淘汰理由。

# 图算法轨迹

`frontend/src/viz/algorithms/graph/` 提供与 Vue 无关的 BFS、DFS、Dijkstra、Prim、Kruskal、拓扑排序。
它是算法数据模块，没有注册到 `scenes/`，未接入页面；正式舞台仍为链表、出栈序列、二叉树三类。

## 入口与输入

直接调用 `bfs(input)` 等具名函数，或 `buildGraphTrace(algorithm, input)`。
`algorithm` 为 `bfs | dfs | dijkstra | prim | kruskal | topological`。

```json
{
  "directed": true,
  "nodes": ["S", "A", "T"],
  "edges": [
    {"id": "sa", "from": "S", "to": "A", "weight": 2},
    {"id": "at", "from": "A", "to": "T", "weight": 3}
  ],
  "start": "S"
}
```

- 1–64个顶点、至多512条边；顶点和边分别使用唯一标识，标识为1–40个字母、数字、下划线或连字符。
- 边权须为安全整数；累计距离或权值溢出则抛出 `RangeError`。零权、平行边和自环允许，平行边的ID须不同。
- BFS、DFS、Dijkstra需要有效 `start`。Prim可选起点；Kruskal和拓扑排序不使用起点。
- Prim/Kruskal只接受无向图，可有负权。Dijkstra拒绝图中任何负权边，包括不可达分量的负权边。
  拓扑排序只接受有向图。无向边只输入一次，由邻接函数按两端展开。
- 输入排列不决定顺序：顶点按标识字典序，邻点先按顶点再按边ID；Prim/Kruskal按权值再按边ID。
  Dijkstra同距按顶点标识，等长路径不替换已有前驱；拓扑每次选当前零入度集合中的最小标识。
- 可显式给 `neighborOrder`、`zeroIndegreeOrder` 为 `ascending-node-id`，`tieBreak` 为 `weight-then-edge-id`。
  不支持的顺序抛错，不静默换规则。Dijkstra的可选 `target` 不提前截断全图距离计算。

## 输出格式

`GraphTrace` 包含 `algorithm`、规范化的 `input`、`steps` 和带 `kind` 的 `result`。
每个步骤保存独立的完整状态快照，输出在类型与运行时均递归只读；原输入不会被冻结或改写。
快照不依赖前一步逆操作，因此未来舞台可以直接跳转或倒退。

| 字段 | 语义 |
| --- | --- |
| `id` | 算法名和步骤序号，规范化输入相同则稳定；跨输入不承诺同一序号表达同一事件 |
| `title` / `note` / `phase` | 事件标题、解释与算法阶段 |
| `vars` | 沿用引擎 `VarView`，包含候选集合与输出的文本投影 |
| `predict` | 沿用引擎 `Predict` 的ID、提示、选项、反馈与takeaway，在该步揭示前提问 |
| `state` | 算法当前状态；具体字段只在相应算法中出现 |

`GraphStep` 复用现有 `Step` 的讲解、变量与预测约定，但不冒充引擎 `Trace`：
没有 `frame`、布局坐标、代码行映射或占位舞台。后续舞台须把结构快照转换成真实 `Frame` 后再接播放器。

| 算法 | 主要状态 | 结果 |
| --- | --- | --- |
| BFS | `frontier`队列，`discovered`入队标记，`order`出队次序，`parents`前驱 | `kind=traversal`、访问序列和前驱；只访问起点可达分量 |
| DFS | `stack/frontier`递归栈，`discovered`持久标记，`order`首次进入次序，`parents` | 同上；回溯保留访问标记，不在其他分量另启搜索 |
| Dijkstra | `distances`暂定值，`order`已确定集合，`parentEdges/parents`路径，`frontier`可达未确定候选 | `kind=shortest-path`；不可达距离为JSON可序列化的`null` |
| Prim | `discovered/order`入树次序，`frontier`跨割边ID，`selectedEdges`，权和与分量数 | `kind=spanning-forest`、边ID、权和、分量数、`connected` |
| Kruskal | `frontier`未处理边ID，`representatives`并查集代表，已选边、权和与分量数 | 同上；不连通时输出最小生成森林，不称全图生成树 |
| 拓扑 | `indegree`当前入度，`frontier`零入度集合，`order`已输出前缀 | `kind=topological`、`acyclic`与`remaining`；剩余点还可能含环的后继，不等同于环上顶点集合 |

`activeNode/activeEdge` 表示当前处理对象。Prim优先从指定起点开始，再按字典序启动未覆盖分量；
单顶点无边的森林连通且权和为0。检查点ID由算法和节点/边标识构成，在一条轨迹内唯一。

## 验证与来源

单测用 Floyd–Warshall 对拍距离，用显式栈搜索对拍递归DFS，用边子集穷举对拍最小生成森林，
用全排列对照拓扑结果；另验证并列顺序、快照隔离、零权/负权边界、断连、平行边、自环与整数溢出。
内容候选直接使用相同输入构建轨迹，不因此获得正式场景或内容审核状态。

实现沿用既有链表、栈、二叉树模拟器“确定执行、逐步快照、预测点”的约定。
算法规则参考作者维护的Princeton源码：
[BFS](https://github.com/kevin-wayne/algs4/blob/master/src/main/java/edu/princeton/cs/algs4/BreadthFirstPaths.java)、
[DFS](https://github.com/kevin-wayne/algs4/blob/master/src/main/java/edu/princeton/cs/algs4/DepthFirstSearch.java)、
[Dijkstra](https://github.com/kevin-wayne/algs4/blob/master/src/main/java/edu/princeton/cs/algs4/DijkstraSP.java)、
[Prim](https://github.com/kevin-wayne/algs4/blob/master/src/main/java/edu/princeton/cs/algs4/PrimMST.java)、
[Kruskal](https://github.com/kevin-wayne/algs4/blob/master/src/main/java/edu/princeton/cs/algs4/KruskalMST.java)、
[拓扑](https://github.com/kevin-wayne/algs4/blob/master/src/main/java/edu/princeton/cs/algs4/TopologicalX.java)。
教学用小图采用直接扫描候选，拓扑采用零入度法；与参考的堆优化或FIFO队列破并列实现不同，输出规则在本页明确。

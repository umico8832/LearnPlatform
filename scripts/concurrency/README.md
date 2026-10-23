# 本机隔离并发实验

工具只用于 `learnplatform-e2e`，沿用 C/D 的 k6、真实认证、MySQL/Redis 和固定延迟 mock 上游。
不连接服务器，不读取本地 `.env`，不写 Flyway 种子或正式题目内容。

先从主检出的 `tmp/p28/e2e.lock` 取得独占锁并写 owner `codex G ...`。Python 工具会检查锁归属，HTTP仅允许18081–18084回环端口并核对容器项目/运行状态；造数及SQL还核对数据库的Compose project标签。需要本机 k6（C/D 使用 v2.3.0）、Docker、JDK 21 和 Node；不在压测期间运行 Maven 或其他负载。

```bash
mkdir /path/to/main/tmp/p28/e2e.lock
printf '%s\n' 'codex G reproduce' > /path/to/main/tmp/p28/e2e.lock/owner
export LOADTEST_OUTPUT=/absolute/path/to/evidence
export K6_BIN=/absolute/path/to/k6
python3 scripts/concurrency/environment.py --output "$LOADTEST_OUTPUT"
python3 scripts/concurrency/run_suite.py
python3 scripts/concurrency/seed.py
python3 scripts/concurrency/scale_suite.py
```

普通接口：4 VU预热10秒，10/50 VU各30秒；追问：8/64 VU每人3次。mock 每调用等待1秒再分两段发送、各间隔0.1秒；追问生成和判断仍经真实业务链。`true_failures` 必须为0，AI明确1007容量拒绝单列比例和延迟；每日额度、其他业务错误、未知/残缺SSE和传输错误仍失败。

造数新增200课程、3万题、1000用户、30万作答、10万卡与各30万复习/学习/奖励事件。32个活跃用户占80%作答，12门热门课程占80%题目；其余为长尾。64个测试用户可登录，其余禁用。所有内容均标记隔离夹具，不是课程内容质量证据。固定日期2026-10-23、确定性整数分布便于重放；奖励包含首次及同日重复审计，FSRS卡采用有效的合成REVIEW状态、历史为合成数据，不能据此推导学习效果。

做纯读前后对比时固定数据库，`scale_suite.py practice_stats questions` 可只跑目标路径。`environment.py --keep-data` 仅重建应用并保留独占E2E卷，供同数据对比；正常首次启动会重置隔离卷。

```bash
python3 scripts/concurrency/multi_instance.py --output "$LOADTEST_OUTPUT" --lease 3s --quota 3
export LOADTEST_BASE=http://127.0.0.1:18083
export LOADTEST_METRICS=http://127.0.0.1:18081,http://127.0.0.1:18082
python3 scripts/concurrency/probe_consistency.py --output "$LOADTEST_OUTPUT/consistency.json"
python3 scripts/concurrency/probe_admission.py
python3 scripts/concurrency/multi_instance.py --output "$LOADTEST_OUTPUT" --lease 30s --quota 3
python3 scripts/concurrency/scale_suite.py
python3 scripts/concurrency/run_suite.py --ask-only
```

双实例工具复用当前E2E后端镜像，新增18082直连、18083 Nginx、18084 mock统计端口（仅回环）。3秒租约仅加速故障实验，应用默认30秒；`probe_admission.py` 会 SIGKILL 本轮第一个后端并在finally恢复。真实上游并发来自 mock 的加锁逐次计数，应用在途取双实例 Prometheus 采样之和，二者分别保留证据。取消探针把既有DELETE停止请求发到另一实例，等待实际工作退出；只关闭TCP连接不能作为业务停止确认。k6按Nginx X-Upstream分别计数，并要求两台均有命中、未知上游为0。认证仅在内存，结果只保存聚合与无身份采样。

完成后先移除本轮额外容器，再使用统一E2E清理入口；最后只移除自己持有的锁及owner：

```bash
python3 scripts/concurrency/multi_instance.py --output "$LOADTEST_OUTPUT" --down
COMPOSE_DISABLE_ENV_FILE=1 DB_PASSWORD=root123 JWT_SECRET=ci-e2e-jwt-secret-key-for-testing-only \
  python3 scripts/docker-lifecycle.py e2e-clean
# 核对 learnplatform-e2e 容器/卷为空后删除自己锁内的 owner，再 rmdir 锁
```

以上凭据为仓库已有明确的隔离测试夹具。清理不会操作日常开发卷。实验报告及原始结果保留在主检出 `tmp/p28/g-concurrency2.md`、`tmp/p28/g-loadtest/`，压测数值不作为生产承诺。

# 本地开发

## 环境要求

- JDK 17+（`pom.xml` 的编译基线为 Java 17）
- Maven 3.8+
- Node.js 20+
- MySQL 8.0+

## 1. 配置环境变量

```bash
cp .env.example .env
```

变量含义见[配置说明](configuration.md)。编辑 `.env` 后，从仓库根目录使用项目脚本加载。不要直接 `source .env`，数据库 URL 中的 `&` 可能被 shell 当作控制符。

```bash
source scripts/load-env.sh .env
```

不得读取、输出或提交真实密钥。

## 2. 启动 MySQL

使用本机 MySQL 8.0。首次初始化由 Flyway 在后端启动时按迁移脚本完成；不要手工维护独立于 Flyway 的数据库结构。

## 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认地址：

- API：`http://localhost:8080`
- Knife4j：`http://localhost:8080/doc.html`

## 4. 启动前端

```bash
cd frontend
npm ci
npm run dev
```

前端默认地址：`http://localhost:5173`。

## 5. 基础验证

```bash
cd backend
mvn test

cd ../frontend
npm test
npm run build
```

完整质量门禁和 E2E 方式见[测试策略](../development/testing.md)，启动异常见[常见问题排查](troubleshooting.md)。

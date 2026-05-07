# 本地开发

本文面向 Windows + Docker Desktop + IDEA 的本地开发方式。推荐只用 Docker Compose 启动 Postgres 和 Redis，后端由 IDEA 运行，前端用 Vite 运行。

## 启动步骤

1. 启动 Docker Desktop。

2. 在仓库根目录启动依赖：

```powershell
docker compose up -d postgres redis
```

当前端口映射：

- Postgres 容器 `5432` 映射到宿主机 `localhost:5433`
- Redis 容器 `6379` 映射到宿主机 `localhost:16379`

3. 在 IDEA 配置并运行后端主类：

主类：`com.travel.ai.TravelAiApplication`

建议环境变量：

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/ragent
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=16379
APP_JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
SPRING_AI_DASHSCOPE_API_KEY=<your-key>
```

Windows / IDEA 环境变量分隔符用分号 `;`，不要用 Linux/macOS 常见的空格换行写法。IDEA 的 Run Configuration 里可以逐项填写，也可以在 Environment variables 中使用 `KEY=value;KEY2=value2`。

4. 检查后端：

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
```

5. 启动前端：

```powershell
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173`。前端 `/api` 会代理到 `http://127.0.0.1:8081`。

## Redis 端口说明

Windows 上 `6379` 有时会落在系统排除端口范围内，Docker 绑定 `6379:6379` 可能失败。当前 compose 使用：

```yaml
ports:
  - "16379:6379"
```

因此 IDEA 本地运行 Spring Boot 时，Redis 需要配置为：

```text
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=16379
```

Docker 内部服务访问 Redis 时仍使用 `redis:6379`。

## 常见错误

### 连接 `127.0.0.1:5432` refused

原因通常是本地后端仍在连接默认 Postgres 端口 `5432`，但 compose 暴露的是 `5433`。

处理：

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/ragent
```

同时确认依赖容器已启动：

```powershell
docker compose ps postgres
```

### `RedisConnectionFailureException`

常见原因：

- IDEA 运行时仍连接 `localhost:6379`。
- Redis 容器没有启动。
- Windows 排除端口导致旧的 `6379:6379` 映射失败。

处理：

```text
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=16379
```

并检查容器：

```powershell
docker compose ps redis
```

### 环境变量分隔符错误

Windows / IDEA 中多个环境变量通常用分号 `;` 分隔：

```text
SPRING_DATA_REDIS_PORT=16379;SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/ragent
```

不要写成 Linux shell 的形式：

```bash
SPRING_DATA_REDIS_PORT=16379 SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/ragent
```

如果变量较多，优先在 IDEA Run Configuration 的 Environment variables 弹窗里逐项添加，减少分隔符错误。

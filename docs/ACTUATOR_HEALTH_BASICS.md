# Actuator 健康检查底层理解（含本项目配置）

更新时间：2026-04-02

## 0. 这份文档解决什么问题

- 你已经知道“Actuator health 用来做探活”，但不清楚它内部是怎么工作的。
- 你想从底层基础理解：`health` 的 `UP/DOWN` 怎么来的、`show-details: when_authorized` 具体怎么受鉴权影响、以及什么时候/如何自定义健康检查（`HealthIndicator`）。

本文尽量用“机制 + 配置解读 + 落地建议”的方式解释，并结合本项目当前配置。

## 1. Actuator 是什么（定位）

Actuator 是 Spring Boot 的“运行期管理端点（management endpoints）”能力。它不像业务接口那样回答“我怎么做业务”，而是回答“服务目前处在什么运行状态”。

常见端点：

- `GET /actuator/health`：健康状况聚合（UP/DOWN）
- `GET /actuator/info`：版本/自定义信息（如果你配置了）
- `GET /actuator/metrics`：指标（通常需要额外暴露与鉴权）

## 2. 你的项目里 Actuator 做了什么

### 2.1 暴露哪些端点（Actuator 层）

`src/main/resources/application.yml`：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

含义：只把 `health` 和 `info` 映射成 HTTP 端点；其它端点即使存在能力也不对外。

### 2.2 健康详情是否展开（健康端点层）

同文件：

```yaml
management:
  endpoint:
    health:
      show-details: when_authorized
      show-components: when_authorized
```

含义（概念层）：

- 匿名请求：通常只看到总体聚合状态（例如 `{"status":"UP"}`），不展开每个依赖的明细。
- 已鉴权请求：允许展开组件级信息（例如 DB/Redis 各自是否 UP，失败原因等）。

### 2.3 安全白名单（Spring Security 层）

`src/main/java/com/powernode/springmvc/security/SecurityConfig.java`：

```java
authorizeHttpRequests(auth -> auth
  .requestMatchers("/auth/login", "/actuator/health/**", "/actuator/info").permitAll()
  .requestMatchers("/travel/**", "/knowledge/**").authenticated()
  .anyRequest().permitAll()
)
```

关键点：

- 必须用 `/actuator/health/**`，否则只匹配根路径时，`/actuator/health/liveness`、`/actuator/health/readiness` 这类子路径可能会被拦。
- `permitAll()` 让探活不需要 JWT，便于被负载均衡/K8s/dockers 直接探测。

## 3. A 方向：`health` 的 `UP/DOWN` 是怎么判断出来的

### 3.1 底层机制：由“健康指示器（HealthIndicator/Contributor）”聚合

`/actuator/health` 的输出不是你手写的固定字符串，而是由一组“健康指示器”计算后汇总得到的。

它们的来源通常有两类：

- 自动配置（来自你引入的依赖和 Bean）：例如 JDBC/Redis 会注册对应的健康指示器。
- 你自己写的自定义 HealthIndicator：用代码定义“我关心的依赖是否可用”。

每个指示器会返回一个 `Health` 对象（至少包含状态，如 UP/DOWN，可能包含 details）。

最终聚合逻辑会把多个组件的结果合并成总体状态。

### 3.2 “UP”通常代表什么（更贴近实践）

`UP` 常见含义是：

- 所有被判定为“关键”的 HealthIndicator 都处于可用状态；或
- 没有足以把总体变为 DOWN 的失败条件。

但注意：这取决于你的健康指示器实现以及框架默认规则。

### 3.3 为什么你不写自定义 Indicator 也能看到 DB/Redis？

因为你引入了 `spring-boot-starter-data-redis`、JDBC/数据源相关依赖后，Spring Boot 会自动装配对应的 HealthIndicator。

也就是说，“health 端点能反映 DB/Redis 状态”往往来自自动配置，而不是来自你的业务代码。

### 3.4 怎么读 `health` 输出（建议姿势）

当你访问：

- 匿名：更多是聚合状态，适合用于探活
- 鉴权后：你更容易看到哪一个依赖失败（例如数据库连接失败、Redis 超时等）

解读要点：

- 如果总体 DOWN：通常是至少一个关键组件失败。
- 如果总体 UP 但业务出错：可能是你没有把某个“业务关键依赖”加入健康指标（即 health 关注点不等于业务关注点）。

## 4. B 方向：`show-details: when_authorized` 到底是什么意思

### 4.1 它和 Spring Security 的关系

`when_authorized` 的核心思想：**只有当前请求通过了鉴权，才允许展开细节**。

这意味着它不是“匿名一定会被拒绝”，而是“匿名看到的粒度更粗”。

### 4.2 为什么你会感觉“只看见 status”

因为匿名请求通常：

- `show-details` 不展开（不显示组件级信息、错误原因细节等）
- 你只能看到总体状态，足够用于监控/探活

### 4.3 信息泄露控制（为什么要这么做）

健康详情里可能包含：

- 依赖地址线索、异常栈信息、超时原因

暴露这些信息会增加攻击面，所以通过 `when_authorized` 把细节收敛到“需要知道的人（带 token 的请求）”，是工程上常见的安全做法。

## 5. C 方向：自定义 HealthIndicator 怎么写（落地）

### 5.1 什么时候需要自定义

当你想把“业务关键依赖”加入 health 聚合时，通常需要自定义。

常见例子（结合本项目可能会关心）：

- 向量库/pgvector 的“可写入/可查询能力”是否正常（不仅仅是数据库连上）
- 外部 LLM（DashScope）在一定超时阈值内是否可调用
- 天气服务是否可用（或者你选择：天气不可用不影响总体健康）

### 5.2 最基本写法（HealthIndicator）

模板思路：

- 实现 `HealthIndicator`
- 在 `health()` 里做快速探测（要么连通性探测，要么轻量查询）
- 用 `Health.up()/down()` 或 `Health.Builder` 填 details

示例（伪代码风格，便于你按需改）：

```java
@Component
public class LlmHealthIndicator implements HealthIndicator {
  @Override
  public Health health() {
    try {
      // 1) 做一个非常轻量的探测（例如只校验可用性，不要做大请求）
      // 2) 设置短超时，避免 health 被拖慢
      return Health.up()
          .withDetail("llm", "ok")
          .build();
    } catch (Exception e) {
      return Health.down(e)
          .withDetail("llm", "unavailable")
          .build();
    }
  }
}
```

工程建议：

- `health()` 必须尽量快，避免 health 端点被拖慢造成“探活本身变慢”，甚至引发连锁问题。
- 如果某个依赖失败不应导致整体 DOWN，可以选择让它不返回 DOWN，或者将其作为非关键指标（需要你对聚合策略再做选择）。

### 5.3 自定义时的“超时策略”要特别注意

健康检查是“系统探活”，不是业务请求重试通道。

所以：

- 对外部调用必须强制短超时
- 避免 health 里做长链路/重试风暴

结合你项目里已有的经验（`TravelAgent.chat`、`WeatherTool` 的 timeout 与降级），自定义 health 指示器也应该遵循相似的超时与失败处理思想。

## 6. 和本项目最相关的建议清单

- 你现在只暴露 `health,info`，并配合 Security whitelist，让探活路径无需 JWT，这是正确的“降低攻击面”默认姿势。
- `show-details/show-components: when_authorized` 让匿名探活只看总体状态，安全性更好。
- 若你后续发现“health 显示 UP，但业务关键链路失败”，通常原因是：你没有把那条关键链路对应的探测加入 health（需要自定义 HealthIndicator 或增强已有指标）。

## 7. 你可以怎么验证是否符合预期（手工）

不依赖 K8s 的最简验证：

- 匿名：`GET http://localhost:8081/actuator/health`，只看是否能访问以及是否只显示聚合结果
- 鉴权：携带 JWT 访问同一地址，确认能否展开 components/details（视你过滤器对 actuator 的处理）
- 探活子路径：如果你在编排里会用 liveness/readiness，先确认已开启 `management.endpoint.health.probes.enabled`（否则这两个子路径可能返回 404）。开启后再检查这些子路径是否都被 `permitAll()` 正确覆盖（你已用 `/actuator/health/**`，通常可以避免误拦）

## 8. 结束语

Actuator 的“底层本质”可以总结为一句话：

> `/actuator/health` 不是业务逻辑，而是由框架聚合多个“健康指示器”的结果得到的总体状态。

理解这句话之后，A/B/C 三个方向就都能串起来：  
聚合来自哪些指示器（A）→ 详情是否展示受鉴权影响（B）→ 你如何让框架也知道你的业务关键依赖（C）。


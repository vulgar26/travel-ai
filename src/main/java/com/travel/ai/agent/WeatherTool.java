package com.travel.ai.agent;

import jakarta.annotation.PostConstruct;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.travel.ai.config.AppAgentProperties;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class WeatherTool {

    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);
    private final AppAgentProperties appAgentProperties;
    @Value("${weather.api-key}")
    private String apiKey;

    /**
     * 可选：真实天气接口的基础地址，比如 https://api.xxx.com/weather
     * 目前我们项目里如果没有配置，就走本地模拟数据。
     */
    @Value("${weather.api-url:}")
    private String apiUrl;

    /**
     * 兼容旧配置：当 {@code app.agent.tool-timeout} 无效时退回（见 {@link #effectiveTimeoutMs()}）。
     */
    @Value("${weather.timeout-ms:3000}")
    private long weatherTimeoutMs;

    public WeatherTool(AppAgentProperties appAgentProperties) {
        this.appAgentProperties = appAgentProperties;
    }

    /**
     * 带超时配置的 OkHttpClient。
     */
    private OkHttpClient client;

    /** {@link #init()} 解析后的毫秒超时，供日志与提示文案复用。 */
    private long httpTimeoutMs;

    @PostConstruct
    public void init() {
        long ms = effectiveTimeoutMs();
        this.httpTimeoutMs = ms;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
                .writeTimeout(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
                .callTimeout(ms, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();

        log.info("=== WeatherTool Bean已加载，超时时间={}ms（app.agent.tool-timeout），apiUrl={} ===", ms, apiUrl);
    }

    /**
     * 优先 {@code app.agent.tool-timeout}（非零）；否则退回 {@code weather.timeout-ms}。
     */
    private long effectiveTimeoutMs() {
        Duration d = appAgentProperties.getToolTimeout();
        if (d != null && !d.isZero() && !d.isNegative()) {
            return Math.max(1L, d.toMillis());
        }
        return Math.max(1L, weatherTimeoutMs);
    }

    @Tool(description = "获取指定城市的实时天气信息，包括温度、天气状况、湿度、风速等，用于出行规划参考")
    public String getWeather(String cityName) {
        log.info("=== 天气工具被调用了！城市：{} ===", cityName);

        try {
            return getWeatherStrict(cityName);
        } catch (java.net.SocketTimeoutException e) {
            log.warn("调用天气接口超时，城市={}，timeout={}ms", cityName, httpTimeoutMs);
            return String.format("%s实时天气查询超时（>%dms），请稍后再试。", cityName, httpTimeoutMs);
        } catch (Exception e) {
            log.error("调用天气接口异常，城市={}，error={}", cityName, e.toString());
            return String.format("%s实时天气暂时不可用，请稍后再试。", cityName);
        }
    }

    /**
     * 工具治理用“严格版本”：失败时抛出异常，由上层统一映射为 outcome/error_code。
     * <p>
     * 注意：该方法不作为 LLM Tool 暴露，避免模型直接依赖异常语义。
     */
    public String getWeatherStrict(String cityName) throws Exception {
        // 如果没有配置真实接口地址，继续走本地模拟数据（避免引入外部依赖）
        if (apiUrl == null || apiUrl.isBlank()) {
            return String.format(
                    "%s实时天气：晴，温度22℃，体感温度20℃，湿度45%%，东南风，风速12KM/H，适合出行",
                    cityName
            );
        }

        // 下面是一个带超时保护的 HTTP 调用示例：
        // 这里只是一个占位写法：真实项目中你可以根据实际天气 API 的路径和参数进行拼接
        String url = String.format("%s?city=%s&apiKey=%s", apiUrl, cityName, apiKey);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("调用天气接口失败，code={}，message={}", response.code(), response.message());
                throw new IllegalStateException("weather_api_failed: code=" + response.code());
            }

            String body = response.body().string();
            log.debug("天气接口原始返回: {}", body);

            // 这里不强行解析具体字段，直接返回一段简要描述，避免绑定到某个特定三方 API
            return String.format("%s实时天气数据已获取（来自外部接口），原始响应片段：%s",
                    cityName,
                    body.length() > 100 ? body.substring(0, 100) + "..." : body);
        }
    }
}

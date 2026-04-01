package com.powernode.springmvc.agent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WeatherTool {
    @Value("${weather.api-key}")
    private String apiKey;

    private final OkHttpClient client = new OkHttpClient();

    @PostConstruct
    public void init() {
        log.info("=== WeatherTool Bean已加载 ===");
    }

    @Tool(description = "获取指定城市的实时天气信息，包括温度、天气状况、湿度、风速等，用于出行规划参考")
    public String getWeather(String cityName) {
        // 模拟天气数据，验证Function Calling流程
        log.info("=== 天气工具被调用了！城市：{} ===", cityName);

        return String.format(
                "%s实时天气：晴，温度22℃，体感温度20℃，湿度45%%，东南风，风速12KM/H，适合出行",
                cityName
        );
    }
}

package com.tianji.aigc.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AiHttpClientConfig {

    @Bean
    public RestClientCustomizer aiRestClientCustomizer() {
        // ① JDK 自带 HttpClient:强制 HTTP/1.1 + 连接超时
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)      // ⭐ 绕开 HTTP/2 挂住
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        // ② Spring 的 JDK 请求工厂:设置读超时
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMinutes(5));      // ⭐ 关键:10s → 5 分钟

        return builder -> builder.requestFactory(factory);
    }
}

package org.cloud.gateway.config.cors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {
    private final CorsProperties corsProperties;

    // 构造器注入
    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(corsProperties.getAllowedOrigins());  // 允许所有域名跨域
        corsConfiguration.addAllowedMethod("*");  // 允许所有请求方法
        corsConfiguration.addAllowedHeader("*"); // 允许所有请求头
        corsConfiguration.setAllowCredentials(true);  // 允许携带 cookie 的请求
        corsConfiguration.setMaxAge(3600L);  // 预检请求的有效期为 60 分钟，单位：秒

        // 文件下载
        corsConfiguration.addExposedHeader("Content-Disposition");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);

        return new CorsWebFilter(source);
    }
}

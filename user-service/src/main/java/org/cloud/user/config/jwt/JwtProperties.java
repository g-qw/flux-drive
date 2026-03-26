package org.cloud.user.config.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 密钥（至少256位，即32字符）
     */
    private String secret;

    /**
     * 签发者
     */
    private String issuer;

    /**
     * 过期时间（单位：秒）, 默认 24 小时
     */
    private long expiration = 86400;

    /**
     * Token 自动刷新阈值（单位：秒），默认 5 分钟
     */
    private long refreshThreshold = 300;
}

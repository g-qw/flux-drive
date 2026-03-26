package org.cloud.user.util.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.cloud.user.config.jwt.JwtProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider {
    private final SecretKey secretKey;
    private final String issuer;
    private final long expiration;
    private final long refreshThreshold;

    public JwtProvider(JwtProperties jwtProperties) {
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.issuer = jwtProperties.getIssuer();
        this.expiration = jwtProperties.getExpiration() * 1000;
        this.refreshThreshold = jwtProperties.getRefreshThreshold();
    }

    /**
     * 生成 JWT Token
     *
     * @param subject    主题
     * @param claims     自定义声明数据
     * @return JWT Token 字符串
     */
    public String generateToken(String subject, Map<String, Object> claims) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .header()
                .type("JWT")
                .and()
                .issuer(issuer)
                .subject(subject)
                .audience().add("client").and()
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expirationDate)
                .notBefore(now)
                .claims(claims)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 验证 JWT Token
     *
     * @param token JWT字符串
     * @return claims
     */
    public Claims validate(String token) {
        return parseToken(token);
    }

    private Claims parseToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

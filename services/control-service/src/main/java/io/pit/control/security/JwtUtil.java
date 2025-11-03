package io.pit.control.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 提供JWT令牌的生成、验证和解析功能
 */
@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${pit.security.jwt.secret:pit-jwt-secret-key-change-in-production}")
    private String jwtSecret;

    @Value("${pit.security.jwt.expiration:86400000}") // 24小时
    private long jwtExpiration;

    /**
     * 生成JWT令牌
     */
    public String generateToken(String userId, String email, String orgId, String globalRole) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("orgId", orgId);
        claims.put("globalRole", globalRole);

        return createToken(claims, email);
    }

    /**
     * 从令牌中获取用户ID
     */
    public String getUserIdFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("userId", String.class));
    }

    /**
     * 从令牌中获取邮箱
     */
    public String getEmailFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * 从令牌中获取组织ID
     */
    public String getOrgIdFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("orgId", String.class));
    }

    /**
     * 从令牌中获取全局角色
     */
    public String getGlobalRoleFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("globalRole", String.class));
    }

    /**
     * 从令牌中获取过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * 验证令牌是否有效
     */
    public boolean validateToken(String token, String email) {
        try {
            final String tokenEmail = getEmailFromToken(token);
            return (email.equals(tokenEmail) && !isTokenExpired(token));
        } catch (Exception e) {
            logger.warn("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 验证令牌格式是否正确
     */
    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 刷新令牌
     */
    public String refreshToken(String token) {
        try {
            final Claims claims = getAllClaimsFromToken(token);
            return createToken(claims, claims.getSubject());
        } catch (Exception e) {
            logger.error("Failed to refresh token: {}", e.getMessage());
            return null;
        }
    }

    // 私有方法

    private <T> T getClaimFromToken(String token, ClaimsResolver<T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.resolve(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    private boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
            .setClaims(claims)
            .setSubject(subject)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(getSigningKey(), SignatureAlgorithm.HS512)
            .compact();
    }

    private SecretKey getSigningKey() {
        // 确保密钥长度足够
        if (jwtSecret.length() < 32) {
            jwtSecret = jwtSecret + "0".repeat(32 - jwtSecret.length());
        }
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    @FunctionalInterface
    private interface ClaimsResolver<T> {
        T resolve(Claims claims);
    }
}
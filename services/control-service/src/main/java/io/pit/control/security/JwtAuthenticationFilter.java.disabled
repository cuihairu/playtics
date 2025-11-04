package io.pit.control.security;

import io.pit.control.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * JWT认证过滤器
 * 处理Bearer Token认证
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {

        final String requestTokenHeader = request.getHeader("Authorization");

        String email = null;
        String jwtToken = null;

        // 检查Authorization头
        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
            jwtToken = requestTokenHeader.substring(7);
            try {
                email = jwtUtil.getEmailFromToken(jwtToken);
            } catch (Exception e) {
                logger.warn("Unable to get email from JWT Token: {}", e.getMessage());
            }
        }

        // 验证令牌并设置认证上下文
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            if (jwtUtil.isTokenValid(jwtToken)) {
                // 从令牌中提取用户信息
                String userId = jwtUtil.getUserIdFromToken(jwtToken);
                String orgId = jwtUtil.getOrgIdFromToken(jwtToken);
                String globalRole = jwtUtil.getGlobalRoleFromToken(jwtToken);

                // 创建权限列表
                List<SimpleGrantedAuthority> authorities = Arrays.asList(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_" + globalRole)
                );

                // 创建认证对象
                UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(email, null, authorities);

                // 设置附加信息
                PitAuthenticationDetails details = new PitAuthenticationDetails();
                details.userId = userId;
                details.email = email;
                details.orgId = orgId;
                details.globalRole = globalRole;
                details.jwtToken = jwtToken;

                authToken.setDetails(details);

                // 设置认证上下文
                SecurityContextHolder.getContext().setAuthentication(authToken);

                logger.debug("JWT authentication successful for user: {}", email);
            } else {
                logger.warn("JWT Token validation failed for user: {}", email);
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // 跳过不需要认证的路径
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/") ||
               path.startsWith("/api/public/") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/swagger-") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/") ||
               path.startsWith("/static/");
    }

    /**
     * 自定义认证详情类
     */
    public static class PitAuthenticationDetails {
        public String userId;
        public String email;
        public String orgId;
        public String globalRole;
        public String jwtToken;

        public boolean isSuperAdmin() {
            return "SUPER_ADMIN".equals(globalRole);
        }

        public boolean isOrgAdmin() {
            return "ORG_ADMIN".equals(globalRole) || isSuperAdmin();
        }
    }
}
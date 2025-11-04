package io.pit.control.config;

import io.pit.control.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Spring Security配置
 * 配置JWT认证和权限控制
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF（使用JWT不需要）
            .csrf(AbstractHttpConfigurer::disable)

            // CORS配置
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 会话管理：无状态
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 权限配置
            .authorizeHttpRequests(authz -> authz
                // 公开访问的端点
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                .requestMatchers("/").permitAll()
                .requestMatchers("/static/**").permitAll()

                // 管理员专用端点
                .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")

                // 组织管理端点
                .requestMatchers(HttpMethod.POST, "/api/organizations").hasAnyRole("SUPER_ADMIN", "ORG_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/organizations/**").hasAnyRole("SUPER_ADMIN", "ORG_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/organizations/**").hasRole("SUPER_ADMIN")

                // 游戏管理端点
                .requestMatchers(HttpMethod.POST, "/api/games").hasAnyRole("SUPER_ADMIN", "ORG_ADMIN", "GAME_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/games/**").hasAnyRole("SUPER_ADMIN", "ORG_ADMIN", "GAME_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/games/**").hasAnyRole("SUPER_ADMIN", "ORG_ADMIN")

                // 用户管理端点
                .requestMatchers(HttpMethod.POST, "/api/users").hasAnyRole("SUPER_ADMIN", "ORG_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/users/**").hasAnyRole("SUPER_ADMIN", "ORG_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasAnyRole("SUPER_ADMIN", "ORG_ADMIN")

                // 其他所有端点需要认证
                .anyRequest().authenticated()
            )

            // 添加JWT过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 允许的源
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));

        // 允许的HTTP方法
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        // 允许的头部
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-Requested-With", "Accept",
            "Origin", "Cache-Control", "X-File-Name", "X-Admin-Token"
        ));

        // 暴露的头部
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization", "X-Total-Count", "X-Page-Count"
        ));

        // 允许凭证
        configuration.setAllowCredentials(true);

        // 预检请求缓存时间
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
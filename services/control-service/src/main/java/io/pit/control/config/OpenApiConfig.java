package io.pit.control.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

/**
 * OpenAPI/Swagger 配置
 * 提供完整的API文档和交互界面
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:Pit Control Service}")
    private String applicationName;

    @Value("${spring.application.version:1.0.0}")
    private String applicationVersion;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(apiInfo())
            .servers(Arrays.asList(
                new Server().url("http://localhost:8080" + contextPath).description("Development Server"),
                new Server().url("https://api.pit.example.com" + contextPath).description("Production Server")
            ))
            .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
            .components(new Components()
                .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .in(SecurityScheme.In.HEADER)
                    .name("Authorization")
                    .description("JWT authorization header using the Bearer scheme. Example: \"Authorization: Bearer {token}\"")
                )
            );
    }

    private Info apiInfo() {
        return new Info()
            .title("Pit Gaming Analytics Platform API")
            .description("""
                # Pit Gaming Analytics Platform Control Service API

                Professional gaming analytics platform supporting multi-tenant architecture,
                comprehensive user management, and enterprise-grade security.

                ## Features

                ### 🏢 Multi-Tenant Architecture
                - **Organization Management**: Enterprise-grade organization management with tier-based quotas
                - **Game Management**: Complete game lifecycle management with multi-platform support
                - **Environment Management**: Dev/Test/Prod environment isolation with configuration management

                ### 👥 User Management & Security
                - **JWT Authentication**: Stateless authentication with refresh tokens
                - **RBAC Permissions**: 8 role types with 4 permission scopes
                - **Email Verification**: Secure user registration with email verification
                - **Password Security**: BCrypt encryption with account lockout protection

                ### 🔑 API Key Management
                - **Scoped API Keys**: Environment-specific API keys with fine-grained permissions
                - **Usage Analytics**: Real-time API key usage statistics and monitoring
                - **Key Rotation**: Secure API key regeneration and lifecycle management

                ### 📊 Gaming Analytics Ready
                - **GameAnalytics Compatible**: Support for 13 game genres and 6 platforms
                - **Event Schema**: Professional gaming event schema with validation
                - **Real-time Processing**: Kafka + Flink + ClickHouse data pipeline

                ## Authentication

                Most endpoints require authentication using JWT tokens. Include the JWT token in the Authorization header:
                ```
                Authorization: Bearer YOUR_JWT_TOKEN
                ```

                ## Permission Levels

                - **SUPER_ADMIN**: Full system access
                - **ORG_ADMIN**: Organization-wide access
                - **GAME_ADMIN**: Game-specific management
                - **ANALYST**: Read access with analytics capabilities
                - **VIEWER**: Read-only access
                - **DEVELOPER**: Development environment access
                - **QA**: Testing environment access
                - **MARKETING**: Marketing analytics access

                ## Rate Limiting

                API endpoints are rate-limited based on organization tier:
                - **FREE**: 1,000 requests/hour
                - **PRO**: 10,000 requests/hour
                - **ENTERPRISE**: Unlimited

                ## Support

                For technical support and documentation, visit:
                - **GitHub**: https://github.com/yourusername/pit
                - **Documentation**: https://docs.pit.example.com
                - **Support**: support@pit.example.com
                """)
            .version(applicationVersion)
            .contact(new Contact()
                .name("Pit Development Team")
                .email("dev@pit.example.com")
                .url("https://pit.example.com"))
            .license(new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT"));
    }
}
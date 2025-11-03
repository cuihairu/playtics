package io.pit.control.controller;

import io.pit.control.dto.ApiResponse;
import io.pit.control.dto.UserDTO;
import io.pit.control.security.JwtUtil;
import io.pit.control.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 认证控制器
 * 处理用户登录、注册、密码重置等认证相关操作
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@Valid @RequestBody LoginRequest request) {
        logger.info("Login attempt for email: {}", request.email);

        try {
            // 验证用户密码
            if (!userService.validatePassword(request.email, request.password)) {
                userService.recordFailedLogin(request.email, getClientIp());
                return ResponseEntity.status(401)
                    .body(ApiResponse.unauthorized("Invalid email or password"));
            }

            // 获取用户信息
            Optional<UserDTO> userOpt = userService.getUserByEmail(request.email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(401)
                    .body(ApiResponse.unauthorized("Invalid email or password"));
            }

            UserDTO user = userOpt.get();

            // 检查用户状态
            if (!user.isActive()) {
                return ResponseEntity.status(403)
                    .body(ApiResponse.forbidden("Account is disabled"));
            }

            if (user.isLocked) {
                return ResponseEntity.status(423)
                    .body(ApiResponse.error(423, "Account is temporarily locked"));
            }

            if (!user.isEmailVerified()) {
                return ResponseEntity.status(403)
                    .body(ApiResponse.forbidden("Email not verified"));
            }

            // 生成JWT令牌
            String token = jwtUtil.generateToken(
                user.id,
                user.email,
                user.orgId,
                user.globalRole.toString()
            );

            // 记录成功登录
            userService.recordSuccessfulLogin(user.id, getClientIp());

            // 构建响应数据
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("token", token);
            responseData.put("tokenType", "Bearer");
            responseData.put("expiresIn", 86400); // 24小时
            responseData.put("user", user);

            logger.info("Login successful for user: {}", user.email);
            return ResponseEntity.ok(ApiResponse.success("Login successful", responseData));

        } catch (Exception e) {
            logger.error("Login failed for email: {}", request.email, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Login failed"));
        }
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserDTO>> register(@Valid @RequestBody RegisterRequest request) {
        logger.info("Registration attempt for email: {}", request.email);

        try {
            // 创建用户DTO
            UserDTO userDTO = new UserDTO();
            userDTO.email = request.email;
            userDTO.name = request.name;
            userDTO.orgId = request.orgId;

            // 创建用户
            UserDTO createdUser = userService.createUser(userDTO, request.password);

            logger.info("Registration successful for user: {}", createdUser.email);
            return ResponseEntity.status(201)
                .body(ApiResponse.success("Registration successful. Please verify your email.", createdUser));

        } catch (IllegalArgumentException e) {
            logger.warn("Registration failed for email: {} - {}", request.email, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));
        } catch (Exception e) {
            logger.error("Registration failed for email: {}", request.email, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Registration failed"));
        }
    }

    /**
     * 邮箱验证
     */
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        logger.info("Email verification attempt with token: {}", request.token);

        try {
            boolean success = userService.verifyEmail(request.token);

            if (success) {
                logger.info("Email verification successful for token: {}", request.token);
                return ResponseEntity.ok(ApiResponse.success("Email verified successfully"));
            } else {
                return ResponseEntity.status(400)
                    .body(ApiResponse.badRequest("Invalid or expired verification token"));
            }

        } catch (Exception e) {
            logger.error("Email verification failed for token: {}", request.token, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Email verification failed"));
        }
    }

    /**
     * 请求密码重置
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        logger.info("Password reset request for email: {}", request.email);

        try {
            userService.requestPasswordReset(request.email);

            // 为安全起见，始终返回成功消息
            return ResponseEntity.ok(
                ApiResponse.success("If the email exists, a password reset link has been sent")
            );

        } catch (Exception e) {
            logger.error("Password reset request failed for email: {}", request.email, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Password reset request failed"));
        }
    }

    /**
     * 重置密码
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        logger.info("Password reset attempt with token: {}", request.token);

        try {
            boolean success = userService.resetPassword(request.token, request.newPassword);

            if (success) {
                logger.info("Password reset successful for token: {}", request.token);
                return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
            } else {
                return ResponseEntity.status(400)
                    .body(ApiResponse.badRequest("Invalid or expired reset token"));
            }

        } catch (Exception e) {
            logger.error("Password reset failed for token: {}", request.token, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Password reset failed"));
        }
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refreshToken(
            @RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401)
                .body(ApiResponse.unauthorized("Invalid authorization header"));
        }

        String token = authHeader.substring(7);

        try {
            String newToken = jwtUtil.refreshToken(token);

            if (newToken != null) {
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("token", newToken);
                responseData.put("tokenType", "Bearer");
                responseData.put("expiresIn", 86400);

                return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", responseData));
            } else {
                return ResponseEntity.status(401)
                    .body(ApiResponse.unauthorized("Invalid or expired token"));
            }

        } catch (Exception e) {
            logger.error("Token refresh failed", e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Token refresh failed"));
        }
    }

    // 私有方法
    private String getClientIp() {
        // 简化实现，实际项目中应该从HttpServletRequest中获取真实IP
        return "unknown";
    }

    // 请求DTO类
    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        public String email;

        @NotBlank(message = "Password is required")
        public String password;
    }

    public static class RegisterRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        public String email;

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name cannot exceed 100 characters")
        public String name;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        public String password;

        public String orgId; // 可选，用于邀请注册
    }

    public static class VerifyEmailRequest {
        @NotBlank(message = "Verification token is required")
        public String token;
    }

    public static class ForgotPasswordRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        public String email;
    }

    public static class ResetPasswordRequest {
        @NotBlank(message = "Reset token is required")
        public String token;

        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        public String newPassword;
    }
}
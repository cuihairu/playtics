package io.pit.control.controller;

import io.pit.control.dto.ApiKeyDTO;
import io.pit.control.dto.ApiResponse;
import io.pit.control.jpa.ApiKeyEntity;
import io.pit.control.security.JwtAuthenticationFilter;
import io.pit.control.service.ApiKeyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * API密钥管理控制器
 * 提供API密钥的创建、管理和权限控制功能
 */
@RestController
@RequestMapping("/api/api-keys")
@CrossOrigin(origins = "*")
public class ApiKeyController {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyController.class);

    @Autowired
    private ApiKeyService apiKeyService;

    /**
     * 创建API密钥
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ORG_ADMIN') or hasRole('GAME_ADMIN')")
    public ResponseEntity<ApiResponse<ApiKeyDTO>> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request,
            Authentication authentication) {

        logger.info("Creating API key for game: {} environment: {} by user: {}",
            request.gameId, request.environmentId, authentication.getName());

        try {
            // 权限检查
            if (!canManageApiKeyForGame(request.gameId, authentication)) {
                return ResponseEntity.status(403)
                    .body(ApiResponse.forbidden("Cannot create API key for this game"));
            }

            ApiKeyDTO created = apiKeyService.createApiKey(
                request.gameId,
                request.environmentId,
                request.name,
                request.description,
                request.permissions
            );

            logger.info("API key created successfully: {} for game: {}",
                created.id, request.gameId);

            return ResponseEntity.status(201)
                .body(ApiResponse.success("API key created successfully", created));

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to create API key: {}", e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to create API key for game: {}", request.gameId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to create API key"));
        }
    }

    /**
     * 根据游戏获取API密钥列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ApiKeyDTO>>> getApiKeysByGame(
            @RequestParam String gameId,
            @RequestParam(required = false) String environmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {

        try {
            // 权限检查
            if (!canAccessGame(gameId, authentication)) {
                return ResponseEntity.status(403)
                    .body(ApiResponse.forbidden("Access denied to game"));
            }

            // 创建分页参数
            Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<ApiKeyDTO> apiKeys;
            if (environmentId != null) {
                apiKeys = apiKeyService.getApiKeysByGameAndEnvironment(gameId, environmentId, pageable);
            } else {
                apiKeys = apiKeyService.getApiKeysByGame(gameId, pageable);
            }

            return ResponseEntity.ok(
                ApiResponse.page(
                    apiKeys.getContent(),
                    apiKeys.getTotalElements(),
                    page,
                    size
                )
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve API keys for game: {}", gameId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve API keys"));
        }
    }

    /**
     * 根据ID获取API密钥
     */
    @GetMapping("/{keyId}")
    @PreAuthorize("@apiKeyController.canAccessApiKey(#keyId, authentication)")
    public ResponseEntity<ApiResponse<ApiKeyDTO>> getApiKey(
            @PathVariable String keyId,
            Authentication authentication) {

        try {
            Optional<ApiKeyDTO> apiKey = apiKeyService.getApiKey(keyId);

            if (apiKey.isPresent()) {
                return ResponseEntity.ok(
                    ApiResponse.success("API key retrieved successfully", apiKey.get())
                );
            } else {
                return ResponseEntity.status(404)
                    .body(ApiResponse.notFound("API key not found"));
            }

        } catch (Exception e) {
            logger.error("Failed to retrieve API key: {}", keyId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve API key"));
        }
    }

    /**
     * 更新API密钥
     */
    @PutMapping("/{keyId}")
    @PreAuthorize("@apiKeyController.canManageApiKey(#keyId, authentication)")
    public ResponseEntity<ApiResponse<ApiKeyDTO>> updateApiKey(
            @PathVariable String keyId,
            @Valid @RequestBody UpdateApiKeyRequest request,
            Authentication authentication) {

        logger.info("Updating API key: {} by user: {}",
            keyId, authentication.getName());

        try {
            ApiKeyDTO updated = apiKeyService.updateApiKey(
                keyId,
                request.name,
                request.description,
                request.permissions
            );

            logger.info("API key updated successfully: {}", keyId);
            return ResponseEntity.ok(
                ApiResponse.success("API key updated successfully", updated)
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to update API key {}: {}", keyId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to update API key: {}", keyId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to update API key"));
        }
    }

    /**
     * 删除API密钥
     */
    @DeleteMapping("/{keyId}")
    @PreAuthorize("@apiKeyController.canManageApiKey(#keyId, authentication)")
    public ResponseEntity<ApiResponse<String>> deleteApiKey(
            @PathVariable String keyId,
            Authentication authentication) {

        logger.info("Deleting API key: {} by user: {}",
            keyId, authentication.getName());

        try {
            apiKeyService.deleteApiKey(keyId);

            logger.info("API key deleted successfully: {}", keyId);
            return ResponseEntity.ok(
                ApiResponse.success("API key deleted successfully")
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to delete API key {}: {}", keyId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to delete API key: {}", keyId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to delete API key"));
        }
    }

    /**
     * 重新生成API密钥
     */
    @PostMapping("/{keyId}/regenerate")
    @PreAuthorize("@apiKeyController.canManageApiKey(#keyId, authentication)")
    public ResponseEntity<ApiResponse<ApiKeyDTO>> regenerateApiKey(
            @PathVariable String keyId,
            Authentication authentication) {

        logger.info("Regenerating API key: {} by user: {}",
            keyId, authentication.getName());

        try {
            ApiKeyDTO regenerated = apiKeyService.regenerateApiKey(keyId);

            logger.info("API key regenerated successfully: {}", keyId);
            return ResponseEntity.ok(
                ApiResponse.success("API key regenerated successfully", regenerated)
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to regenerate API key {}: {}", keyId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to regenerate API key: {}", keyId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to regenerate API key"));
        }
    }

    /**
     * 启用/禁用API密钥
     */
    @PostMapping("/{keyId}/toggle")
    @PreAuthorize("@apiKeyController.canManageApiKey(#keyId, authentication)")
    public ResponseEntity<ApiResponse<ApiKeyDTO>> toggleApiKey(
            @PathVariable String keyId,
            @RequestParam boolean enabled,
            Authentication authentication) {

        logger.info("{} API key: {} by user: {}",
            enabled ? "Enabling" : "Disabling", keyId, authentication.getName());

        try {
            ApiKeyDTO toggled = apiKeyService.toggleApiKey(keyId, enabled);

            logger.info("API key {} successfully: {}", enabled ? "enabled" : "disabled", keyId);
            return ResponseEntity.ok(
                ApiResponse.success(
                    "API key " + (enabled ? "enabled" : "disabled") + " successfully",
                    toggled
                )
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to toggle API key {}: {}", keyId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to toggle API key: {}", keyId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to toggle API key"));
        }
    }

    /**
     * 获取API密钥使用统计
     */
    @GetMapping("/{keyId}/statistics")
    @PreAuthorize("@apiKeyController.canAccessApiKey(#keyId, authentication)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getApiKeyStatistics(
            @PathVariable String keyId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Authentication authentication) {

        try {
            Map<String, Object> statistics = apiKeyService.getApiKeyStatistics(keyId, startDate, endDate);

            return ResponseEntity.ok(
                ApiResponse.success("Statistics retrieved successfully", statistics)
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve API key statistics: {}", keyId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve statistics"));
        }
    }

    /**
     * 验证API密钥
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateApiKey(
            @Valid @RequestBody ValidateApiKeyRequest request) {

        try {
            Map<String, Object> validation = apiKeyService.validateApiKey(request.keyValue);

            return ResponseEntity.ok(
                ApiResponse.success("API key validation completed", validation)
            );

        } catch (Exception e) {
            logger.error("Failed to validate API key", e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to validate API key"));
        }
    }

    // 权限检查方法

    public boolean canAccessGame(String gameId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            if (details.isSuperAdmin()) {
                return true;
            }

            // 通过API Key服务检查用户是否有权限访问该游戏
            return apiKeyService.canUserAccessGame(details.userId, gameId);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canManageApiKeyForGame(String gameId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            if (details.isSuperAdmin()) {
                return true;
            }

            return apiKeyService.canUserManageGame(details.userId, gameId);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canAccessApiKey(String keyId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            if (details.isSuperAdmin()) {
                return true;
            }

            Optional<ApiKeyDTO> apiKey = apiKeyService.getApiKey(keyId);
            return apiKey.isPresent() && canAccessGame(apiKey.get().gameId, authentication);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canManageApiKey(String keyId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            if (details.isSuperAdmin()) {
                return true;
            }

            Optional<ApiKeyDTO> apiKey = apiKeyService.getApiKey(keyId);
            return apiKey.isPresent() && canManageApiKeyForGame(apiKey.get().gameId, authentication);
        } catch (Exception e) {
            return false;
        }
    }

    // 请求DTO类

    public static class CreateApiKeyRequest {
        @NotBlank(message = "Game ID is required")
        public String gameId;

        @NotBlank(message = "Environment ID is required")
        public String environmentId;

        @NotBlank(message = "Name is required")
        public String name;

        public String description;

        public List<ApiKeyEntity.Permission> permissions;
    }

    public static class UpdateApiKeyRequest {
        public String name;
        public String description;
        public List<ApiKeyEntity.Permission> permissions;
    }

    public static class ValidateApiKeyRequest {
        @NotBlank(message = "API key value is required")
        public String keyValue;
    }
}
package io.pit.control.controller;

import io.pit.control.dto.ApiResponse;
import io.pit.control.dto.UserDTO;
import io.pit.control.jpa.UserRoleEntity;
import io.pit.control.security.JwtAuthenticationFilter;
import io.pit.control.service.UserService;
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
import java.util.Optional;

/**
 * 用户管理控制器
 * 提供用户管理和权限控制功能
 */
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    /**
     * 创建用户
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<UserDTO>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication) {

        logger.info("Creating user: {} by: {}",
            request.userDTO.email, authentication.getName());

        try {
            // 权限检查：组织管理员只能在自己的组织内创建用户
            if (!canCreateUserInOrg(request.userDTO.orgId, authentication)) {
                return ResponseEntity.status(403)
                    .body(ApiResponse.forbidden("Cannot create user in this organization"));
            }

            UserDTO created = userService.createUser(request.userDTO, request.password);

            logger.info("User created successfully: {} (ID: {})",
                created.email, created.id);

            return ResponseEntity.status(201)
                .body(ApiResponse.success("User created successfully", created));

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to create user: {}", e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to create user: {}", request.userDTO.email, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to create user"));
        }
    }

    /**
     * 根据组织获取用户列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDTO>>> getUsersByOrganization(
            @RequestParam(required = false) String orgId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {

        try {
            // 如果没有指定orgId，从认证信息中获取
            if (orgId == null) {
                JwtAuthenticationFilter.PitAuthenticationDetails details =
                    (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();
                orgId = details.orgId;
            }

            // 权限检查
            if (!canAccessOrganization(orgId, authentication)) {
                return ResponseEntity.status(403)
                    .body(ApiResponse.forbidden("Access denied to organization"));
            }

            List<UserDTO> users = userService.getUsersByOrganization(orgId);

            return ResponseEntity.ok(
                ApiResponse.success("Users retrieved successfully", users)
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve users for organization: {}", orgId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve users"));
        }
    }

    /**
     * 搜索用户
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserDTO>>> searchUsers(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            Authentication authentication) {

        try {
            // 创建分页和排序参数
            Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<UserDTO> users = userService.searchUsers(q, pageable);

            return ResponseEntity.ok(
                ApiResponse.page(
                    users.getContent(),
                    users.getTotalElements(),
                    page,
                    size
                )
            );

        } catch (Exception e) {
            logger.error("Failed to search users with query: {}", q, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to search users"));
        }
    }

    /**
     * 根据ID获取用户
     */
    @GetMapping("/{userId}")
    @PreAuthorize("@userController.canAccessUser(#userId, authentication)")
    public ResponseEntity<ApiResponse<UserDTO>> getUser(
            @PathVariable String userId,
            Authentication authentication) {

        try {
            Optional<UserDTO> user = userService.getUser(userId);

            if (user.isPresent()) {
                return ResponseEntity.ok(
                    ApiResponse.success("User retrieved successfully", user.get())
                );
            } else {
                return ResponseEntity.status(404)
                    .body(ApiResponse.notFound("User not found"));
            }

        } catch (Exception e) {
            logger.error("Failed to retrieve user: {}", userId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve user"));
        }
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDTO>> getCurrentUser(Authentication authentication) {

        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            Optional<UserDTO> user = userService.getUser(details.userId);

            if (user.isPresent()) {
                return ResponseEntity.ok(
                    ApiResponse.success("Current user retrieved successfully", user.get())
                );
            } else {
                return ResponseEntity.status(404)
                    .body(ApiResponse.notFound("Current user not found"));
            }

        } catch (Exception e) {
            logger.error("Failed to retrieve current user for: {}", authentication.getName(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve current user"));
        }
    }

    /**
     * 更新用户
     */
    @PutMapping("/{userId}")
    @PreAuthorize("@userController.canManageUser(#userId, authentication)")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UserDTO userDTO,
            Authentication authentication) {

        logger.info("Updating user: {} by: {}",
            userId, authentication.getName());

        try {
            UserDTO updated = userService.updateUser(userId, userDTO);

            logger.info("User updated successfully: {}", userId);
            return ResponseEntity.ok(
                ApiResponse.success("User updated successfully", updated)
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to update user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to update user: {}", userId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to update user"));
        }
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("@userController.canDeleteUser(#userId, authentication)")
    public ResponseEntity<ApiResponse<String>> deleteUser(
            @PathVariable String userId,
            Authentication authentication) {

        logger.info("Deleting user: {} by: {}",
            userId, authentication.getName());

        try {
            userService.deleteUser(userId);

            logger.info("User deleted successfully: {}", userId);
            return ResponseEntity.ok(
                ApiResponse.success("User deleted successfully")
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to delete user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (IllegalStateException e) {
            logger.warn("Cannot delete user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(409)
                .body(ApiResponse.conflict(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to delete user: {}", userId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to delete user"));
        }
    }

    /**
     * 为用户分配角色
     */
    @PostMapping("/{userId}/roles")
    @PreAuthorize("@userController.canManageUserRoles(#userId, authentication)")
    public ResponseEntity<ApiResponse<String>> assignRole(
            @PathVariable String userId,
            @Valid @RequestBody AssignRoleRequest request,
            Authentication authentication) {

        logger.info("Assigning role {} to user {} by: {}",
            request.role, userId, authentication.getName());

        try {
            userService.assignRole(
                userId,
                request.role,
                request.scope,
                request.orgId,
                request.gameId,
                request.environmentId
            );

            logger.info("Role assigned successfully: {} to user {}", request.role, userId);
            return ResponseEntity.ok(
                ApiResponse.success("Role assigned successfully")
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to assign role to user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (IllegalStateException e) {
            logger.warn("Cannot assign role to user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(409)
                .body(ApiResponse.conflict(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to assign role to user: {}", userId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to assign role"));
        }
    }

    /**
     * 移除用户角色
     */
    @DeleteMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("@userController.canManageUserRoles(#userId, authentication)")
    public ResponseEntity<ApiResponse<String>> removeRole(
            @PathVariable String userId,
            @PathVariable String roleId,
            Authentication authentication) {

        logger.info("Removing role {} from user {} by: {}",
            roleId, userId, authentication.getName());

        try {
            userService.removeRole(userId, roleId);

            logger.info("Role removed successfully: {} from user {}", roleId, userId);
            return ResponseEntity.ok(
                ApiResponse.success("Role removed successfully")
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to remove role from user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to remove role from user: {}", userId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to remove role"));
        }
    }

    /**
     * 获取用户角色
     */
    @GetMapping("/{userId}/roles")
    @PreAuthorize("@userController.canAccessUser(#userId, authentication)")
    public ResponseEntity<ApiResponse<List<UserRoleEntity>>> getUserRoles(
            @PathVariable String userId,
            Authentication authentication) {

        try {
            List<UserRoleEntity> roles = userService.getUserRoles(userId);

            return ResponseEntity.ok(
                ApiResponse.success("User roles retrieved successfully", roles)
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve roles for user: {}", userId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve user roles"));
        }
    }

    // 权限检查方法

    public boolean canAccessOrganization(String orgId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            return details.isSuperAdmin() || orgId.equals(details.orgId);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canCreateUserInOrg(String orgId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            return details.isSuperAdmin() ||
                   (details.isOrgAdmin() && orgId.equals(details.orgId));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canAccessUser(String userId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            // 用户可以访问自己的信息
            if (userId.equals(details.userId)) {
                return true;
            }

            // 超级管理员可以访问所有用户
            if (details.isSuperAdmin()) {
                return true;
            }

            // 组织管理员可以访问同组织的用户
            if (details.isOrgAdmin()) {
                Optional<UserDTO> user = userService.getUser(userId);
                return user.isPresent() && user.get().orgId.equals(details.orgId);
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canManageUser(String userId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            // 用户可以更新自己的基本信息
            if (userId.equals(details.userId)) {
                return true;
            }

            // 超级管理员可以管理所有用户
            if (details.isSuperAdmin()) {
                return true;
            }

            // 组织管理员可以管理同组织的用户
            if (details.isOrgAdmin()) {
                Optional<UserDTO> user = userService.getUser(userId);
                return user.isPresent() && user.get().orgId.equals(details.orgId);
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canDeleteUser(String userId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            // 用户不能删除自己
            if (userId.equals(details.userId)) {
                return false;
            }

            // 超级管理员可以删除其他用户
            if (details.isSuperAdmin()) {
                return true;
            }

            // 组织管理员可以删除同组织的非管理员用户
            if (details.isOrgAdmin()) {
                Optional<UserDTO> user = userService.getUser(userId);
                return user.isPresent() &&
                       user.get().orgId.equals(details.orgId) &&
                       !user.get().isOrgAdmin();
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean canManageUserRoles(String userId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            // 超级管理员和组织管理员可以管理角色
            return details.isSuperAdmin() || details.isOrgAdmin();
        } catch (Exception e) {
            return false;
        }
    }

    // 请求DTO类

    public static class CreateUserRequest {
        @Valid
        public UserDTO userDTO;

        @NotBlank(message = "Password is required")
        public String password;
    }

    public static class AssignRoleRequest {
        public UserRoleEntity.RoleType role;
        public UserRoleEntity.PermissionScope scope;
        public String orgId;
        public String gameId;
        public String environmentId;
    }
}
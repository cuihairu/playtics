package io.pit.control.controller;

import io.pit.control.dto.ApiResponse;
import io.pit.control.dto.OrganizationDTO;
import io.pit.control.jpa.OrganizationEntity;
import io.pit.control.security.JwtAuthenticationFilter;
import io.pit.control.service.OrganizationService;
import jakarta.validation.Valid;
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
 * 组织管理控制器
 * 提供组织的CRUD操作和管理功能
 */
@RestController
@RequestMapping("/api/organizations")
@CrossOrigin(origins = "*")
public class OrganizationController {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationController.class);

    @Autowired
    private OrganizationService organizationService;

    /**
     * 创建组织
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrganizationDTO>> createOrganization(
            @Valid @RequestBody OrganizationDTO organizationDTO,
            Authentication authentication) {

        logger.info("Creating organization: {} by user: {}",
            organizationDTO.name, authentication.getName());

        try {
            OrganizationDTO created = organizationService.createOrganization(organizationDTO);

            logger.info("Organization created successfully: {} (ID: {})",
                created.name, created.id);

            return ResponseEntity.status(201)
                .body(ApiResponse.success("Organization created successfully", created));

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to create organization: {}", e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to create organization: {}", organizationDTO.name, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to create organization"));
        }
    }

    /**
     * 获取所有组织
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<OrganizationDTO>>> getAllOrganizations() {

        try {
            List<OrganizationDTO> organizations = organizationService.getAllActiveOrganizations();

            return ResponseEntity.ok(
                ApiResponse.success("Organizations retrieved successfully", organizations)
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve organizations", e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve organizations"));
        }
    }

    /**
     * 分页搜索组织
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<OrganizationDTO>>> searchOrganizations(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        try {
            // 创建分页和排序参数
            Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<OrganizationDTO> organizations = organizationService.searchOrganizations(q, pageable);

            return ResponseEntity.ok(
                ApiResponse.page(
                    organizations.getContent(),
                    organizations.getTotalElements(),
                    page,
                    size
                )
            );

        } catch (Exception e) {
            logger.error("Failed to search organizations with query: {}", q, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to search organizations"));
        }
    }

    /**
     * 根据ID获取组织
     */
    @GetMapping("/{orgId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @organizationController.canAccessOrganization(#orgId, authentication)")
    public ResponseEntity<ApiResponse<OrganizationDTO>> getOrganization(
            @PathVariable String orgId,
            Authentication authentication) {

        try {
            Optional<OrganizationDTO> organization = organizationService.getOrganization(orgId);

            if (organization.isPresent()) {
                return ResponseEntity.ok(
                    ApiResponse.success("Organization retrieved successfully", organization.get())
                );
            } else {
                return ResponseEntity.status(404)
                    .body(ApiResponse.notFound("Organization not found"));
            }

        } catch (Exception e) {
            logger.error("Failed to retrieve organization: {}", orgId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve organization"));
        }
    }

    /**
     * 更新组织
     */
    @PutMapping("/{orgId}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @organizationController.canManageOrganization(#orgId, authentication)")
    public ResponseEntity<ApiResponse<OrganizationDTO>> updateOrganization(
            @PathVariable String orgId,
            @Valid @RequestBody OrganizationDTO organizationDTO,
            Authentication authentication) {

        logger.info("Updating organization: {} by user: {}",
            orgId, authentication.getName());

        try {
            OrganizationDTO updated = organizationService.updateOrganization(orgId, organizationDTO);

            logger.info("Organization updated successfully: {}", orgId);
            return ResponseEntity.ok(
                ApiResponse.success("Organization updated successfully", updated)
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to update organization {}: {}", orgId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to update organization: {}", orgId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to update organization"));
        }
    }

    /**
     * 删除组织
     */
    @DeleteMapping("/{orgId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<String>> deleteOrganization(
            @PathVariable String orgId,
            Authentication authentication) {

        logger.info("Deleting organization: {} by user: {}",
            orgId, authentication.getName());

        try {
            organizationService.deleteOrganization(orgId);

            logger.info("Organization deleted successfully: {}", orgId);
            return ResponseEntity.ok(
                ApiResponse.success("Organization deleted successfully")
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to delete organization {}: {}", orgId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (IllegalStateException e) {
            logger.warn("Cannot delete organization {}: {}", orgId, e.getMessage());
            return ResponseEntity.status(409)
                .body(ApiResponse.conflict(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to delete organization: {}", orgId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to delete organization"));
        }
    }

    /**
     * 升级组织层级
     */
    @PostMapping("/{orgId}/upgrade")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrganizationDTO>> upgradeOrganization(
            @PathVariable String orgId,
            @RequestParam OrganizationEntity.OrganizationTier tier,
            Authentication authentication) {

        logger.info("Upgrading organization {} to tier {} by user: {}",
            orgId, tier, authentication.getName());

        try {
            OrganizationDTO upgraded = organizationService.upgradeOrganization(orgId, tier);

            logger.info("Organization upgraded successfully: {} to {}", orgId, tier);
            return ResponseEntity.ok(
                ApiResponse.success("Organization upgraded successfully", upgraded)
            );

        } catch (IllegalArgumentException e) {
            logger.warn("Failed to upgrade organization {}: {}", orgId, e.getMessage());
            return ResponseEntity.status(400)
                .body(ApiResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to upgrade organization: {}", orgId, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to upgrade organization"));
        }
    }

    /**
     * 根据层级获取组织
     */
    @GetMapping("/by-tier/{tier}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<OrganizationDTO>>> getOrganizationsByTier(
            @PathVariable OrganizationEntity.OrganizationTier tier) {

        try {
            List<OrganizationDTO> organizations = organizationService.getOrganizationsByTier(tier);

            return ResponseEntity.ok(
                ApiResponse.success("Organizations retrieved successfully", organizations)
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve organizations by tier: {}", tier, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve organizations"));
        }
    }

    /**
     * 获取组织统计信息
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrganizationStatistics() {

        try {
            Map<String, Object> statistics = organizationService.getOrganizationStatistics();

            return ResponseEntity.ok(
                ApiResponse.success("Statistics retrieved successfully", statistics)
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve organization statistics", e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve statistics"));
        }
    }

    /**
     * 检查组织名称是否可用
     */
    @GetMapping("/check-name")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('ORG_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkOrganizationNameAvailability(
            @RequestParam String name) {

        try {
            boolean available = organizationService.isOrganizationNameAvailable(name);

            Map<String, Boolean> result = Map.of("available", available);

            return ResponseEntity.ok(
                ApiResponse.success("Name availability checked", result)
            );

        } catch (Exception e) {
            logger.error("Failed to check organization name availability: {}", name, e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to check name availability"));
        }
    }

    /**
     * 获取需要升级的组织
     */
    @GetMapping("/upgrade-candidates")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<OrganizationDTO>>> getOrganizationsNeedingUpgrade() {

        try {
            List<OrganizationDTO> organizations = organizationService.getOrganizationsNeedingUpgrade();

            return ResponseEntity.ok(
                ApiResponse.success("Upgrade candidates retrieved successfully", organizations)
            );

        } catch (Exception e) {
            logger.error("Failed to retrieve organizations needing upgrade", e);
            return ResponseEntity.status(500)
                .body(ApiResponse.error("Failed to retrieve upgrade candidates"));
        }
    }

    // 权限检查方法

    /**
     * 检查用户是否可以访问组织
     */
    public boolean canAccessOrganization(String orgId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            // 超级管理员可以访问所有组织
            if (details.isSuperAdmin()) {
                return true;
            }

            // 组织管理员和用户只能访问自己的组织
            return orgId.equals(details.orgId);

        } catch (Exception e) {
            logger.warn("Failed to check organization access for user: {}", authentication.getName(), e);
            return false;
        }
    }

    /**
     * 检查用户是否可以管理组织
     */
    public boolean canManageOrganization(String orgId, Authentication authentication) {
        try {
            JwtAuthenticationFilter.PitAuthenticationDetails details =
                (JwtAuthenticationFilter.PitAuthenticationDetails) authentication.getDetails();

            // 超级管理员可以管理所有组织
            if (details.isSuperAdmin()) {
                return true;
            }

            // 组织管理员只能管理自己的组织
            return details.isOrgAdmin() && orgId.equals(details.orgId);

        } catch (Exception e) {
            logger.warn("Failed to check organization management permission for user: {}", authentication.getName(), e);
            return false;
        }
    }
}
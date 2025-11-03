package io.pit.control.service;

import io.pit.control.dto.OrganizationDTO;
import io.pit.control.jpa.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 组织管理服务
 * 提供企业级多租户组织管理功能
 */
@Service
@Transactional
public class OrganizationService {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationService.class);

    @Autowired
    private OrganizationRepo organizationRepo;

    @Autowired
    private GameRepo gameRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ApiKeyRepo apiKeyRepo;

    /**
     * 创建新组织
     */
    public OrganizationDTO createOrganization(OrganizationDTO dto) {
        logger.info("Creating organization: {}", dto.name);

        // 生成唯一ID
        if (dto.id == null || dto.id.trim().isEmpty()) {
            dto.id = "org_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }

        // 设置默认值
        if (dto.tier == null) {
            dto.tier = OrganizationEntity.OrganizationTier.FREE;
        }
        setDefaultQuotas(dto);

        // 转换并保存
        OrganizationEntity entity = dto.toEntity();
        entity = organizationRepo.save(entity);

        logger.info("Organization created successfully: {} (ID: {})", entity.name, entity.id);
        return new OrganizationDTO(entity);
    }

    /**
     * 更新组织信息
     */
    public OrganizationDTO updateOrganization(String orgId, OrganizationDTO dto) {
        logger.info("Updating organization: {}", orgId);

        OrganizationEntity entity = organizationRepo.findById(orgId)
            .filter(org -> org.deletedAt == null)
            .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        // 检查层级变更权限
        if (dto.tier != null && dto.tier != entity.tier) {
            validateTierChange(entity, dto.tier);
        }

        dto.updateEntity(entity);
        entity = organizationRepo.save(entity);

        logger.info("Organization updated successfully: {}", orgId);
        return new OrganizationDTO(entity);
    }

    /**
     * 删除组织（软删除）
     */
    public void deleteOrganization(String orgId) {
        logger.info("Deleting organization: {}", orgId);

        OrganizationEntity entity = organizationRepo.findById(orgId)
            .filter(org -> org.deletedAt == null)
            .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        // 检查是否有活跃的游戏
        long activeGames = gameRepo.countByOrgIdAndDeletedAtIsNull(orgId);
        if (activeGames > 0) {
            throw new IllegalStateException("Cannot delete organization with active games. Please delete all games first.");
        }

        // 软删除
        entity.deletedAt = LocalDateTime.now();
        entity.status = OrganizationEntity.OrganizationStatus.DELETED;
        organizationRepo.save(entity);

        logger.info("Organization deleted successfully: {}", orgId);
    }

    /**
     * 根据ID获取组织
     */
    @Transactional(readOnly = true)
    public Optional<OrganizationDTO> getOrganization(String orgId) {
        return organizationRepo.findById(orgId)
            .filter(org -> org.deletedAt == null)
            .map(entity -> {
                OrganizationDTO dto = new OrganizationDTO(entity);
                enrichWithStatistics(dto);
                return dto;
            });
    }

    /**
     * 获取所有活跃组织
     */
    @Transactional(readOnly = true)
    public List<OrganizationDTO> getAllActiveOrganizations() {
        return organizationRepo.findAllActive().stream()
            .map(entity -> {
                OrganizationDTO dto = new OrganizationDTO(entity);
                enrichWithStatistics(dto);
                return dto;
            })
            .collect(Collectors.toList());
    }

    /**
     * 搜索组织
     */
    @Transactional(readOnly = true)
    public Page<OrganizationDTO> searchOrganizations(String query, Pageable pageable) {
        return organizationRepo.searchByName(query, pageable)
            .map(entity -> {
                OrganizationDTO dto = new OrganizationDTO(entity);
                enrichWithStatistics(dto);
                return dto;
            });
    }

    /**
     * 根据层级获取组织
     */
    @Transactional(readOnly = true)
    public List<OrganizationDTO> getOrganizationsByTier(OrganizationEntity.OrganizationTier tier) {
        return organizationRepo.findByTierAndDeletedAtIsNull(tier).stream()
            .map(entity -> {
                OrganizationDTO dto = new OrganizationDTO(entity);
                enrichWithStatistics(dto);
                return dto;
            })
            .collect(Collectors.toList());
    }

    /**
     * 升级组织层级
     */
    public OrganizationDTO upgradeOrganization(String orgId, OrganizationEntity.OrganizationTier newTier) {
        logger.info("Upgrading organization {} to tier: {}", orgId, newTier);

        OrganizationEntity entity = organizationRepo.findById(orgId)
            .filter(org -> org.deletedAt == null)
            .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        if (newTier.ordinal() <= entity.tier.ordinal()) {
            throw new IllegalArgumentException("Cannot downgrade organization tier");
        }

        entity.tier = newTier;
        updateQuotasForTier(entity, newTier);
        entity = organizationRepo.save(entity);

        logger.info("Organization upgraded successfully: {} to {}", orgId, newTier);
        return new OrganizationDTO(entity);
    }

    /**
     * 获取组织统计信息
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getOrganizationStatistics() {
        List<Object> stats = organizationRepo.getOrganizationStatistics();
        if (stats.isEmpty()) {
            return Map.of(
                "totalOrgs", 0L,
                "freeOrgs", 0L,
                "proOrgs", 0L,
                "enterpriseOrgs", 0L,
                "activeOrgs", 0L
            );
        }
        return (Map<String, Object>) stats.get(0);
    }

    /**
     * 检查组织名称是否可用
     */
    @Transactional(readOnly = true)
    public boolean isOrganizationNameAvailable(String name) {
        return organizationRepo.searchByName(name, Pageable.unpaged()).isEmpty();
    }

    /**
     * 获取需要升级的免费组织
     */
    @Transactional(readOnly = true)
    public List<OrganizationDTO> getOrganizationsNeedingUpgrade() {
        return organizationRepo.findFreeOrganizationsNeedingUpgrade().stream()
            .map(entity -> {
                OrganizationDTO dto = new OrganizationDTO(entity);
                enrichWithStatistics(dto);
                return dto;
            })
            .collect(Collectors.toList());
    }

    // 私有辅助方法

    /**
     * 设置默认配额
     */
    private void setDefaultQuotas(OrganizationDTO dto) {
        if (dto.maxGames == null) {
            dto.maxGames = switch (dto.tier) {
                case FREE -> 5;
                case PRO -> 50;
                case ENTERPRISE -> 1000;
            };
        }

        if (dto.maxEventsPerMonth == null) {
            dto.maxEventsPerMonth = switch (dto.tier) {
                case FREE -> 1_000_000L;
                case PRO -> 100_000_000L;
                case ENTERPRISE -> -1L; // 无限制
            };
        }

        if (dto.maxDataRetentionDays == null) {
            dto.maxDataRetentionDays = switch (dto.tier) {
                case FREE -> 90;
                case PRO -> 365;
                case ENTERPRISE -> 1095; // 3年
            };
        }
    }

    /**
     * 验证层级变更
     */
    private void validateTierChange(OrganizationEntity entity, OrganizationEntity.OrganizationTier newTier) {
        // 检查当前游戏数量是否超过新层级限制
        long currentGameCount = gameRepo.countByOrgIdAndDeletedAtIsNull(entity.id);
        int maxGamesForNewTier = switch (newTier) {
            case FREE -> 5;
            case PRO -> 50;
            case ENTERPRISE -> 1000;
        };

        if (currentGameCount > maxGamesForNewTier) {
            throw new IllegalArgumentException(
                String.format("Cannot downgrade to %s tier. Current game count (%d) exceeds limit (%d)",
                    newTier, currentGameCount, maxGamesForNewTier));
        }
    }

    /**
     * 更新层级配额
     */
    private void updateQuotasForTier(OrganizationEntity entity, OrganizationEntity.OrganizationTier tier) {
        entity.maxGames = switch (tier) {
            case FREE -> 5;
            case PRO -> 50;
            case ENTERPRISE -> 1000;
        };

        entity.maxEventsPerMonth = switch (tier) {
            case FREE -> 1_000_000L;
            case PRO -> 100_000_000L;
            case ENTERPRISE -> -1L;
        };

        entity.maxDataRetentionDays = switch (tier) {
            case FREE -> 90;
            case PRO -> 365;
            case ENTERPRISE -> 1095;
        };
    }

    /**
     * 丰富统计信息
     */
    private void enrichWithStatistics(OrganizationDTO dto) {
        dto.totalGames = (int) gameRepo.countByOrgIdAndDeletedAtIsNull(dto.id);
        dto.totalUsers = (int) userRepo.countByOrgIdAndDeletedAtIsNull(dto.id);

        // 计算上线游戏数
        dto.liveGames = (int) gameRepo.findByOrgIdAndDeletedAtIsNull(dto.id).stream()
            .filter(game -> game.status == GameEntity.GameStatus.LIVE)
            .count();
    }
}
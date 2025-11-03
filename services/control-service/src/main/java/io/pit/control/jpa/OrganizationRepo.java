package io.pit.control.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 组织数据访问接口
 */
@Repository
public interface OrganizationRepo extends JpaRepository<OrganizationEntity, String> {

    /**
     * 查找活跃的组织
     */
    @Query("SELECT o FROM OrganizationEntity o WHERE o.status = 'ACTIVE' AND o.deletedAt IS NULL")
    List<OrganizationEntity> findAllActive();

    /**
     * 根据层级查找组织
     */
    List<OrganizationEntity> findByTierAndDeletedAtIsNull(OrganizationEntity.OrganizationTier tier);

    /**
     * 根据名称搜索组织（模糊匹配）
     */
    @Query("SELECT o FROM OrganizationEntity o WHERE " +
           "(LOWER(o.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(o.displayName) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "o.deletedAt IS NULL")
    Page<OrganizationEntity> searchByName(@Param("query") String query, Pageable pageable);

    /**
     * 查找联系邮箱匹配的组织
     */
    Optional<OrganizationEntity> findByContactEmailAndDeletedAtIsNull(String contactEmail);

    /**
     * 统计指定层级的组织数量
     */
    @Query("SELECT COUNT(o) FROM OrganizationEntity o WHERE o.tier = :tier AND o.status = 'ACTIVE' AND o.deletedAt IS NULL")
    long countByTierAndActive(@Param("tier") OrganizationEntity.OrganizationTier tier);

    /**
     * 查找需要升级的免费组织（基于游戏数量）
     */
    @Query("SELECT o FROM OrganizationEntity o WHERE o.tier = 'FREE' AND " +
           "(SELECT COUNT(g) FROM GameEntity g WHERE g.orgId = o.id AND g.deletedAt IS NULL) >= o.maxGames")
    List<OrganizationEntity> findFreeOrganizationsNeedingUpgrade();

    /**
     * 查找即将过期的组织（如果有试用期等）
     */
    @Query("SELECT o FROM OrganizationEntity o WHERE o.status = 'ACTIVE' AND o.deletedAt IS NULL AND " +
           "o.createdAt < :beforeDate")
    List<OrganizationEntity> findOldOrganizations(@Param("beforeDate") LocalDateTime beforeDate);

    /**
     * 根据地区查找组织
     */
    List<OrganizationEntity> findByRegionAndDeletedAtIsNull(String region);

    /**
     * 查找启用GDPR合规的组织
     */
    @Query("SELECT o FROM OrganizationEntity o WHERE o.complianceRequirements LIKE '%GDPR%' AND o.deletedAt IS NULL")
    List<OrganizationEntity> findGdprCompliantOrganizations();

    /**
     * 获取组织统计信息
     */
    @Query("SELECT new map(" +
           "COUNT(o) as totalOrgs, " +
           "COUNT(CASE WHEN o.tier = 'FREE' THEN 1 END) as freeOrgs, " +
           "COUNT(CASE WHEN o.tier = 'PRO' THEN 1 END) as proOrgs, " +
           "COUNT(CASE WHEN o.tier = 'ENTERPRISE' THEN 1 END) as enterpriseOrgs, " +
           "COUNT(CASE WHEN o.status = 'ACTIVE' THEN 1 END) as activeOrgs" +
           ") FROM OrganizationEntity o WHERE o.deletedAt IS NULL")
    List<Object> getOrganizationStatistics();
}
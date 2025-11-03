package io.pit.control.jpa;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 组织实体 - 游戏公司/发行商级别的租户
 * 支持多层级租户架构的顶层实体
 */
@Entity
@Table(name = "organizations")
public class OrganizationEntity {

    @Id
    @Column(length = 32)
    public String id;

    @Column(nullable = false, length = 100)
    public String name;

    @Column(name = "display_name", length = 200)
    public String displayName;

    @Column(length = 500)
    public String description;

    /**
     * 组织层级: free, pro, enterprise
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public OrganizationTier tier = OrganizationTier.FREE;

    /**
     * 组织状态: active, suspended, deleted
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public OrganizationStatus status = OrganizationStatus.ACTIVE;

    // 联系信息
    @Column(name = "contact_email", length = 255)
    public String contactEmail;

    @Column(name = "contact_phone", length = 50)
    public String contactPhone;

    // 配额和限制
    @Column(name = "max_games")
    public Integer maxGames = 5; // 默认最多5个游戏

    @Column(name = "max_events_per_month")
    public Long maxEventsPerMonth = 1000000L; // 默认月100万事件

    @Column(name = "max_data_retention_days")
    public Integer maxDataRetentionDays = 90; // 默认保留90天

    // 计费信息
    @Column(name = "billing_email", length = 255)
    public String billingEmail;

    @Column(name = "payment_method", length = 50)
    public String paymentMethod;

    // 地理和合规
    @Column(length = 10)
    public String region; // US, EU, APAC

    @Column(name = "data_residency", length = 20)
    public String dataResidency; // 数据驻留要求

    @Column(name = "compliance_requirements")
    public String complianceRequirements; // GDPR, CCPA等

    // 时间戳
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    public LocalDateTime deletedAt;

    // 关联关系
    @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY)
    public List<GameEntity> games;

    @OneToMany(mappedBy = "organization", fetch = FetchType.LAZY)
    public List<UserEntity> users;

    public enum OrganizationTier {
        FREE,      // 免费版
        PRO,       // 专业版
        ENTERPRISE // 企业版
    }

    public enum OrganizationStatus {
        ACTIVE,    // 活跃
        SUSPENDED, // 暂停
        DELETED    // 已删除
    }

    // 业务方法
    public boolean canCreateGame() {
        return games == null || games.size() < maxGames;
    }

    public boolean isEnterprise() {
        return tier == OrganizationTier.ENTERPRISE;
    }

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE && deletedAt == null;
    }
}
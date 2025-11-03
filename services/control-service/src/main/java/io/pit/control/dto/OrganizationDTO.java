package io.pit.control.dto;

import io.pit.control.jpa.OrganizationEntity;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

/**
 * 组织数据传输对象
 */
public class OrganizationDTO {

    public String id;

    @NotBlank(message = "组织名称不能为空")
    @Size(max = 100, message = "组织名称不能超过100个字符")
    public String name;

    @Size(max = 200, message = "显示名称不能超过200个字符")
    public String displayName;

    @Size(max = 500, message = "描述不能超过500个字符")
    public String description;

    public OrganizationEntity.OrganizationTier tier;
    public OrganizationEntity.OrganizationStatus status;

    // 联系信息
    @Email(message = "联系邮箱格式不正确")
    public String contactEmail;

    @Pattern(regexp = "^[+]?[0-9\\s-()]+$", message = "联系电话格式不正确")
    public String contactPhone;

    // 配额信息
    @Min(value = 1, message = "最大游戏数量至少为1")
    public Integer maxGames;

    @Min(value = 1000, message = "月事件数量至少为1000")
    public Long maxEventsPerMonth;

    @Min(value = 30, message = "数据保留天数至少为30天")
    public Integer maxDataRetentionDays;

    // 计费信息
    @Email(message = "计费邮箱格式不正确")
    public String billingEmail;

    public String paymentMethod;

    // 地理和合规
    public String region;
    public String dataResidency;
    public String complianceRequirements;

    // 统计信息（只读）
    public Integer totalGames;
    public Integer liveGames;
    public Integer totalUsers;
    public Integer totalApiKeys;
    public Long totalEventsProcessed;

    // 时间戳
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    // 构造函数
    public OrganizationDTO() {}

    public OrganizationDTO(OrganizationEntity entity) {
        this.id = entity.id;
        this.name = entity.name;
        this.displayName = entity.displayName;
        this.description = entity.description;
        this.tier = entity.tier;
        this.status = entity.status;
        this.contactEmail = entity.contactEmail;
        this.contactPhone = entity.contactPhone;
        this.maxGames = entity.maxGames;
        this.maxEventsPerMonth = entity.maxEventsPerMonth;
        this.maxDataRetentionDays = entity.maxDataRetentionDays;
        this.billingEmail = entity.billingEmail;
        this.paymentMethod = entity.paymentMethod;
        this.region = entity.region;
        this.dataResidency = entity.dataResidency;
        this.complianceRequirements = entity.complianceRequirements;
        this.createdAt = entity.createdAt;
        this.updatedAt = entity.updatedAt;
    }

    /**
     * 转换为实体对象
     */
    public OrganizationEntity toEntity() {
        OrganizationEntity entity = new OrganizationEntity();
        entity.id = this.id;
        entity.name = this.name;
        entity.displayName = this.displayName;
        entity.description = this.description;
        entity.tier = this.tier != null ? this.tier : OrganizationEntity.OrganizationTier.FREE;
        entity.status = this.status != null ? this.status : OrganizationEntity.OrganizationStatus.ACTIVE;
        entity.contactEmail = this.contactEmail;
        entity.contactPhone = this.contactPhone;
        entity.maxGames = this.maxGames;
        entity.maxEventsPerMonth = this.maxEventsPerMonth;
        entity.maxDataRetentionDays = this.maxDataRetentionDays;
        entity.billingEmail = this.billingEmail;
        entity.paymentMethod = this.paymentMethod;
        entity.region = this.region;
        entity.dataResidency = this.dataResidency;
        entity.complianceRequirements = this.complianceRequirements;
        return entity;
    }

    /**
     * 更新实体对象
     */
    public void updateEntity(OrganizationEntity entity) {
        if (this.name != null) entity.name = this.name;
        if (this.displayName != null) entity.displayName = this.displayName;
        if (this.description != null) entity.description = this.description;
        if (this.tier != null) entity.tier = this.tier;
        if (this.status != null) entity.status = this.status;
        if (this.contactEmail != null) entity.contactEmail = this.contactEmail;
        if (this.contactPhone != null) entity.contactPhone = this.contactPhone;
        if (this.maxGames != null) entity.maxGames = this.maxGames;
        if (this.maxEventsPerMonth != null) entity.maxEventsPerMonth = this.maxEventsPerMonth;
        if (this.maxDataRetentionDays != null) entity.maxDataRetentionDays = this.maxDataRetentionDays;
        if (this.billingEmail != null) entity.billingEmail = this.billingEmail;
        if (this.paymentMethod != null) entity.paymentMethod = this.paymentMethod;
        if (this.region != null) entity.region = this.region;
        if (this.dataResidency != null) entity.dataResidency = this.dataResidency;
        if (this.complianceRequirements != null) entity.complianceRequirements = this.complianceRequirements;
    }

    // 业务方法
    public boolean isEnterprise() {
        return tier == OrganizationEntity.OrganizationTier.ENTERPRISE;
    }

    public boolean isActive() {
        return status == OrganizationEntity.OrganizationStatus.ACTIVE;
    }

    public String getDisplayName() {
        return displayName != null && !displayName.trim().isEmpty() ? displayName : name;
    }
}
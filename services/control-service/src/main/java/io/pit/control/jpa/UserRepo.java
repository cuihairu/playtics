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
 * 用户数据访问接口
 */
@Repository
public interface UserRepo extends JpaRepository<UserEntity, String> {

    /**
     * 根据邮箱查找用户
     */
    Optional<UserEntity> findByEmailAndDeletedAtIsNull(String email);

    /**
     * 根据组织ID查找用户
     */
    List<UserEntity> findByOrgIdAndDeletedAtIsNull(String orgId);

    /**
     * 根据组织ID查找用户（分页）
     */
    Page<UserEntity> findByOrgIdAndDeletedAtIsNull(String orgId, Pageable pageable);

    /**
     * 根据全局角色查找用户
     */
    List<UserEntity> findByGlobalRoleAndDeletedAtIsNull(UserEntity.GlobalRole globalRole);

    /**
     * 根据状态查找用户
     */
    List<UserEntity> findByStatusAndDeletedAtIsNull(UserEntity.UserStatus status);

    /**
     * 搜索用户（根据名称或邮箱）
     */
    @Query("SELECT u FROM UserEntity u WHERE " +
           "(LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "u.deletedAt IS NULL")
    Page<UserEntity> searchUsers(@Param("query") String query, Pageable pageable);

    /**
     * 在指定组织内搜索用户
     */
    @Query("SELECT u FROM UserEntity u WHERE u.orgId = :orgId AND " +
           "(LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "u.deletedAt IS NULL")
    Page<UserEntity> searchUsersInOrg(@Param("orgId") String orgId, @Param("query") String query, Pageable pageable);

    /**
     * 查找已验证邮箱的用户
     */
    List<UserEntity> findByEmailVerifiedTrueAndDeletedAtIsNull();

    /**
     * 查找启用2FA的用户
     */
    List<UserEntity> findByTwoFactorEnabledTrueAndDeletedAtIsNull();

    /**
     * 查找被锁定的用户
     */
    @Query("SELECT u FROM UserEntity u WHERE u.lockedUntil IS NOT NULL AND u.lockedUntil > :now AND u.deletedAt IS NULL")
    List<UserEntity> findLockedUsers(@Param("now") LocalDateTime now);

    /**
     * 查找长时间未登录的用户
     */
    @Query("SELECT u FROM UserEntity u WHERE (u.lastLogin IS NULL OR u.lastLogin < :beforeDate) AND u.deletedAt IS NULL")
    List<UserEntity> findInactiveUsers(@Param("beforeDate") LocalDateTime beforeDate);

    /**
     * 根据邮箱验证令牌查找用户
     */
    Optional<UserEntity> findByEmailVerificationTokenAndDeletedAtIsNull(String token);

    /**
     * 根据密码重置令牌查找用户
     */
    Optional<UserEntity> findByPasswordResetTokenAndDeletedAtIsNull(String token);

    /**
     * 统计组织的用户数量
     */
    long countByOrgIdAndDeletedAtIsNull(String orgId);

    /**
     * 统计活跃用户数量
     */
    long countByStatusAndDeletedAtIsNull(UserEntity.UserStatus status);
}
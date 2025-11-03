-- Pit Control Service 数据库迁移脚本
-- 从简单项目模型升级到企业级多租户架构
-- 版本: v0.2.0
-- 兼容: PostgreSQL 12+ / H2 (开发环境)

-- ==========================================
-- 第一阶段：创建新的多租户表结构
-- ==========================================

-- 1. 组织表 (新增)
CREATE TABLE IF NOT EXISTS organizations (
    id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200),
    description VARCHAR(500),
    tier VARCHAR(20) NOT NULL DEFAULT 'FREE',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- 联系信息
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),

    -- 配额限制
    max_games INTEGER DEFAULT 5,
    max_events_per_month BIGINT DEFAULT 1000000,
    max_data_retention_days INTEGER DEFAULT 90,

    -- 计费信息
    billing_email VARCHAR(255),
    payment_method VARCHAR(50),

    -- 地理和合规
    region VARCHAR(10),
    data_residency VARCHAR(20),
    compliance_requirements TEXT,

    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

-- 2. 游戏表 (扩展现有projects表)
CREATE TABLE IF NOT EXISTS games (
    id VARCHAR(32) PRIMARY KEY,
    org_id VARCHAR(32) NOT NULL,
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(200),
    description TEXT,
    genre VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'DEVELOPMENT',

    -- 版本信息
    current_version VARCHAR(20),
    min_supported_version VARCHAR(20),
    release_date TIMESTAMP,

    -- 应用商店链接
    app_store_url VARCHAR(500),
    google_play_url VARCHAR(500),
    steam_url VARCHAR(500),

    -- 游戏专业配置
    default_currency VARCHAR(10) DEFAULT 'USD',
    virtual_currencies TEXT,
    max_level INTEGER,
    has_multiplayer BOOLEAN DEFAULT FALSE,
    has_guilds BOOLEAN DEFAULT FALSE,
    has_pvp BOOLEAN DEFAULT FALSE,

    -- 数据配置
    data_retention_days INTEGER DEFAULT 90,
    enable_real_time_analytics BOOLEAN DEFAULT TRUE,
    enable_crash_reporting BOOLEAN DEFAULT TRUE,
    sample_rate DECIMAL(3,2) DEFAULT 1.0,

    -- 隐私合规
    pii_detection_enabled BOOLEAN DEFAULT TRUE,
    gdpr_compliance BOOLEAN DEFAULT FALSE,
    coppa_compliance BOOLEAN DEFAULT FALSE,

    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,

    -- 外键约束
    CONSTRAINT fk_games_org FOREIGN KEY (org_id) REFERENCES organizations(id)
);

-- 3. 游戏环境表 (新增)
CREATE TABLE IF NOT EXISTS game_environments (
    id VARCHAR(32) PRIMARY KEY,
    game_id VARCHAR(32) NOT NULL,
    name VARCHAR(50) NOT NULL,
    display_name VARCHAR(100),
    description VARCHAR(500),
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- 环境配置
    api_endpoint VARCHAR(500),
    data_namespace VARCHAR(100),
    kafka_topic_prefix VARCHAR(50),

    -- 数据隔离配置
    isolated_storage BOOLEAN DEFAULT FALSE,
    data_retention_days INTEGER,
    max_events_per_day BIGINT,

    -- 功能开关
    enable_debug_mode BOOLEAN DEFAULT FALSE,
    enable_sampling BOOLEAN DEFAULT TRUE,
    sample_rate DECIMAL(3,2) DEFAULT 1.0,
    enable_real_time BOOLEAN DEFAULT TRUE,

    -- 安全配置
    require_https BOOLEAN DEFAULT TRUE,
    allowed_origins TEXT,
    ip_whitelist TEXT,

    -- 监控配置
    enable_alerts BOOLEAN DEFAULT TRUE,
    alert_email VARCHAR(255),
    error_threshold DECIMAL(3,2) DEFAULT 0.05,

    -- 版本控制
    schema_version VARCHAR(20),
    config_version VARCHAR(20),

    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,

    -- 外键约束
    CONSTRAINT fk_game_environments_game FOREIGN KEY (game_id) REFERENCES games(id),

    -- 唯一约束
    CONSTRAINT uk_game_environment_name UNIQUE (game_id, name)
);

-- 4. 用户表 (新增)
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(32) PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(100),
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),

    -- 认证信息
    password_hash VARCHAR(255),
    email_verified BOOLEAN DEFAULT FALSE,
    email_verification_token VARCHAR(255),
    password_reset_token VARCHAR(255),
    password_reset_expires TIMESTAMP,

    -- 组织关联
    org_id VARCHAR(32),
    global_role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    -- 个人信息
    company VARCHAR(100),
    title VARCHAR(50),
    phone VARCHAR(20),
    time_zone VARCHAR(50) DEFAULT 'UTC',
    locale VARCHAR(10) DEFAULT 'en-US',

    -- 偏好设置
    notification_email BOOLEAN DEFAULT TRUE,
    notification_sms BOOLEAN DEFAULT FALSE,
    dashboard_theme VARCHAR(20) DEFAULT 'light',

    -- 安全设置
    two_factor_enabled BOOLEAN DEFAULT FALSE,
    two_factor_secret VARCHAR(32),
    login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    last_login TIMESTAMP,
    last_login_ip VARCHAR(45),

    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,

    -- 外键约束
    CONSTRAINT fk_users_org FOREIGN KEY (org_id) REFERENCES organizations(id)
);

-- 5. 用户角色关联表 (新增)
CREATE TABLE IF NOT EXISTS user_roles (
    id VARCHAR(32) PRIMARY KEY,
    user_id VARCHAR(32) NOT NULL,
    org_id VARCHAR(32),
    game_id VARCHAR(32),
    environment_id VARCHAR(32),
    role VARCHAR(20) NOT NULL,
    scope VARCHAR(20) NOT NULL,

    -- 权限配置
    permissions TEXT,

    -- 邀请信息
    invited_by VARCHAR(32),
    invitation_accepted BOOLEAN DEFAULT FALSE,
    invitation_token VARCHAR(255),
    invitation_expires TIMESTAMP,

    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,

    -- 外键约束
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_roles_org FOREIGN KEY (org_id) REFERENCES organizations(id),
    CONSTRAINT fk_user_roles_game FOREIGN KEY (game_id) REFERENCES games(id),
    CONSTRAINT fk_user_roles_environment FOREIGN KEY (environment_id) REFERENCES game_environments(id)
);

-- 6. 用户邀请表 (新增)
CREATE TABLE IF NOT EXISTS user_invitations (
    id VARCHAR(32) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    inviter_id VARCHAR(32) NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,

    -- 权限范围
    org_id VARCHAR(32),
    game_id VARCHAR(32),
    environment_id VARCHAR(32),
    role VARCHAR(20) NOT NULL,
    scope VARCHAR(20) NOT NULL,

    -- 邀请状态
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    message VARCHAR(500),
    subject VARCHAR(200),

    -- 时间控制
    expires_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    rejected_at TIMESTAMP,
    created_user_id VARCHAR(32),

    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 外键约束
    CONSTRAINT fk_user_invitations_inviter FOREIGN KEY (inviter_id) REFERENCES users(id),
    CONSTRAINT fk_user_invitations_org FOREIGN KEY (org_id) REFERENCES organizations(id),
    CONSTRAINT fk_user_invitations_game FOREIGN KEY (game_id) REFERENCES games(id),
    CONSTRAINT fk_user_invitations_user FOREIGN KEY (created_user_id) REFERENCES users(id)
);

-- ==========================================
-- 第二阶段：扩展现有API Keys表
-- ==========================================

-- 备份现有api_keys表数据
CREATE TABLE IF NOT EXISTS api_keys_backup AS SELECT * FROM api_keys;

-- 为api_keys表添加新字段（保持向后兼容）
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS org_id VARCHAR(32);
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS game_id VARCHAR(32);
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS environment_id VARCHAR(32);
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS description VARCHAR(500);
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS key_type VARCHAR(20) DEFAULT 'PRODUCTION';
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';

-- 配额和限制
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS daily_quota BIGINT;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS monthly_quota BIGINT;

-- 安全配置
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS require_hmac BOOLEAN DEFAULT FALSE;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS allowed_origins TEXT;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS ip_whitelist TEXT;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS user_agent_whitelist TEXT;

-- 功能权限
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS can_write BOOLEAN DEFAULT TRUE;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS can_read BOOLEAN DEFAULT FALSE;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS can_export BOOLEAN DEFAULT FALSE;

-- 使用统计
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS total_requests BIGINT DEFAULT 0;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS total_events BIGINT DEFAULT 0;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMP;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS last_used_ip VARCHAR(45);

-- 过期控制
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS auto_rotate BOOLEAN DEFAULT FALSE;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS rotation_days INTEGER DEFAULT 90;

-- 创建信息
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS created_by VARCHAR(32);
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE api_keys ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP;

-- ==========================================
-- 第三阶段：创建索引优化性能
-- ==========================================

-- 组织表索引
CREATE INDEX IF NOT EXISTS idx_organizations_tier ON organizations(tier);
CREATE INDEX IF NOT EXISTS idx_organizations_status ON organizations(status);
CREATE INDEX IF NOT EXISTS idx_organizations_created_at ON organizations(created_at);

-- 游戏表索引
CREATE INDEX IF NOT EXISTS idx_games_org_id ON games(org_id);
CREATE INDEX IF NOT EXISTS idx_games_status ON games(status);
CREATE INDEX IF NOT EXISTS idx_games_genre ON games(genre);
CREATE INDEX IF NOT EXISTS idx_games_created_at ON games(created_at);

-- 环境表索引
CREATE INDEX IF NOT EXISTS idx_game_environments_game_id ON game_environments(game_id);
CREATE INDEX IF NOT EXISTS idx_game_environments_type ON game_environments(type);
CREATE INDEX IF NOT EXISTS idx_game_environments_status ON game_environments(status);

-- 用户表索引
CREATE INDEX IF NOT EXISTS idx_users_org_id ON users(org_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_global_role ON users(global_role);
CREATE INDEX IF NOT EXISTS idx_users_last_login ON users(last_login);

-- 用户角色表索引
CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_org_id ON user_roles(org_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_game_id ON user_roles(game_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role);
CREATE INDEX IF NOT EXISTS idx_user_roles_scope ON user_roles(scope);

-- 邀请表索引
CREATE INDEX IF NOT EXISTS idx_user_invitations_email ON user_invitations(email);
CREATE INDEX IF NOT EXISTS idx_user_invitations_token ON user_invitations(token);
CREATE INDEX IF NOT EXISTS idx_user_invitations_status ON user_invitations(status);
CREATE INDEX IF NOT EXISTS idx_user_invitations_expires_at ON user_invitations(expires_at);

-- API Keys表索引
CREATE INDEX IF NOT EXISTS idx_api_keys_org_id ON api_keys(org_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_game_id ON api_keys(game_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_environment_id ON api_keys(environment_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_status ON api_keys(status);
CREATE INDEX IF NOT EXISTS idx_api_keys_created_by ON api_keys(created_by);
CREATE INDEX IF NOT EXISTS idx_api_keys_last_used_at ON api_keys(last_used_at);

-- ==========================================
-- 第四阶段：数据迁移和初始化
-- ==========================================

-- 创建默认超级管理员组织
INSERT INTO organizations (id, name, display_name, tier, status, max_games, max_events_per_month, max_data_retention_days)
VALUES ('org_system', 'System', 'System Organization', 'ENTERPRISE', 'ACTIVE', 1000, -1, 1095)
ON CONFLICT (id) DO NOTHING;

-- 迁移现有projects到games表
INSERT INTO games (id, org_id, name, display_name, created_at)
SELECT p.id, 'org_system', p.name, p.name, CURRENT_TIMESTAMP
FROM projects p
WHERE NOT EXISTS (SELECT 1 FROM games g WHERE g.id = p.id);

-- 为每个游戏创建默认生产环境
INSERT INTO game_environments (id, game_id, name, display_name, type, status)
SELECT
    'env_' || g.id || '_prod',
    g.id,
    'production',
    'Production Environment',
    'PRODUCTION',
    'ACTIVE'
FROM games g
WHERE NOT EXISTS (
    SELECT 1 FROM game_environments ge
    WHERE ge.game_id = g.id AND ge.name = 'production'
);

-- 更新现有API Keys关联到新的多租户结构
UPDATE api_keys
SET
    org_id = 'org_system',
    game_id = project_id,
    environment_id = (
        SELECT id FROM game_environments
        WHERE game_id = api_keys.project_id
        AND name = 'production'
        LIMIT 1
    )
WHERE org_id IS NULL AND project_id IS NOT NULL;

-- 创建默认超级管理员用户 (仅开发环境)
INSERT INTO users (id, email, name, global_role, org_id, status, email_verified)
VALUES ('user_admin', 'admin@pit.local', 'System Admin', 'SUPER_ADMIN', 'org_system', 'ACTIVE', TRUE)
ON CONFLICT (email) DO NOTHING;

-- ==========================================
-- 第五阶段：创建视图和存储过程
-- ==========================================

-- 组织游戏统计视图
CREATE OR REPLACE VIEW v_organization_stats AS
SELECT
    o.id as org_id,
    o.name as org_name,
    o.tier,
    COUNT(g.id) as total_games,
    COUNT(CASE WHEN g.status = 'LIVE' THEN 1 END) as live_games,
    COUNT(u.id) as total_users,
    COUNT(ak.api_key) as total_api_keys,
    COALESCE(SUM(ak.total_events), 0) as total_events_processed
FROM organizations o
LEFT JOIN games g ON o.id = g.org_id AND g.deleted_at IS NULL
LEFT JOIN users u ON o.id = u.org_id AND u.deleted_at IS NULL
LEFT JOIN api_keys ak ON o.id = ak.org_id
WHERE o.deleted_at IS NULL
GROUP BY o.id, o.name, o.tier;

-- 用户权限汇总视图
CREATE OR REPLACE VIEW v_user_permissions AS
SELECT
    u.id as user_id,
    u.email,
    u.name,
    u.global_role,
    ur.org_id,
    ur.game_id,
    ur.environment_id,
    ur.role,
    ur.scope,
    o.name as org_name,
    g.name as game_name
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN organizations o ON ur.org_id = o.id
LEFT JOIN games g ON ur.game_id = g.id
WHERE u.deleted_at IS NULL;

-- ==========================================
-- 完成迁移
-- ==========================================

-- 更新数据库版本标记
CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(20) PRIMARY KEY,
    description TEXT,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schema_migrations (version, description)
VALUES ('v0.2.0', 'Multi-tenant architecture upgrade')
ON CONFLICT (version) DO UPDATE SET applied_at = CURRENT_TIMESTAMP;

-- 提交事务
COMMIT;
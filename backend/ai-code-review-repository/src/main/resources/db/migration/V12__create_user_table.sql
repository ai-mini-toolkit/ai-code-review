-- V12__create_user_table.sql
-- 创建用户表（用于系统认证和授权）

CREATE TABLE IF NOT EXISTS "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,  -- BCrypt hash ($2a$10$...)
    email VARCHAR(100) NOT NULL UNIQUE,
    real_name VARCHAR(100),
    avatar VARCHAR(255),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',  -- ADMIN, USER
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_user_role CHECK (role IN ('ADMIN', 'USER'))
);

-- 创建索引（优化查询性能）
CREATE INDEX idx_user_username ON "user"(username);
CREATE INDEX idx_user_email ON "user"(email);
CREATE INDEX idx_user_role ON "user"(role);
CREATE INDEX idx_user_enabled ON "user"(enabled);

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_user_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER user_updated_at_trigger
BEFORE UPDATE ON "user"
FOR EACH ROW
EXECUTE FUNCTION update_user_updated_at();

COMMENT ON TABLE "user" IS '系统用户表';
COMMENT ON COLUMN "user".id IS '用户 ID（主键）';
COMMENT ON COLUMN "user".username IS '用户名（唯一）';
COMMENT ON COLUMN "user".password_hash IS 'BCrypt 加密密码';
COMMENT ON COLUMN "user".email IS '邮箱（唯一）';
COMMENT ON COLUMN "user".real_name IS '真实姓名';
COMMENT ON COLUMN "user".avatar IS '头像 URL';
COMMENT ON COLUMN "user".role IS '用户角色：ADMIN（管理员）、USER（普通用户）';
COMMENT ON COLUMN "user".enabled IS '是否启用';

-- V13__insert_default_admin.sql
-- 插入默认管理员用户

-- 默认管理员账号
-- 用户名: admin
-- 密码: admin123 (BCrypt hash, strength=10)
INSERT INTO "user" (username, password_hash, email, real_name, role, enabled)
VALUES (
    'admin',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
    'admin@aicodereview.com',
    'System Administrator',
    'ADMIN',
    true
)
ON CONFLICT (username) DO NOTHING;  -- 如果已存在则跳过

COMMENT ON TABLE "user" IS '默认管理员账号: admin / admin123';

-- V6__seed_admin_user.sql
-- LinkForge — Seed initial admin user
-- Password: Admin@LinkForge123! (BCrypt hash) — CHANGE IN PRODUCTION!

INSERT INTO users (id, email, password, first_name, last_name, role, email_verified, enabled, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'admin@linkforge.io',
    '$2a$12$LWq3dIVlh52UHdCBd7kxOeAXqxDDm1/GDm4KVhZZ4XUwZY2pPYqFe',
    'Link',
    'Admin',
    'ADMIN',
    TRUE,
    TRUE,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

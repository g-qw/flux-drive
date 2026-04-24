-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 触发器，自动更新 updated_at 字段
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = TRUNC(EXTRACT(EPOCH FROM NOW()) * 1000);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 用户表 --
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(64) UNIQUE NOT NULL,
    pwd VARCHAR(255) NOT NULL,
    root_dir UUID,
    created_at BIGINT NOT NULL DEFAULT TRUNC(EXTRACT(EPOCH FROM NOW()) * 1000),
    last_login_at BIGINT,
    user_status VARCHAR(32) NOT NULL,
    user_role VARCHAR(32) NOT NULL,
    storage_capacity BIGINT NOT NULL DEFAULT 5368709120,
    used_capacity BIGINT NOT NULL DEFAULT 0,
    bio TEXT,
    avatar VARCHAR(255)
);

-- 表注释
COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.id IS '用户ID';
COMMENT ON COLUMN users.email IS '邮箱地址';
COMMENT ON COLUMN users.username IS '用户名';
COMMENT ON COLUMN users.pwd IS '密码哈希，存储哈希值和盐值的组合';
COMMENT ON COLUMN users.root_dir IS '用户的文件系统的根目录ID';
COMMENT ON COLUMN users.created_at IS '账户创建时间';
COMMENT ON COLUMN users.last_login_at IS '最后登录时间';
COMMENT ON COLUMN users.user_status IS '用户状态';
COMMENT ON COLUMN users.user_role IS '权限级别，如普通用户、管理员等';
COMMENT ON COLUMN users.storage_capacity IS '总存储容量配额，单位字节，默认5GB';
COMMENT ON COLUMN users.used_capacity IS '已使用存储空间，单位字节';
COMMENT ON COLUMN users.bio IS '个人简介';
COMMENT ON COLUMN users.avatar IS '头像URL地址';

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_username ON users(username);

-- 文件系统 --
CREATE TABLE directory (
    id         UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id  UUID REFERENCES directory(id),
    user_id    UUID REFERENCES users(id),
    name       VARCHAR(1024) NOT NULL,
    deleted_at BIGINT DEFAULT NULL,
    created_at BIGINT NOT NULL DEFAULT TRUNC(EXTRACT(EPOCH FROM NOW()) * 1000),
    updated_at BIGINT NOT NULL DEFAULT TRUNC(EXTRACT(EPOCH FROM NOW()) * 1000),
    size       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_directory_name UNIQUE (parent_id, name)
);
COMMENT ON TABLE  directory               IS '目录表';
COMMENT ON COLUMN directory.id            IS '目录 ID';
COMMENT ON COLUMN directory.parent_id     IS '父目录 ID';
COMMENT ON COLUMN directory.user_id       IS '目录所属用户的 ID';
COMMENT ON COLUMN directory.name          IS '目录名称';
COMMENT ON COLUMN directory.deleted_at    IS '目录删除时间';
COMMENT ON COLUMN directory.created_at    IS '目录的创建时间';
COMMENT ON COLUMN directory.updated_at    IS '目录的修改时间';
COMMENT ON COLUMN directory.size          IS '目录下的子目录和子文件的总存储空间占用大小，单位: 字节';

-- 目录表索引
CREATE INDEX idx_directory_parent_id ON directory(parent_id);
CREATE INDEX idx_directory_user_id   ON directory(user_id);
CREATE INDEX idx_directory_name      ON directory(name);

-- 目录表触发器
CREATE TRIGGER update_directory_updated_at
    BEFORE UPDATE ON directory
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE file (
    id           UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    directory_id UUID REFERENCES directory(id),
    user_id      UUID REFERENCES users(id),
    bucket       VARCHAR(32)  NOT NULL,
    storage_key  VARCHAR(1024) NOT NULL,
    name         VARCHAR(1024) NOT NULL,
    mime_type    VARCHAR(127) NOT NULL,
    size         BIGINT NOT NULL,
    md5          VARCHAR(32) NOT NULL,
    deleted_at   BIGINT DEFAULT NULL,
    created_at   BIGINT NOT NULL DEFAULT TRUNC(EXTRACT(EPOCH FROM NOW()) * 1000),
    updated_at   BIGINT NOT NULL DEFAULT TRUNC(EXTRACT(EPOCH FROM NOW()) * 1000),
    CONSTRAINT uk_file_user_dir_filename UNIQUE (directory_id, name)
);
COMMENT ON TABLE  file IS '文件表';
COMMENT ON COLUMN file.id      IS '文件 ID';
COMMENT ON COLUMN file.directory_id IS '文件所在目录的 ID';
COMMENT ON COLUMN file.user_id      IS '文件所属用户的ID';
COMMENT ON COLUMN file.bucket       IS '存储该文件的 Minio 存储桶';
COMMENT ON COLUMN file.storage_key  IS '文件在 Minio 的固定扁平存储键，在首次创建文件时按特定规则分配';
COMMENT ON COLUMN file.name         IS '文件名称，包括文件扩展名';
COMMENT ON COLUMN file.mime_type    IS '文件类型';
COMMENT ON COLUMN file.size         IS '文件大小，单位: 字节';
COMMENT ON COLUMN file.md5          IS '文件的MD5哈希值';
COMMENT ON COLUMN file.deleted_at   IS '文件删除时间';
COMMENT ON COLUMN file.created_at   IS '文件的创建时间';
COMMENT ON COLUMN file.updated_at   IS '文件的修改时间';

-- 文件表索引
CREATE INDEX idx_file_directory_id ON file(directory_id);
CREATE INDEX idx_file_user_id      ON file(user_id);
CREATE INDEX idx_file_storage_key  ON file(storage_key);
CREATE INDEX idx_file_filename     ON file(name);

-- 文件表触发器
CREATE TRIGGER update_file_updated_at
    BEFORE UPDATE ON file
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE file_processing_task (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    file_id         UUID NOT NULL,
    user_id         UUID NOT NULL,
    md5             VARCHAR(32) NOT NULL,
    bucket          VARCHAR(32) NOT NULL,
    storage_key     VARCHAR(1024) NOT NULL,
    task_type       VARCHAR(32) NOT NULL,
    processed       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      BIGINT NOT NULL DEFAULT TRUNC(EXTRACT(EPOCH FROM NOW()) * 1000),
    updated_at      BIGINT NOT NULL DEFAULT TRUNC(EXTRACT(EPOCH FROM NOW()) * 1000)
);

COMMENT ON TABLE   file_processing_task IS '媒体文件待处理队列，用于封面生成、转码等异步任务';
COMMENT ON COLUMN   file_processing_task.file_id IS '待处理文件 ID';
COMMENT ON COLUMN   file_processing_task.user_id IS '文件所属用户的ID';
COMMENT ON COLUMN   file_processing_task.md5 IS '待处理文件的MD5哈希值';
COMMENT ON COLUMN   file_processing_task.bucket IS '待处理文件所在的 MinIO bucket';
COMMENT ON COLUMN   file_processing_task.storage_key IS '待处理文件在 MinIO 中的存储路径';
COMMENT ON COLUMN   file_processing_task.task_type IS '任务类型';
COMMENT ON COLUMN   file_processing_task.processed IS '是否已处理完成：FALSE-待处理，TRUE-已完成';
COMMENT ON COLUMN   file_processing_task.created_at IS '创建时间戳(毫秒)';
COMMENT ON COLUMN   file_processing_task.updated_at IS '更新时间戳(毫秒)';

CREATE INDEX idx_file_processing_task_processed ON file_processing_task(processed);

CREATE TRIGGER update_file_processing_task_updated_at
    BEFORE UPDATE ON file_processing_task
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE media_cover (
        id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        file_id         UUID NOT NULL,
        user_id         UUID NOT NULL,
        md5             VARCHAR(32) NOT NULL,
        bucket          VARCHAR(32)  NOT NULL,
        storage_key     VARCHAR(1024) NOT NULL,
        size            BIGINT,
        width           INT NOT NULL,
        height          INT NOT NULL,
        created_at      BIGINT NOT NULL DEFAULT TRUNC(EXTRACT(EPOCH FROM NOW()) * 1000),
        updated_at      BIGINT NOT NULL DEFAULT TRUNC(EXTRACT(EPOCH FROM NOW()) * 1000)
);
COMMENT ON TABLE media_cover IS '媒体文件缩略图/封面，用于图片预览和视频封面';
COMMENT ON COLUMN media_cover.id IS '封面id';
COMMENT ON COLUMN media_cover.file_id IS '关联的源文件 ID';
COMMENT ON COLUMN media_cover.user_id IS '关联的源文件的所属用户的ID';
COMMENT ON COLUMN media_cover.md5 IS '关联文件的MD5哈希值';
COMMENT ON COLUMN media_cover.bucket IS '封面文件所在的 MinIO bucket';
COMMENT ON COLUMN media_cover.storage_key IS '在 MinIO 中的存储路径';
COMMENT ON COLUMN media_cover.size IS '文件大小(字节)';
COMMENT ON COLUMN media_cover.width IS '宽度(像素)';
COMMENT ON COLUMN media_cover.height IS '高度(像素)';

CREATE INDEX idx_media_cover_user_id ON media_cover(user_id);
CREATE INDEX idx_media_cover_md5 ON media_cover(md5);

CREATE TRIGGER update_media_cover_updated_at
    BEFORE UPDATE ON media_cover
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE resource_share (
    id                  UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id            UUID NOT NULL,
    code                VARCHAR(32) NOT NULL UNIQUE,
    access_type         VARCHAR(16) NOT NULL DEFAULT 'public',
    expire_at           BIGINT,
    require_password    BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash       VARCHAR(255),
    status              VARCHAR(32) NOT NULL DEFAULT 'active',
    title               VARCHAR(255) NOT NULL,
    description         VARCHAR(100) NOT NULL,
    created_at          BIGINT NOT NULL DEFAULT TRUNC(EXTRACT(EPOCH FROM NOW()) * 1000),
    updated_at          BIGINT NOT NULL DEFAULT TRUNC(EXTRACT(EPOCH FROM NOW()) * 1000)
);
COMMENT ON TABLE resource_share IS '资源分享表';
COMMENT ON COLUMN resource_share.id IS '分享链接唯一标识UUID';
COMMENT ON COLUMN resource_share.owner_id IS '分享创建者用户ID，关联users表';
COMMENT ON COLUMN resource_share.code IS '分享短码，用于URL访问，全局唯一';
COMMENT ON COLUMN resource_share.expire_at IS '分享过期时间戳（毫秒），NULL表示永不过期';
COMMENT ON COLUMN resource_share.access_type IS '访问类型：public(公开)/private(白名单)';
COMMENT ON COLUMN resource_share.require_password IS '是否需要密码访问';
COMMENT ON COLUMN resource_share.password_hash IS '密码哈希值，bcrypt加密存储';
COMMENT ON COLUMN resource_share.status IS '分享状态：active(有效)/expired(过期)/revoked(撤销)/disabled(禁用)';
COMMENT ON COLUMN resource_share.title IS '分享标题';
COMMENT ON COLUMN resource_share.description IS '分享描述信息';
COMMENT ON COLUMN resource_share.created_at IS '创建时间戳（毫秒）';
COMMENT ON COLUMN resource_share.updated_at IS '最后更新时间戳（毫秒）';

CREATE INDEX idx_share_owner_id ON resource_share(owner_id);
CREATE INDEX idx_share_code ON resource_share(code);

CREATE TABLE share_item (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    share_id        UUID NOT NULL REFERENCES resource_share(id) ON DELETE CASCADE,
    resource_type   VARCHAR(32) NOT NULL CHECK (resource_type IN ('FILE', 'DIRECTORY')),
    resource_id     UUID NOT NULL
);
COMMENT ON TABLE share_item IS '分享资源项表：存储分享链接包含的具体文件或目录';
COMMENT ON COLUMN share_item.id IS '自增主键ID';
COMMENT ON COLUMN share_item.share_id IS '关联的分享链接ID';
COMMENT ON COLUMN share_item.resource_type IS '资源类型：file/dir';
COMMENT ON COLUMN share_item.resource_id IS '资源 id';

CREATE INDEX idx_share_item_share_id ON share_item(share_id);

CREATE TABLE share_access (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    share_id        UUID NOT NULL REFERENCES resource_share(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL,
    viewed          BOOLEAN NOT NULL DEFAULT FALSE
);
COMMENT ON TABLE share_access IS '分享访问权限表：存储特定用户对分享链接的访问授权';
COMMENT ON COLUMN share_access.id IS '自增主键ID';
COMMENT ON COLUMN share_access.share_id IS '关联ID';
COMMENT ON COLUMN share_access.user_id IS '被授权用户ID';

CREATE INDEX share_access_share_id ON share_access(share_id);
-- Báo cáo vi phạm và kiểm duyệt bài đăng

ALTER TABLE posts
    ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL';

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_moderation_status
        CHECK (moderation_status IN ('NORMAL', 'UNDER_REVIEW', 'HIDDEN', 'LOCKED'));

CREATE INDEX idx_posts_moderation_status ON posts(moderation_status);

CREATE TABLE reports (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason VARCHAR(30) NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    resolved_by BIGINT NULL,
    resolution_note VARCHAR(500) NULL,
    CONSTRAINT uk_reports_post_reporter UNIQUE (post_id, reporter_id),
    CONSTRAINT chk_reports_reason CHECK (reason IN ('SPAM', 'INAPPROPRIATE', 'SCAM', 'HARASSMENT', 'OTHER')),
    CONSTRAINT chk_reports_status CHECK (status IN ('PENDING', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT fk_reports_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
    CONSTRAINT fk_reports_resolved_by FOREIGN KEY (resolved_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_reports_status ON reports(status);
CREATE INDEX idx_reports_post ON reports(post_id);

CREATE TABLE moderation_actions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    admin_id BIGINT NULL,
    action VARCHAR(20) NOT NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_moderation_actions_action CHECK (action IN ('HIDE', 'LOCK', 'RESTORE', 'DISMISS', 'AUTO_REVIEW')),
    CONSTRAINT fk_moderation_actions_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    CONSTRAINT fk_moderation_actions_admin FOREIGN KEY (admin_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_moderation_actions_post ON moderation_actions(post_id);

-- post_id không có khóa ngoại để giữ thông báo khi bài đăng bị xóa
CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(500) NOT NULL,
    post_id BIGINT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_notifications_type CHECK (type IN
        ('POST_UNDER_REVIEW', 'POST_HIDDEN', 'POST_LOCKED', 'POST_RESTORED', 'REPORT_DISMISSED')),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_notifications_user_read ON notifications(user_id, is_read);

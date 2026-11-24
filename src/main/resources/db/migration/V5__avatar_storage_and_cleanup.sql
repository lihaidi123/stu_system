CREATE TABLE avatar_asset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  owner_account_id BIGINT NOT NULL UNIQUE,
  storage_provider ENUM('LOCAL','COS') NOT NULL,
  object_key VARCHAR(80) NOT NULL UNIQUE,
  content_type VARCHAR(64) NOT NULL,
  size_bytes INT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_avatar_owner FOREIGN KEY (owner_account_id) REFERENCES iam_account(id) ON DELETE CASCADE,
  CHECK (size_bytes > 0 AND size_bytes <= 2097152)
);

CREATE TABLE avatar_cleanup_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  storage_provider ENUM('LOCAL','COS') NOT NULL,
  object_key VARCHAR(80) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  available_at DATETIME(6) NOT NULL,
  last_error_code VARCHAR(40) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_avatar_cleanup_object (storage_provider,object_key),
  KEY ix_avatar_cleanup_due (available_at)
);

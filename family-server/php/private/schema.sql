-- Dedicated database, MySQL 5.7+, InnoDB, no MySQL-8-only features.
CREATE TABLE IF NOT EXISTS osmon_lock (id INT PRIMARY KEY) ENGINE=InnoDB;
INSERT IGNORE INTO osmon_lock (id) VALUES (1);
CREATE TABLE IF NOT EXISTS osmon_parents (
 token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 expires DOUBLE NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS osmon_devices (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 name VARCHAR(160) NOT NULL,
 owner CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
 code CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL UNIQUE,
 expires DOUBLE NULL, config LONGTEXT NOT NULL, version INT NOT NULL DEFAULT 1,
 applied INT NOT NULL DEFAULT 0, completed LONGTEXT NOT NULL,
 seen DOUBLE NULL, status_day INT NULL,
 location LONGTEXT NULL, location_at DOUBLE NULL,
 location_sharing TINYINT NOT NULL DEFAULT 0, location_request LONGTEXT NULL,
 INDEX(owner), INDEX(location_at), INDEX(expires),
 CONSTRAINT osmon_device_owner FOREIGN KEY(owner) REFERENCES osmon_parents(token) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS osmon_web_codes (
 code CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 expires DOUBLE NOT NULL, INDEX(owner), INDEX(expires),
 CONSTRAINT osmon_code_owner FOREIGN KEY(owner) REFERENCES osmon_parents(token) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS osmon_web_sessions (
 token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 expires DOUBLE NOT NULL, INDEX(owner), INDEX(expires),
 CONSTRAINT osmon_session_owner FOREIGN KEY(owner) REFERENCES osmon_parents(token) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS osmon_rates (
 bucket CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 minute BIGINT NOT NULL, hits INT NOT NULL,
 PRIMARY KEY(bucket,minute), INDEX(minute)
) ENGINE=InnoDB;

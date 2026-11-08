CREATE TABLE IF NOT EXISTS osmon_accounts (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 owner CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL DEFAULT '', verified TINYINT NOT NULL DEFAULT 0,
 failures INT NOT NULL DEFAULT 0, locked_until DOUBLE NOT NULL DEFAULT 0,
 mail_at DOUBLE NOT NULL DEFAULT 0, mail_window DOUBLE NOT NULL DEFAULT 0,
 mail_count INT NOT NULL DEFAULT 0, created DOUBLE NOT NULL,
 CONSTRAINT osmon_account_family FOREIGN KEY(owner) REFERENCES osmon_parents(token) ON DELETE CASCADE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS osmon_account_tokens (
 token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 account_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 purpose VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 expires DOUBLE NOT NULL, INDEX(expires),
 CONSTRAINT osmon_account_token_owner FOREIGN KEY(account_id) REFERENCES osmon_accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB;

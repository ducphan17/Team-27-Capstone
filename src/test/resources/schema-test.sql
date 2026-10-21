-- Test tables only: connect to sentencebuilder_test before running this script.
-- Keep these table definitions aligned with src/main/resources/schema.sql.

CREATE TABLE IF NOT EXISTS source_file (
    file_id        INT AUTO_INCREMENT PRIMARY KEY,
    filename       VARCHAR(255) NOT NULL,
    word_count     INT,
    sentence_count INT,
    imported_at    DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- utf8mb4_bin makes the UNIQUE check exact, so 'café' and 'cafe' are different words.
CREATE TABLE IF NOT EXISTS word (
    word_id     INT AUTO_INCREMENT PRIMARY KEY,
    text        VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL UNIQUE,
    total_count INT DEFAULT 0,
    start_count INT DEFAULT 0,
    end_count   INT DEFAULT 0,
    first_seen  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS word_pair (
    from_word_id INT NOT NULL,
    to_word_id   INT NOT NULL,
    count        INT DEFAULT 0,
    user_count   INT DEFAULT 0,
    PRIMARY KEY (from_word_id, to_word_id),
    FOREIGN KEY (from_word_id) REFERENCES word (word_id),
    FOREIGN KEY (to_word_id) REFERENCES word (word_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS generated_sentence (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    sentence        TEXT,
    start_word      VARCHAR(100),
    algorithm       VARCHAR(50),
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    added_to_corpus BOOLEAN DEFAULT FALSE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

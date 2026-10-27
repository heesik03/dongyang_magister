package com.magister.backend.global.migration;

import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.ChapterData;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 기존 회차의 공개 ID·순서·본문·revision과 검토/사건 참조를 그대로 보존한다. */
public class V9__separate_chapters extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean mysql = connection.getMetaData().getDatabaseProductName().equals("MySQL");
        try (Statement statement = connection.createStatement()) {
            String schema =
                    """
                    CREATE TABLE chapters (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        work_id BIGINT NOT NULL,
                        chapter_key VARCHAR(64) NOT NULL,
                        chapter_no INT NOT NULL,
                        title VARCHAR(100) NOT NULL,
                        content LONGTEXT NOT NULL,
                        revision BIGINT NOT NULL,
                        version BIGINT NOT NULL,
                        mutation_id VARCHAR(64) NOT NULL,
                        created_at TIMESTAMP(6) NOT NULL,
                        updated_at TIMESTAMP(6) NOT NULL,
                        CONSTRAINT fk_chapters_work FOREIGN KEY (work_id) REFERENCES works(id),
                        CONSTRAINT uk_chapters_key UNIQUE(work_id, chapter_key),
                        CONSTRAINT uk_chapters_number UNIQUE(work_id, chapter_no),
                        CONSTRAINT ck_chapter_number CHECK(chapter_no BETWEEN 1 AND 100),
                        CONSTRAINT ck_chapter_revision CHECK(revision >= 1)
                    )
                    """;
            // MySQL 기본 정렬은 대소문자를 합칠 수 있다. 기존 JSON의 서로 다른 ID를 보존한다.
            if (mysql) {
                schema =
                        schema.replace(
                                "chapter_key VARCHAR(64)",
                                "chapter_key VARCHAR(64) CHARACTER SET utf8mb4 COLLATE"
                                    + " utf8mb4_bin");
            }
            statement.execute(schema);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        try (Statement read = connection.createStatement();
                ResultSet rows =
                        read.executeQuery("SELECT work_id,content,updated_at FROM workspaces");
                PreparedStatement insert =
                        connection.prepareStatement(
                                """
                                INSERT INTO chapters(work_id,chapter_key,chapter_no,title,content,revision,
                                    version,mutation_id,created_at,updated_at) VALUES(?,?,?,?,?,?,0,?,?,?)
                                """);
                PreparedStatement update =
                        connection.prepareStatement(
                                "UPDATE workspaces SET content=? WHERE work_id=?")) {
            while (rows.next()) {
                WorkspaceData data =
                        objectMapper.readValue(rows.getString("content"), WorkspaceData.class);
                if (data.chapters() == null
                        || data.chapters().size() > 100
                        || data.analyses() == null
                        || data.events() == null) {
                    throw new IllegalStateException("Cannot migrate invalid workspace structure");
                }
                Set<String> chapterKeys = new HashSet<>();
                int chapterNo = 0;
                for (ChapterData chapter : data.chapters()) {
                    if (chapter == null
                            || chapter.id() == null
                            || chapter.id().isBlank()
                            || chapter.id().length() > 64
                            || !chapterKeys.add(chapter.id())
                            || chapter.title() == null
                            || chapter.title().isBlank()
                            || chapter.title().length() > 100
                            || chapter.content() == null
                            || chapter.content().length() > 200000
                            || chapter.revision() < 1) {
                        // 손상된 자료를 건너뛰면 원고를 잃으므로 원문을 로그에 남기지 않고 이관을 중단한다.
                        throw new IllegalStateException("Cannot migrate invalid chapter");
                    }
                    insert.setLong(1, rows.getLong("work_id"));
                    insert.setString(2, chapter.id());
                    insert.setInt(3, ++chapterNo);
                    insert.setString(4, chapter.title());
                    insert.setString(5, chapter.content());
                    insert.setLong(6, chapter.revision());
                    insert.setString(7, "migration-v9");
                    insert.setTimestamp(8, rows.getTimestamp("updated_at"));
                    insert.setTimestamp(9, rows.getTimestamp("updated_at"));
                    insert.executeUpdate();
                }
                update.setString(
                        1,
                        objectMapper.writeValueAsString(
                                new WorkspaceData(List.of(), data.analyses(), data.events())));
                update.setLong(2, rows.getLong("work_id"));
                update.executeUpdate();
            }
        }
    }
}

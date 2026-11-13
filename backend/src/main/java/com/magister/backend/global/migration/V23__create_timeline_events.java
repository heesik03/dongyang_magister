package com.magister.backend.global.migration;

import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.EventData;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

/** 타임라인 사건을 Workspace JSON에서 분리해 전용 테이블로 관리한다. */
public class V23__create_timeline_events extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean mysql = connection.getMetaData().getDatabaseProductName().equals("MySQL");
        try (Statement statement = connection.createStatement()) {
            String schema =
                    """
                    CREATE TABLE timeline_events (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        work_id BIGINT NOT NULL,
                        event_key VARCHAR(64) NOT NULL,
                        title VARCHAR(100) NOT NULL,
                        description VARCHAR(2000) NOT NULL,
                        time_text VARCHAR(100) NOT NULL,
                        characters_text VARCHAR(500) NOT NULL,
                        chapter_id VARCHAR(64) NOT NULL,
                        quote TEXT NOT NULL,
                        sequence INT NULL,
                        time_status VARCHAR(20) NULL,
                        source_revision BIGINT NULL,
                        start_offset INT NULL,
                        end_offset INT NULL,
                        extraction_id VARCHAR(36) NULL,
                        created_at TIMESTAMP(6) NOT NULL,
                        updated_at TIMESTAMP(6) NOT NULL,
                        CONSTRAINT fk_timeline_events_work FOREIGN KEY (work_id) REFERENCES works(id) ON DELETE CASCADE,
                        CONSTRAINT uk_timeline_events_key UNIQUE (work_id, event_key)
                    )
                    """;
            if (mysql) {
                schema =
                        schema.replace(
                                "event_key VARCHAR(64)",
                                "event_key VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            }
            statement.execute(schema);
            statement.execute(
                    "CREATE INDEX ix_timeline_events_work_seq ON timeline_events(work_id, sequence, id)");
        }

        ObjectMapper objectMapper = new ObjectMapper();
        try (Statement read = connection.createStatement();
                ResultSet rows =
                        read.executeQuery("SELECT work_id,content,updated_at FROM workspaces");
                PreparedStatement insert =
                        connection.prepareStatement(
                                """
                                INSERT INTO timeline_events(
                                    work_id,event_key,title,description,time_text,characters_text,
                                    chapter_id,quote,sequence,time_status,source_revision,
                                    start_offset,end_offset,extraction_id,created_at,updated_at
                                ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                                """);
                PreparedStatement update =
                        connection.prepareStatement(
                                "UPDATE workspaces SET content=? WHERE work_id=?")) {
            while (rows.next()) {
                WorkspaceData data =
                        objectMapper.readValue(rows.getString("content"), WorkspaceData.class);
                List<EventData> events = data.events() == null ? List.of() : data.events();
                int fallbackSeq = 0;
                for (EventData event : events) {
                    insert.setLong(1, rows.getLong("work_id"));
                    insert.setString(2, event.id());
                    insert.setString(3, event.title());
                    insert.setString(4, event.description() == null ? "" : event.description());
                    insert.setString(5, event.time() == null ? "" : event.time());
                    insert.setString(6, event.characters() == null ? "" : event.characters());
                    insert.setString(7, event.chapterId() == null ? "" : event.chapterId());
                    insert.setString(8, event.quote() == null ? "" : event.quote());
                    if (event.sequence() != null) {
                        insert.setInt(9, event.sequence());
                    } else {
                        insert.setNull(9, Types.INTEGER);
                    }
                    insert.setString(10, event.timeStatus());
                    if (event.sourceRevision() != null) {
                        insert.setLong(11, event.sourceRevision());
                    } else {
                        insert.setNull(11, Types.BIGINT);
                    }
                    if (event.startOffset() != null) {
                        insert.setInt(12, event.startOffset());
                    } else {
                        insert.setNull(12, Types.INTEGER);
                    }
                    if (event.endOffset() != null) {
                        insert.setInt(13, event.endOffset());
                    } else {
                        insert.setNull(13, Types.INTEGER);
                    }
                    insert.setString(14, event.extractionId());
                    insert.setTimestamp(15, rows.getTimestamp("updated_at"));
                    insert.setTimestamp(16, rows.getTimestamp("updated_at"));
                    insert.executeUpdate();
                }
                update.setString(
                        1,
                        objectMapper.writeValueAsString(
                                new WorkspaceData(List.of(), data.analyses(), List.of())));
                update.setLong(2, rows.getLong("work_id"));
                update.executeUpdate();
            }
        }
    }
}

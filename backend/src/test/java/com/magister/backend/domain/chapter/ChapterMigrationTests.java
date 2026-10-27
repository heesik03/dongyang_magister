package com.magister.backend.domain.chapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class ChapterMigrationTests {
    @Test
    void migratesLegacyIdsOrderRevisionUnicodeAndPreservesOtherData() throws Exception {
        String url =
                "jdbc:h2:mem:chapterMigration"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        String location = "classpath:com/magister/backend/global/migration";
        // H2의 IN CHECK 비교기가 생성 연결을 참조하므로 이관 검증이 끝날 때까지 연결을 유지한다.
        try (Connection migrationConnection = DriverManager.getConnection(url, "sa", "")) {
            SingleConnectionDataSource migrationDataSource =
                    new SingleConnectionDataSource(migrationConnection, true);
            Flyway.configure()
                    .dataSource(migrationDataSource)
                    .locations(location)
                    .target("8")
                    .load()
                    .migrate();
            ObjectMapper objectMapper = new ObjectMapper();
            String content =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "chapters",
                                    List.of(
                                            Map.of(
                                                    "id",
                                                    "같은-ID",
                                                    "title",
                                                    "1화",
                                                    "content",
                                                    "첫 문장\r\n반복 😀",
                                                    "revision",
                                                    7),
                                            Map.of(
                                                    "id",
                                                    "c2",
                                                    "title",
                                                    "2화",
                                                    "content",
                                                    "둘째",
                                                    "revision",
                                                    3)),
                                    "analyses",
                                    List.of(),
                                    "events",
                                    List.of(
                                            Map.of(
                                                    "id",
                                                    "event",
                                                    "title",
                                                    "사건",
                                                    "time",
                                                    "",
                                                    "characters",
                                                    "",
                                                    "chapterId",
                                                    "같은-ID",
                                                    "quote",
                                                    "첫 문장"))));
            try (Connection connection = DriverManager.getConnection(url, "sa", "");
                    Statement statement = connection.createStatement()) {
                statement.execute(
                        "INSERT INTO users(id,email,name,password_hash)"
                                + " VALUES(1,'migration@example.com','작가','hash')");
                statement.execute(
                        "INSERT INTO"
                            + " works(id,owner_id,title,genre,era,description,created_at,updated_at,version)"
                            + " VALUES(1,1,'작품','판타지','','',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)");
                statement.execute(
                        "INSERT INTO"
                            + " works(id,owner_id,title,genre,era,description,created_at,updated_at,version)"
                            + " VALUES(2,1,'다른"
                            + " 작품','판타지','','',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)");
                try (PreparedStatement insert =
                        connection.prepareStatement(
                                "INSERT INTO"
                                        + " workspaces(work_id,content,revision,version,updated_at)"
                                        + " VALUES(?,?,5,0,CURRENT_TIMESTAMP)")) {
                    for (long workId = 1; workId <= 2; workId++) {
                        insert.setLong(1, workId);
                        insert.setString(2, content);
                        insert.executeUpdate();
                    }
                }
                try (PreparedStatement insert =
                        connection.prepareStatement(
                                "INSERT INTO"
                                    + " analysis_jobs(id,work_id,request_id,request_body,input,status,created_at,updated_at,version)"
                                    + " VALUES('legacy-job',1,'legacy','{}',?,'FAILED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)")) {
                    insert.setString(1, "{\"chapter\":{\"id\":\"같은-ID\",\"title\":\"1화\"}}");
                    insert.executeUpdate();
                }
            }
            Flyway.configure().dataSource(migrationDataSource).locations(location).load().migrate();
            try (Connection connection = DriverManager.getConnection(url, "sa", "");
                    Statement statement = connection.createStatement();
                    ResultSet job =
                            statement.executeQuery(
                                    "SELECT"
                                        + " chapter_key_snapshot,chapter_title_snapshot,source_format_version"
                                        + " FROM analysis_jobs WHERE id='legacy-job'")) {
                assertThat(job.next()).isTrue();
                assertThat(job.getString("chapter_key_snapshot")).isEqualTo("같은-ID");
                assertThat(job.getString("chapter_title_snapshot")).isEqualTo("1화");
                assertThat(job.getInt("source_format_version")).isZero();
            }
            try (Connection connection = DriverManager.getConnection(url, "sa", "");
                    Statement statement = connection.createStatement();
                    ResultSet chapters =
                            statement.executeQuery(
                                    "SELECT * FROM chapters ORDER BY work_id,chapter_no")) {
                assertThat(chapters.next()).isTrue();
                assertThat(chapters.getString("chapter_key")).isEqualTo("같은-ID");
                assertThat(chapters.getString("content")).isEqualTo("첫 문장\r\n반복 😀");
                assertThat(chapters.getLong("revision")).isEqualTo(7);
                assertThat(chapters.getLong("version")).isZero();
                int count = 1;
                while (chapters.next()) {
                    count++;
                }
                assertThat(count).isEqualTo(4);
            }
            try (Connection connection = DriverManager.getConnection(url, "sa", "");
                    Statement statement = connection.createStatement();
                    ResultSet workspace =
                            statement.executeQuery(
                                    "SELECT content,revision FROM workspaces WHERE work_id=1")) {
                assertThat(workspace.next()).isTrue();
                assertThat(
                                objectMapper
                                        .readTree(workspace.getString("content"))
                                        .get("chapters")
                                        .size())
                        .isZero();
                assertThat(
                                objectMapper
                                        .readTree(workspace.getString("content"))
                                        .get("events")
                                        .get(0)
                                        .get("chapterId")
                                        .asString())
                        .isEqualTo("같은-ID");
                assertThat(workspace.getLong("revision")).isEqualTo(5);
            }
        }
    }
}

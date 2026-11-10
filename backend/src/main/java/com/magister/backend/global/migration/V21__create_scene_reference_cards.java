package com.magister.backend.global.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.Statement;

public class V21__create_scene_reference_cards extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE scene_reference_cards (
                      id VARCHAR(64) PRIMARY KEY,
                      title VARCHAR(160) NOT NULL,
                      author VARCHAR(100) NOT NULL,
                      location VARCHAR(160) NOT NULL,
                      source_url VARCHAR(512) NOT NULL,
                      technique VARCHAR(500) NOT NULL,
                      enabled BOOLEAN NOT NULL DEFAULT TRUE
                    )
                    """);
        }
        String[][] cards = {
            {"austen-letter", "오만과 편견", "제인 오스틴", "35장", "https://www.gutenberg.org/files/1342/1342-h/1342-h.htm", "편지로 앞선 사건을 새롭게 해석할 정보를 공개한다."},
            {"doyle-reports", "바스커빌가의 개", "아서 코난 도일", "8~10장", "https://www.gutenberg.org/files/2852/2852-h/2852-h.htm", "보고와 일기로 관찰과 추측을 나눠 전달한다."},
            {"doyle-photograph", "셜록 홈스의 모험", "아서 코난 도일", "보헤미아의 스캔들", "https://www.gutenberg.org/ebooks/1661", "사진의 행방을 즉시 밝히지 않고 인물의 행동을 통해 숨긴 위치를 드러낸다."},
            {"doyle-pawnbroker", "셜록 홈스의 모험", "아서 코난 도일", "붉은 머리 연맹", "https://www.gutenberg.org/ebooks/1661", "이상해 보이는 구인 조건을 먼저 소개하고 그 조건이 실제 범행에 필요한 이유를 뒤에 공개한다."},
            {"doyle-room", "셜록 홈스의 모험", "아서 코난 도일", "얼룩 끈", "https://www.gutenberg.org/ebooks/1661", "환기구와 종 줄처럼 평범해 보이는 소품을 먼저 보여주고 후반에 기능을 연결한다."},
            {"stevenson-statement", "지킬 박사와 하이드 씨", "로버트 루이스 스티븐슨", "헨리 지킬의 사건 진술", "https://www.gutenberg.org/ebooks/43", "마지막에 당사자의 진술을 공개해 앞서 관찰된 사건의 동기와 인과를 재구성한다."}
        };
        try (PreparedStatement insert = context.getConnection().prepareStatement("""
                INSERT INTO scene_reference_cards(id, title, author, location, source_url, technique)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            for (String[] card : cards) {
                for (int index = 0; index < card.length; index++) {
                    insert.setString(index + 1, card[index]);
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}

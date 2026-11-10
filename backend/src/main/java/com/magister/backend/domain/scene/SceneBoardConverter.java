package com.magister.backend.domain.scene;

import com.magister.backend.domain.scene.SceneBoardData.Board;
import com.magister.backend.domain.scene.SceneBoardData.Preview;
import com.magister.backend.domain.scene.SceneBoardData.Result;
import com.magister.backend.domain.scene.SceneBoardData.Snapshot;
import com.magister.backend.domain.scene.SceneBoardRepository.StoredBoard;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
class SceneBoardConverter {
    private final ObjectMapper objectMapper;

    Board toBoard(StoredBoard board) {
        return new Board(board.id(), board.status(), readSnapshot(board.snapshot()),
                board.result() == null ? null : objectMapper.readValue(board.result(), Result.class));
    }

    Snapshot readSnapshot(String snapshot) {
        return objectMapper.readValue(snapshot, Snapshot.class);
    }

    Preview toPreview(Snapshot snapshot, String token) {
        return new Preview(snapshot, token);
    }
}

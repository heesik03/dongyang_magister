package com.magister.backend.domain.scene;

import com.magister.backend.domain.scene.SceneBoardData.Board;
import com.magister.backend.domain.scene.SceneBoardData.Fact;
import com.magister.backend.domain.scene.SceneBoardData.Preview;
import com.magister.backend.domain.scene.SceneBoardData.Recommendation;
import com.magister.backend.domain.scene.SceneBoardData.Reference;
import com.magister.backend.domain.scene.SceneBoardData.Result;
import com.magister.backend.domain.scene.SceneBoardData.Selection;
import com.magister.backend.domain.scene.SceneBoardData.Setting;
import com.magister.backend.domain.scene.SceneBoardData.Snapshot;
import com.magister.backend.domain.scene.SceneBoardRepository.StoredBoard;
import com.magister.backend.domain.scene.SceneImageData.Input;
import com.magister.backend.domain.scene.SceneImageData.Job;
import com.magister.backend.domain.scene.SceneImageRepository.StoredImage;
import com.magister.backend.domain.setting.entity.WorkSetting;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
class SceneBoardConverter {
    private final ObjectMapper objectMapper;

    Board toBoard(StoredBoard board) {
        return new Board(
                board.id(),
                board.status(),
                readSnapshot(board.snapshot()),
                board.result() == null ? null : objectMapper.readValue(board.result(), Result.class));
    }

    Snapshot readSnapshot(String snapshot) {
        return objectMapper.readValue(snapshot, Snapshot.class);
    }

    Snapshot toSnapshot(
            Selection selection, String chapterTitle, String text, List<WorkSetting> settings) {
        List<Setting> selected =
                settings.stream()
                        .sorted(Comparator.comparing(WorkSetting::getId))
                        .map(
                                setting ->
                                        new Setting(
                                                setting.getId(),
                                                setting.getTitle(),
                                                setting.getContent()))
                        .toList();
        return new Snapshot(selection, chapterTitle, text, selected);
    }

    Preview toPreview(Snapshot snapshot, String token) {
        return new Preview(snapshot, token);
    }

    Fact toFact(String description, String quote, int start, int end) {
        return new Fact(description, quote, start, end);
    }

    Recommendation toRecommendation(Reference reference, String question) {
        return new Recommendation(reference, question);
    }

    Result toResult(
            List<Fact> facts,
            List<String> inferences,
            List<String> gaps,
            List<String> options,
            List<Recommendation> references) {
        return new Result(facts, inferences, gaps, options, references);
    }

    SceneImageData.Preview toImagePreview(Input input, String token, long revision) {
        return new SceneImageData.Preview(input, token, revision);
    }

    Job toImageJob(StoredImage image, Board board) {
        return new Job(
                image.id(),
                image.status(),
                objectMapper.readValue(image.input(), Input.class),
                board.snapshot().selection().revision());
    }
}

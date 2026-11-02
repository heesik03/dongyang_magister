package com.magister.backend.domain.timeline.converter;

import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.timeline.dto.ExtractionData.Candidate;
import com.magister.backend.domain.timeline.dto.ExtractionData.Input;
import com.magister.backend.domain.timeline.dto.ExtractionData.Job;
import com.magister.backend.domain.timeline.dto.ExtractionData.Preview;
import com.magister.backend.domain.timeline.dto.ExtractionData.Source;
import com.magister.backend.domain.timeline.entity.EventExtraction;
import com.magister.backend.domain.timeline.repository.ExtractionView;
import com.magister.backend.domain.workspace.dto.SaveWorkspaceRequest;
import com.magister.backend.domain.workspace.dto.WorkspaceData;
import com.magister.backend.domain.workspace.dto.WorkspaceData.EventData;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExtractionConverter {
    private final ObjectMapper objectMapper;

    public Job response(ExtractionView extraction) {
        return new Job(
                extraction.getId(),
                extraction.getStatus(),
                extraction.getVersion(),
                extraction.getCreatedAt(),
                extraction.getError(),
                Arrays.asList(objectMapper.readValue(extraction.getResult(), Candidate[].class)),
                List.of());
    }

    public Job response(EventExtraction extraction) {
        return new Job(
                extraction.getId(),
                extraction.getStatus(),
                extraction.getVersion(),
                extraction.getCreatedAt(),
                extraction.getError(),
                Arrays.asList(objectMapper.readValue(extraction.getResult(), Candidate[].class)),
                List.of());
    }

    public Preview preview(Input input, String token) {
        return new Preview(input.sources(), token, input.model());
    }

    public Input input(List<Chapter> chapters, String model) {
        return new Input(
                chapters.stream()
                        .map(
                                chapter ->
                                        new Source(
                                                chapter.getChapterKey(),
                                                chapter.getTitle(),
                                                chapter.getRevision(),
                                                chapter.getChapterNo(),
                                                chapter.getContent()))
                        .toList(),
                model);
    }

    public EventData event(Candidate candidate, String extractionId) {
        return new EventData(
                candidate.id(),
                candidate.title(),
                candidate.time(),
                candidate.characters(),
                candidate.chapterId(),
                candidate.quote(),
                candidate.description(),
                candidate.sequence(),
                candidate.timeStatus(),
                candidate.sourceRevision(),
                candidate.startOffset(),
                candidate.endOffset(),
                extractionId);
    }

    public SaveWorkspaceRequest confirmation(
            long version, String mutationId, List<EventData> events) {
        return new SaveWorkspaceRequest(
                version, mutationId, new WorkspaceData(List.of(), List.of(), events), true, true);
    }
}

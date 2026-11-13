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

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExtractionConverter {
    public Job response(ExtractionView extraction, List<Candidate> candidates) {
        return new Job(
                extraction.getId(),
                extraction.getStatus(),
                extraction.getVersion(),
                extraction.getCreatedAt(),
                extraction.getError(),
                candidates,
                List.of());
    }

    public Job response(EventExtraction extraction, List<Candidate> candidates) {
        return new Job(
                extraction.getId(),
                extraction.getStatus(),
                extraction.getVersion(),
                extraction.getCreatedAt(),
                extraction.getError(),
                candidates,
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

    public Candidate candidate(
            String id,
            String title,
            String description,
            String time,
            String characters,
            String chapterId,
            long sourceRevision,
            int startOffset,
            int endOffset,
            String quote,
            int sequence) {
        return new Candidate(
                id,
                title,
                description,
                time,
                characters,
                chapterId,
                sourceRevision,
                startOffset,
                endOffset,
                quote,
                "HOLD",
                "UNRESOLVED",
                sequence);
    }

    public EventData event(Candidate candidate, String extractionId, int sequence) {
        return new EventData(
                candidate.id(),
                candidate.title(),
                candidate.time(),
                candidate.characters(),
                candidate.chapterId(),
                candidate.quote(),
                candidate.description(),
                sequence,
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

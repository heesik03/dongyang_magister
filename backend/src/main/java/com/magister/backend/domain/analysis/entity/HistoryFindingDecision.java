package com.magister.backend.domain.analysis.entity;

import com.magister.backend.domain.analysis.dto.HistoryDecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "history_finding_decisions", uniqueConstraints =
        @UniqueConstraint(columnNames = {"job_id", "finding_index"}),
        indexes = @Index(name = "ix_history_decision_claim", columnList = "claim_key"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HistoryFindingDecision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AnalysisJob job;

    @Column(name = "finding_index", nullable = false)
    private int findingIndex;

    @Column(name = "claim_key", length = 64, nullable = false)
    private String claimKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HistoryDecision.Choice choice;

    @Column(nullable = false, length = 500)
    private String reason;

    public HistoryFindingDecision(AnalysisJob job, int index, String key) {
        this.job = job;
        findingIndex = index;
        claimKey = key;
    }

    public void record(HistoryDecision decision) {
        choice = decision.choice();
        reason = decision.reason().strip();
    }

    public HistoryDecision decision() {
        return new HistoryDecision(choice, reason);
    }
}

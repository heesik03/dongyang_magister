import { useEffect, useState } from 'react'
import { reviewClient, type ChapterReviewSummary, type WorkReviewOverview } from './reviewClient'

const STATUS_LABELS: Record<ChapterReviewSummary['analysisStatus'], string> = {
  CURRENT: '최신 원고 분석됨',
  OUTDATED: '수정 전 원고 기준',
  UNANALYZED: '미분석',
}

export function ReviewOverviewModal({
  workId,
  currentChapterId,
  onSelectChapter,
  onClose,
}: {
  workId: string
  currentChapterId?: string
  onSelectChapter?: (chapterId: string) => void
  onClose: () => void
}) {
  const [reloadKey, setReloadKey] = useState(0)
  const [state, setState] = useState<{
    key: string
    overview: WorkReviewOverview | null
    error: string
  } | null>(null)
  const [viewMode, setViewMode] = useState<'visual' | 'table'>('visual')
  const requestKey = `${workId}:${reloadKey}`

  useEffect(() => {
    const controller = new AbortController()
    void reviewClient
      .getOverview(workId, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) {
          setState({ key: requestKey, overview: result, error: '' })
        }
      })
      .catch((failure) => {
        if (!controller.signal.aborted) {
          setState({
            key: requestKey,
            overview: null,
            error:
              failure instanceof Error
                ? failure.message
                : '회차별 검토 현황을 불러오지 못했습니다.',
          })
        }
      })
    return () => controller.abort()
  }, [workId, requestKey])

  const current = state?.key === requestKey ? state : null
  const overview = current?.overview ?? null
  const loading = !current
  const error = current?.error ?? ''

  const maxCandidates = Math.max(
    1,
    ...(overview?.chapters.map((chapter) => chapter.totalCandidateCount) ?? [1]),
  )

  return (
    <section className="review-overview-box" aria-label="회차별 검토 현황">
      <div className="review-overview-header">
        <div>
          <strong>회차별 검토 현황</strong>
          <p className="small muted">
            전체 회차의 최신 분석 완료 기준 후보 분포와 작가 판단 상태를 비교합니다.
          </p>
        </div>
        <div className="review-overview-actions">
          <div className="filter-tabs" role="group" aria-label="검토 현황 보기 방식">
            <button
              type="button"
              aria-pressed={viewMode === 'visual'}
              className={viewMode === 'visual' ? 'selected' : ''}
              onClick={() => setViewMode('visual')}
            >
              막대 분포
            </button>
            <button
              type="button"
              aria-pressed={viewMode === 'table'}
              className={viewMode === 'table' ? 'selected' : ''}
              onClick={() => setViewMode('table')}
            >
              목록 표
            </button>
          </div>
          <button type="button" className="ghost small" onClick={onClose}>
            접기
          </button>
        </div>
      </div>

      {loading && <p role="status">회차별 검토 현황을 불러오고 있습니다.</p>}

      {error && (
        <div role="alert" className="notice">
          <p className="error">{error}</p>
          <button type="button" onClick={() => setReloadKey((value) => value + 1)}>
            다시 시도
          </button>
        </div>
      )}

      {overview && !loading && !error && (
        <>
          <div className="review-overview-summary">
            <span className="badge">전체 {overview.totalChapters}회차</span>
            <span className="badge info">
              최신 원고 분석 {overview.currentRevisionChapters}회차
            </span>
            <span className="badge warning">
              수정 전 원고 기준 {overview.outdatedRevisionChapters}회차
            </span>
            <span className="badge">미분석 {overview.unanalyzedChapters}회차</span>
            <span className="badge danger">설정 충돌 후보 {overview.totalConflictCount}건</span>
            <span className="badge warning">설명 보완 {overview.totalSupplementCount}건</span>
            <span className="badge">미검토 {overview.totalUnreviewedCount}건</span>
          </div>

          <p className="small muted">{overview.aggregationBasisNote}</p>

          <div className="review-overview-legend" aria-label="검토 현황 범례">
            <span>
              <i className="legend-dot legend-dot--conflict" aria-hidden="true" /> 설정 충돌 후보
              (건수 병기)
            </span>
            <span>
              <i className="legend-dot legend-dot--supplement" aria-hidden="true" /> 설명 보완 (건수
              병기)
            </span>
            <span>
              <i className="legend-dot legend-dot--unreviewed" aria-hidden="true" /> 미검토 상태
              강조
            </span>
          </div>

          {viewMode === 'visual' ? (
            <div className="review-overview-bars" role="list">
              {overview.chapters.map((chapter) => {
                const conflictWidth =
                  chapter.totalCandidateCount > 0
                    ? Math.round((chapter.conflictCount / maxCandidates) * 100)
                    : 0
                const supplementWidth =
                  chapter.totalCandidateCount > 0
                    ? Math.round((chapter.supplementCount / maxCandidates) * 100)
                    : 0
                const isSelected = currentChapterId === chapter.chapterId
                return (
                  <div
                    role="listitem"
                    key={chapter.chapterId}
                    className={`review-overview-row${isSelected ? ' review-overview-row--active' : ''}`}
                  >
                    <div className="review-overview-row-top">
                      <div>
                        <strong>
                          {chapter.chapterNo}화 · {chapter.chapterTitle}
                        </strong>
                        <span
                          className={`badge ${
                            chapter.analysisStatus === 'CURRENT'
                              ? 'info'
                              : chapter.analysisStatus === 'OUTDATED'
                                ? 'warning'
                                : ''
                          }`}
                        >
                          {STATUS_LABELS[chapter.analysisStatus]}
                        </span>
                      </div>
                      {onSelectChapter && (
                        <button
                          type="button"
                          className="text-button"
                          onClick={() => onSelectChapter(chapter.chapterId)}
                        >
                          {isSelected ? '현재 회차' : `${chapter.chapterTitle} 열기 →`}
                        </button>
                      )}
                    </div>

                    {chapter.analysisStatus === 'UNANALYZED' ? (
                      <p className="small muted">
                        아직 완료된 AI 검토가 없습니다. (현재 원고 버전 {chapter.currentRevision})
                      </p>
                    ) : (
                      <>
                        <div className="review-overview-bar-track" aria-hidden="true">
                          <span
                            className="review-overview-bar-conflict"
                            style={{ width: `${conflictWidth}%` }}
                          />
                          <span
                            className="review-overview-bar-supplement"
                            style={{ width: `${supplementWidth}%` }}
                          />
                        </div>
                        <div className="review-overview-counts small">
                          <span>설정 충돌 후보 {chapter.conflictCount}건</span>
                          <span>설명 보완 {chapter.supplementCount}건</span>
                          <strong>미검토 {chapter.unreviewedCount}건</strong>
                          <span>
                            처리됨 (수용 {chapter.acceptedCount} · 보류 {chapter.holdCount} · 의도된
                            설정 {chapter.intendedCount})
                          </span>
                          <span className="muted">
                            분석 원고 버전 {chapter.analyzedRevision ?? chapter.currentRevision} /
                            현재 버전 {chapter.currentRevision} · 설정 {chapter.settingCount}개 ·
                            이전 회차 {chapter.previousChapterCount}개
                          </span>
                        </div>
                      </>
                    )}
                  </div>
                )
              })}
            </div>
          ) : (
            <div className="review-overview-table-wrap">
              <table className="review-overview-table">
                <caption>회차별 검토 후보 및 판단 집계 표</caption>
                <thead>
                  <tr>
                    <th scope="col">회차</th>
                    <th scope="col">분석 기준</th>
                    <th scope="col">설정 충돌 후보</th>
                    <th scope="col">설명 보완</th>
                    <th scope="col">미검토</th>
                    <th scope="col">수용/보류/의도</th>
                    <th scope="col">이동</th>
                  </tr>
                </thead>
                <tbody>
                  {overview.chapters.map((chapter) => (
                    <tr key={chapter.chapterId}>
                      <td>
                        {chapter.chapterNo}화 · {chapter.chapterTitle}
                      </td>
                      <td>
                        {STATUS_LABELS[chapter.analysisStatus]}
                        {chapter.analyzedRevision != null
                          ? ` (v${chapter.analyzedRevision}/현재 v${chapter.currentRevision})`
                          : ` (현재 v${chapter.currentRevision})`}
                      </td>
                      <td>{chapter.conflictCount}건</td>
                      <td>{chapter.supplementCount}건</td>
                      <td>{chapter.unreviewedCount}건</td>
                      <td>
                        {chapter.acceptedCount} / {chapter.holdCount} / {chapter.intendedCount}
                      </td>
                      <td>
                        {onSelectChapter && (
                          <button
                            type="button"
                            className="text-button"
                            onClick={() => onSelectChapter(chapter.chapterId)}
                          >
                            이동
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  )
}

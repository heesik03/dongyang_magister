# 백엔드 서비스·리포지터리 리팩터링 검토

> 구현 전 점검 기록이다. 이후 적용된 코드·최종 구조·검증·커밋은 [리팩터링 적용 결과](backend-refactoring-implementation-2026-10-26.md)를 기준으로 확인한다.

검토일: 2026-10-26. 대상: 서비스·실행 컴포넌트 9개, 리포지터리 5개와 관련 엔티티·DTO·보안·마이그레이션·테스트. 현재 수정 중인 Swagger 문서의 표현·포맷은 검토에서 제외했다. 구현 코드는 변경하지 않았다.

기획 기준은 [Magister 기획서](https://app.notion.com/p/3eac29dbef40809b8150f5dc8e403d2e)다. 원고·설정 버전과 검토 근거를 연결하고, 작가 판단을 맥락에 맞게 재사용해야 한다는 조건을 확인했다.

## 1. 결론과 우선순위

먼저 고칠 부분은 비동기 작업의 실패 처리와 동시성이다. 타입 표기·주석·컨버터 정리는 그다음이다. `var`와 DTO 생성자의 사용 자체가 실행 성능을 떨어뜨리지는 않는다.

| 우선순위 | 문제 | 판정과 영향 |
| --- | --- | --- |
| P1 | 커밋 후 실행 접수 거절의 실패 상태가 저장되지 않음 | 임시 진단 테스트로 재현. 실행되지 않는 작업이 QUEUED에 남아 계속 폴링됨 |
| P1 | 사용자별 동시 작업 제한의 경합 | 코드로 확인. 같은 사용자가 서로 다른 작품에 동시에 요청하면 각각의 작품 잠금으로는 제한을 보장하지 못함 |
| P2 | 작업 선점·입력 해석 단계가 예외 처리 밖에 있음 | 잘못된 저장 입력으로 재현. 실행이 종료되어도 작업이 QUEUED에 남음. 현재 정상 API 입력으로 직접 만들 수 있다는 뜻은 아님 |
| P2 | 상태 조회에서 쓰기 잠금과 불필요한 대용량 필드 조회 | 코드로 확인. 1초마다 상태 조회가 작업 상태 변경과 경쟁하고 원고 스냅샷까지 읽음 |
| P2 | 취소가 실행기·외부 호출과 연결되지 않음 | 코드로 확인. CANCELLED로 응답해도 실행 중인 공급자 호출은 계속될 수 있음 |
| P2 | 시작 시 모든 미완료 작업을 일괄 실패 처리 | 다중 인스턴스 운영 시 문제. 새 인스턴스가 다른 인스턴스의 정상 실행 작업도 실패 처리함 |
| P2 | 작업 상태를 문자열과 무제한 transition으로 관리 | 변경 안전성 문제. 잘못된 상태·중복 실행을 엔티티가 막지 못함 |
| P2 | Workspace 전체 JSON 읽기·쓰기 | 구조상 비용 확인. 작은 판단 수정에도 전체 원고·분석 JSON을 전송·해석·재저장함 |
| P2 | 중복 권한 조회와 조회 목적에 비해 넓은 엔티티 로딩 | 코드로 확인. 분석 생성 시 이미 읽은 작품을 WorkspaceService가 다시 질의함 |
| 측정 후 | 검색 조건과 인덱스 | 문자열 부분 검색·선택 조건의 OR가 병목 후보. MySQL 실행 계획·지연 시간으로 확정해야 함 |
| 설계 개선 | DTO 변환·페이지 계약·서비스 책임·주석·명시적 타입 | 유지보수성 개선. 아래 적용 구조와 예시 참고 |

P1은 리팩터링 전에 동작을 고쳐야 하는 결함, P2는 실패 경로·운영·부하 대응 개선이다. 재현하지 않은 처리량·쿼리 시간은 수치로 추정하지 않았다.

## 2. 전체 점검 결과

### 서비스와 실행 컴포넌트

| 대상 | 확인 내용 | 권장 범위 |
| --- | --- | --- |
| AnalysisJobService | 요청 검증, 스냅샷 조립, 권한, 작업 제한, 실행기 생성·종료, 커밋 후 제출, 상태 조회·취소가 한 클래스에 있음 | 가장 먼저 책임 분리. 실행기와 상태 전환 트랜잭션을 분리 |
| AnalysisWorker | 선점, JSON 해석, 프롬프트, 외부 호출, 결과 검증, 과거 판단 반영, 상태 저장을 수행 | 실행 흐름만 남기고 입력/결과/상태 컴포넌트 분리 |
| WorkspaceService | 권한, 직렬화, 참조 검증, 멱등 저장, 버전 검사, 작품 갱신을 수행 | 참조 검증과 코덱 분리. 일관성 있는 저장은 트랜잭션 서비스에 유지 |
| GeminiService | SecurityContext 접근, 호출 제한 상태, 동기화, 외부 호출을 수행 | 사용자 ID를 명시적으로 받고 호출 제한 정책을 별도 컴포넌트로 분리 |
| SettingService | 현재 CRUD 책임은 비교적 명확함. 권한 조회 중복과 부모 작품 버전 갱신에 주의 | 무조건 Command/Query 두 서비스로 나눌 필요 없음 |
| WorkService | 작은 CRUD 서비스이며 정렬·소유권·버전 검사가 있음 | 컨버터와 재사용 권한 조회만 추출. 불필요한 세분화 방지 |
| UserService | 비밀번호 해싱의 트랜잭션 분리와 DB 유일 제약 대응이 있음 | 이메일 정규화의 공유 위치와 예외 판별 책임 정리 |
| SessionAuthenticationService | 세션 고정 방지·CSRF 회전·비밀 없는 principal 저장이 있음 | 인증 후 사용자 재조회와 전략 객체의 반복 생성 정리 |
| UserDetailsServiceImpl | 사용자 조회·보안 principal 변환이 작고 명확함 | UserService 정적 메서드 의존과 var 정리 |

### 리포지터리

| 대상 | 확인 내용 | 권장 범위 |
| --- | --- | --- |
| AnalysisJobRepository | 상태 count, 작업 행 쓰기 잠금, 재시작 시 bulk update | 읽기 projection과 변경용 잠금 분리. 재시작 범위·버전 처리 보완 |
| WorkRepository | 소유권 조회, 작품 쓰기 잠금, 오프셋 검색 | 잠금 목적 문서화. 필요 시 커서 검색 추가 |
| WorkSettingRepository | 작품 범위 조회, ID 목록 일괄 조회, 조건별 페이지 | IN 일괄 조회 유지. 더 보기용 ID 커서 추가 검토 |
| UserRepository | 이메일 exists/조회만 제공 | 현재 구조 유지. 동시 작업 제어에 사용자 잠금을 선택하면 전용 메서드 추가 |
| WorkspaceRepository | JpaRepository 기본 메서드만 사용 | 현재 작은 인터페이스 자체는 문제 없음 |

## 3. 우선 수정할 동작 문제와 예시

아래 코드는 변경 방향을 설명하는 발췌 예시다. 신규 타입·메서드는 제안이며 현재 저장소에 존재하지 않는다. import와 일부 생성자·보조 메서드는 생략했다. 예시를 하나의 완성 패치로 간주하면 안 된다.

### 3.1 커밋 후 접수 거절의 실패 상태 누락 — P1

위치: `AnalysisJobService.java:142–158`.

`afterCommit()`에서 실행기 제출이 거절되면 기본 전파 속성의 TransactionTemplate로 실패 상태를 변경한다. 이 시점에는 원 트랜잭션이 이미 커밋됐지만 리소스 정리가 끝나지 않았을 수 있어, 변경이 기존 트랜잭션에 참여하고 추가 커밋이 발생하지 않는다.

실행기를 종료해 접수 거절을 유도하는 임시 테스트에서 서비스 호출 후 DB 상태가 FAILED가 아닌 QUEUED임을 확인했다. 종료 상황 외에도 실행 큐가 차면 같은 catch 경로에 도달한다.

해결: 별도 빈에서 새 트랜잭션으로 상태를 기록한다. 같은 클래스의 자기 호출로 @Transactional을 붙이면 프록시를 거치지 않으므로 해결되지 않는다.

```java
@Service
@RequiredArgsConstructor
public class AnalysisJobStateService {
    private final AnalysisJobRepository jobs;

    /** 커밋 후 접수 실패도 별도 커밋으로 기록하고 이미 종료된 상태는 보존한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failDispatch(String jobId) {
        AnalysisJob job = jobs.lockById(jobId).orElseThrow();
        if (!job.terminal()) {
            job.transition("FAILED", null, "AI 대기열이 가득 찼습니다. 다시 시도해 주세요.");
        }
    }
}

// afterCommit 내부. states는 주입받은 별도 빈이다.
try {
    executor.submit(() -> worker.execute(jobId));
} catch (RejectedExecutionException error) {
    states.failDispatch(jobId);
}
```

근거: [Spring TransactionSynchronization.afterCommit 문서](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html#afterCommit())는 이 위치의 DB 변경에 REQUIRES_NEW를 명시한다. 새 트랜잭션은 추가 커넥션을 사용할 수 있으므로 풀 여유와 상태 저장 실패 시 복구 경로도 검증한다.

### 3.2 사용자별 제한의 검사와 생성이 원자적이지 않음 — P1

위치: `AnalysisJobService.java:61–72`, `AnalysisJobRepository.java:14–16`.

잠그는 대상은 작품이다. 한 사용자가 작품 A와 B에 동시 요청하면 서로 다른 행을 잠그고, 두 요청이 모두 사용자별 활성 작업 0개를 읽은 뒤 작업을 생성할 수 있다. 전체 10개 제한도 count 후 insert 방식이어서 여러 작품의 동시 요청에서 엄밀한 예약이 아니다. 실행기의 물리적 용량은 2+8로 제한되어 있지만, 그 사실이 DB의 작업 접수 제한을 원자적으로 만들지는 않는다.

해결: 사용자별 제한에는 사용자 단위 잠금 또는 활성 슬롯의 DB 유일 제약을 사용한다. 단일 서버의 전체 실행 용량은 별도의 permit 예약으로 관리할 수 있으며, 여러 서버에서는 공유 예약이 필요하다. 모든 경로에서 잠금 순서를 고정한다.

```java
// UserRepository에 추가하는 방안
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select u from User u where u.id = :ownerId")
Optional<User> lockById(Long ownerId);

// 작업 생성 트랜잭션: 사용자 -> 작품 순서로 일관되게 잠근다.
users.lockById(ownerId).orElseThrow();
Work work = works.lockOwned(workId, ownerId).orElseThrow();
if (jobs.countByWork_Owner_IdAndStatusIn(ownerId, ACTIVE_STATUSES) > 0) {
    throw new CustomException(CommonErrorCode.TOO_MANY_REQUESTS);
}
```

이 예시는 사용자별 경합만 해결한다. 전체 용량 예약은 롤백·접수 거절·작업 종료·취소 시 permit 반환까지 별도로 설계해야 한다. 취소로 DB 상태만 바뀌었는데 외부 호출이 진행 중이면 실제 실행 용량을 즉시 반환해서는 안 된다.

### 3.3 선점 단계 예외가 작업 실패로 기록되지 않음 — P2

위치: `AnalysisWorker.java:46–58`.

작업 잠금, RUNNING 전환, 입력 JSON 해석이 try 밖에 있다. 이 단계에서 예외가 나면 트랜잭션은 롤백되고, submit이 반환한 Future도 읽지 않아 실패가 실행기에 묻힌다. 잘못된 저장 입력을 사용한 진단 테스트에서 예외 발생과 QUEUED 잔류를 확인했다. 정상 생성 경로가 현재 잘못된 JSON을 만든다는 주장은 아니다. 저장 데이터 형식 변경·데이터 손상·DB 오류에 대한 복구 공백이다.

해결: 선점부터 예외 처리 범위에 넣고 상태 저장을 별도 서비스로 호출한다. DB 장애로 실패 기록도 불가능한 경우를 위해 오래된 미완료 작업을 탐지하는 복구 정책을 둔다.

```java
public void execute(String jobId) {
    try {
        Optional<AnalysisClaim> claim = states.claim(jobId);
        if (claim.isEmpty()) {
            return;
        }
        AnalysisData result = analyze(claim.get());
        states.succeedIfRunning(jobId, result);
    } catch (Exception error) {
        log.warn("Analysis execution failed: jobId={}, type={}",
                jobId, error.getClass().getSimpleName());
        states.failIfActive(jobId, failureMessages.forException(error));
    }
}
```

`claim()`은 QUEUED인 작업만 가져오고 중복 전달된 RUNNING 작업을 다시 실행하지 않도록 한다. 외부 호출은 이 트랜잭션이 종료된 후 수행한다.

### 3.4 상태 폴링에 쓰기 잠금과 원고 전체 조회 사용 — P2

위치: `AnalysisJobService.java:165–182`, `AnalysisJobRepository.java:18–20`, `AnalysisJob.java:27–37`, `frontend/src/features/analysis/analysisClient.ts:102` 부근.

상태 GET도 `owned()`를 통해 PESSIMISTIC_WRITE를 사용한다. 프런트엔드는 1초 간격으로 폴링하므로 조회가 작업 완료·취소와 같은 행의 잠금을 경쟁한다. 또한 엔티티 조회는 요청 본문·입력 원고·결과 LONGTEXT를 함께 가져온다. QUEUED/RUNNING 확인에 입력 원고는 필요 없다.

해결: 조회는 readOnly 트랜잭션과 소유권 조건을 포함한 projection, 변경은 쓰기 잠금으로 구분한다.

```java
public interface AnalysisJobView {
    String getId();
    String getStatus();
    String getError();
    String getResult();
}

@Query("""
    select j.id as id, j.status as status, j.error as error, j.result as result
    from AnalysisJob j
    where j.id = :jobId and j.work.id = :workId
      and j.work.owner.id = :ownerId
    """)
Optional<AnalysisJobView> findOwnedView(String jobId, Long workId, Long ownerId);

@Transactional(readOnly = true)
public AnalysisJobResponse get(Long ownerId, Long workId, String jobId) {
    AnalysisJobView view = jobs.findOwnedView(jobId, workId, ownerId)
            .orElseThrow(() -> new CustomException(WorkErrorCode.WORK_NOT_FOUND));
    return converter.toResponse(view);
}
```

성공 결과가 매우 커지면 상태와 결과를 별도 조회하는 계약도 검토할 수 있다. 우선은 위처럼 입력 스냅샷과 쓰기 잠금을 제거하는 작은 변경이 적절하다.

### 3.5 취소 상태와 실제 실행 취소의 분리 — P2

위치: `AnalysisJobService.java:170–174`, `AnalysisWorker.java:69–75`.

현재 취소는 DB 상태만 CANCELLED로 변경한다. 대기 중 작업은 실행 시 terminal 검사로 공급자를 호출하지 않지만 큐에서 즉시 제거되지는 않는다. 실행 중인 외부 호출은 계속될 수 있다. 결과 덮어쓰기는 terminal 검사로 방지하고 있으나, 스레드·공급자 동시 호출 슬롯·과금이 즉시 해제되는 것은 아니다.

```java
// Dispatcher가 작업 ID와 Future를 관리하는 방향
Future<?> task = tasks.remove(jobId);
if (task != null) {
    task.cancel(true);
}
```

`cancel(true)`는 인터럽트 요청일 뿐 공급자 작업 중단을 보장하지 않는다. SDK의 실제 취소 지원을 검증하고, 큐의 취소 작업 제거·한도 반환·완료 경합을 함께 처리한다. 현재 제품 계약이 '결과 적용 중단'이라면 그 의미를 명확히 하고, '외부 분석 즉시 중단'으로 안내해서는 안 된다.

### 3.6 재시작 복구가 모든 서버의 작업에 적용됨 — 조건부 P2

위치: `AnalysisJobService.java:54–56`, `AnalysisJobRepository.java:22–26`.

ApplicationReadyEvent에서 모든 QUEUED/RUNNING 행을 실패 처리한다. 단일 서버 전체 재시작에서는 의도에 맞지만 서버를 2개 이상 운영하면 정상 실행 중인 다른 서버의 작업도 실패시킨다. bulk update는 updatedAt과 @Version도 갱신하지 않아, 이미 읽은 엔티티의 이후 저장과 충돌 검사가 맞지 않을 수 있다.

단일 서버를 유지한다면 제약을 문서화한다. 다중 서버 전환 시 workerId·leaseUntil/heartbeat와 재선점 정책을 도입하고 만료된 작업만 처리한다.

```sql
-- lease 컬럼을 추가하는 경우의 개념 예시. 현재 스키마에 실행하지 않는다.
UPDATE analysis_jobs
SET status = 'FAILED', error_message = ?,
    updated_at = CURRENT_TIMESTAMP(6), version = version + 1
WHERE status IN ('QUEUED', 'RUNNING') AND lease_until < CURRENT_TIMESTAMP(6);
```

### 3.7 문자열 상태와 임의 전환 — P2

위치: `AnalysisJob.java:33–69`, 서비스·리포지터리의 상태 문자열.

다섯 상태가 여러 위치에 흩어지고 `transition(String, ...)`은 아무 문자열이나 받는다. 현재 선점은 terminal이 아니면 RUNNING으로 바꾸므로 중복 전달 시 RUNNING 작업도 다시 실행할 수 있다. 현재 요청 멱등성은 존재하지만 엔티티 자체의 상태 전이 보호와는 별개다.

```java
public enum AnalysisJobStatus {
    QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED
}

@Enumerated(EnumType.STRING)
private AnalysisJobStatus status;

public boolean start() {
    if (status != AnalysisJobStatus.QUEUED) {
        return false;
    }
    status = AnalysisJobStatus.RUNNING;
    updatedAt = Instant.now();
    return true;
}
```

`succeed`, `fail`, `cancel`도 허용 상태를 검사하는 메서드로 만든다. JPQL bulk update·count 인자·컨버터·API 테스트를 함께 갱신하고 JSON의 기존 대문자 상태값은 유지한다.

## 4. 사용자 제안 다섯 항목에 대한 판단

### 4.1 var, 짧은 변수명, wildcard import

메인 코드의 var는 26곳이다. 그중 서비스·실행 컴포넌트·UserDetailsService에 24곳이 있다. AnalysisJobService 12곳, AnalysisWorker 6곳, WorkspaceService 4곳, GeminiService와 UserDetailsServiceImpl 각각 1곳이다.

Java의 var는 컴파일 시 구체적인 타입이 확정되므로 동적 타입이나 런타임 성능 문제는 아니다. 이 코드에서는 DTO·Optional·엔티티의 구분이 숨겨지고 `c`, `a`, `r`, `w`, `jobs`, `works`, `json` 같은 축약과 겹쳐 읽기 비용을 높인다. 요청한 명시적 타입 정책을 적용하는 것이 적절하다.

```java
Optional<AnalysisJob> existingJob = analysisJobRepository
        .findByWork_IdAndRequestId(workId, request.requestId());
WorkspaceResponse workspaceResponse = workspaceService.get(ownerId, workId);
List<WorkSetting> selectedSettings = workSettingRepository
        .findAllByWork_IdAndIdIn(workId, settingIds);

for (WorkspaceData.ChapterData chapter : data.chapters()) {
    if (!chapterIds.add(chapter.id())) {
        throw new CustomException(CommonErrorCode.INVALID_REQUEST);
    }
}
```

와일드카드 import도 명시적 import로 정리한다. 이 작업은 기능 결함 수정과 다른 커밋으로 분리하면 검토가 쉽다.

### 4.2 서비스 책임 분리

가장 큰 대상은 AnalysisJobService와 AnalysisWorker다. WorkService·SettingService까지 획일적으로 여러 클래스로 나누면 오히려 탐색 비용이 커진다. 구체적인 패키지는 7절에 제시한다.

### 4.3 메서드와 주요 로직 주석

현재 주석이 전혀 없는 것은 아니다. 회원가입 해싱, DB 유일 제약, 버전 flush, 소유권 검사, 세션 고정 방지, CSRF 회전에는 이유가 설명되어 있다. 반면 리포지터리의 잠금 범위와 AnalysisJobService.create의 입력·멱등성·실패 정책은 설명이 부족하다.

공개 서비스 메서드와 사용자 정의 리포지터리 메서드에는 목적과 계약을 Javadoc으로 적는다. 단순 getter나 JpaRepository의 상속 메서드까지 반복 설명하지 않는다. 주요 내부 로직에는 '왜 필요한가'를 적는다.

```java
/**
 * 현재 소유자의 작품을 쓰기 잠금으로 조회한다.
 * 워크스페이스 최초 생성과 분석 접수의 작품 단위 경합을 직렬화할 때 사용한다.
 * 호출자는 쓰기 트랜잭션을 열어야 하며 외부 API 호출 동안 잠금을 유지하지 않는다.
 */
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select w from Work w where w.id = :id and w.owner.id = :ownerId")
Optional<Work> lockOwned(Long id, Long ownerId);

/**
 * 선택된 회차·확정 설정의 스냅샷으로 분석을 접수한다.
 * 같은 작품에서 동일 requestId와 같은 요청은 기존 작업을 반환한다.
 * 같은 requestId의 다른 요청과 오래된 workspaceVersion은 거절한다.
 */
public AnalysisJobResponse create(Long ownerId, Long workId, AnalysisRequest request) {
    // 구현 생략
}
```

`// 설정 조회`, `// 응답 생성` 같은 코드의 반복 설명보다 잠금 순서, 재시도 조건, 상태 전이, 판단 재사용 범위, 조회량에 주석을 집중한다.

### 4.4 JPA N+1과 실제 조회 비용

현재 검토한 운영 경로에서는 전형적인 목록 N+1을 확정하지 못했다.

| 경로 | 확인 결과 |
| --- | --- |
| 작품 목록 | WorkResponse는 Work 자체 필드만 읽음. owner나 settings를 순회하지 않음 |
| 설정 목록 | SettingResponse는 연결된 Work의 ID만 읽음. 일반적인 Hibernate 프록시 ID 접근은 초기화를 요구하지 않음. 소유권 확인으로 작품도 먼저 읽음 |
| 분석 입력 설정 | findAllByWork_IdAndIdIn으로 한 번에 읽음. 설정별 findById 반복이 아님 |
| 부모 컬렉션에 자식 추가 | 현재 mappedBy List의 add가 컬렉션을 초기화하지 않는지 기존 통합 테스트가 검증함 |
| 워커 선점 | job의 work를 읽는 추가 조회 후보는 있지만 작업 한 건의 고정 비용이며 목록 크기에 비례한 N+1로 분류하지 않음 |

따라서 모든 관계에 EAGER나 fetch join을 추가할 근거는 없다. 실제 개선 대상은 3.4의 잠금·LONGTEXT 로딩, 분석 생성의 중복 작품 조회, 전체 JSON 저장, 페이지의 count·깊은 offset이다.

N+1 회귀 검증은 영속성 컨텍스트를 clear한 뒤 목록 1건과 여러 건의 SQL 수를 비교한다. count 쿼리는 별도로 구분한다. 컬렉션 fetch join과 페이지 조회를 직접 결합하면 행 수 증가·메모리 페이징 문제가 생길 수 있으므로 사용하지 않는다.

### 4.5 쿠키·반복 로직의 공통화

현재 백엔드에는 직접적인 Cookie/ResponseCookie 생성 중복이 없다. 세션 쿠키는 application.yaml의 cookie 설정과 Spring Security의 로그아웃 deleteCookies로 관리한다. 이 부분을 별도 CookieUtil로 옮길 이유는 없다.

현재 추출 가치가 있는 것은 다음과 같다.

- 작품 소유권 조회: WorkAccessService. 도메인 권한이므로 global util이 아님.
- 이메일 정규화: user 도메인의 EmailNormalizer. 인증 어댑터가 UserService 정적 메서드에 의존하지 않게 함.
- Workspace/분석 JSON 변환: 각 도메인의 Codec. 임의 Object를 받는 거대한 JsonUtil 대신 입력·출력 타입을 고정함.
- 동일 CSRF 저장소와 세션 인증 전략: SecurityConfig의 빈을 주입해 설정 불일치를 방지함.
- 반복되는 DTO 매핑: 각 도메인의 converter.
- 페이지 크기·응답 형태: global.pagination.

```java
public final class EmailNormalizer {
    private EmailNormalizer() {}

    public static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
```

`invalid()`가 두 군데 있다고 해서 업무 검증을 전부 CommonValidationUtil로 모으지는 않는다. 검증 규칙과 오류 코드는 해당 도메인에 유지한다.

## 5. DTO 생성은 도메인별 Converter로 통일

현재 WorkResponse.from, SettingResponse.from, UserResponse.from은 이미 정적 변환 메서드다. 문제는 변환 방식이 DTO 내부 from, 서비스의 new, JSON 해석으로 섞여 있다는 점이다.

권장 배치:

```text
domain/work/converter/WorkConverter.java
domain/setting/converter/SettingConverter.java
domain/user/converter/UserConverter.java
domain/analysis/converter/AnalysisJobConverter.java
domain/workspace/converter/WorkspaceConverter.java
```

단순 순수 매핑은 정적 메서드로 충분하다. ObjectMapper처럼 협력 객체가 필요한 AnalysisJobConverter는 생성자 주입 컴포넌트로 만든다. global에는 도메인을 아는 converter를 두지 않는다. 현재 규모에서는 MapStruct 등 새 의존성이 필요하지 않다.

```java
public final class WorkConverter {
    private WorkConverter() {}

    public static WorkResponse toResponse(Work work) {
        return new WorkResponse(
                work.getId(), work.getTitle(), work.getGenre(), work.getEra(),
                work.getDescription(), work.getCreatedAt(), work.getUpdatedAt(),
                work.getVersion());
    }
}

// WorkService: 생성 위치와 변환 규칙을 한곳으로 모은다.
return WorkConverter.toResponse(workRepository.saveAndFlush(work));

// 목록도 동일 컨버터를 사용한다.
Page<WorkResponse> responses = workRepository.search(ownerId, keyword, genre, pageable)
        .map(WorkConverter::toResponse);
return PageResponse.from(responses);
```

컨버터 내부의 new는 필요하다. 목표는 서비스에서 DTO 조립을 제거하고 매핑을 통일하는 것이며, 객체 할당 자체를 없애는 최적화는 아니다. 권한 검사·DB 조회·상태 변경을 컨버터에 넣어서는 안 된다.

복잡한 분석 입력 구성은 단순 DTO 변환과 구분한다. 회차 선택·확정 설정 검사·이전 원고 선택은 AnalysisInputFactory/Validator가 담당하고, 최종 값 객체 조립은 컨버터가 담당한다. 목록 컨버터에서 연관관계를 추가로 읽지 않도록 조회 계획도 함께 검토한다.

## 6. global 공통 객체로 오프셋·커서 페이징 분리

현재 global.response.PageResponse가 이미 있다. items/page/size/totalElements/totalPages 계약을 제공하지만 요청 크기·페이지 변환 정책과 커서 계약은 없다. WorkService와 SettingService는 각각 PageRequest를 만든다.

### 6.1 공통화할 것과 도메인에 남길 것

```text
global/pagination/
  OffsetPageRequest.java
  OffsetPageResponse.java
  CursorPageRequest.java
  CursorPageResponse.java
  CursorCodec.java
domain/work/dto/WorkCursor.java
domain/setting/dto/SettingCursor.java
```

공통: 크기 상한, 기본값, 페이지 메타데이터, size+1 처리, 커서 인코딩/디코딩 규칙. 도메인: 정렬 필드, 커서에 담을 키, 소유권·필터, 실제 검색 조건과 인덱스. 공통 객체가 Work나 Setting 엔티티를 참조하지 않도록 한다.

```java
public record OffsetPageRequest(@Min(0) int page, @Min(1) @Max(100) int size) {
    public OffsetPageRequest {
        if (page < 0 || size < 1 || size > 100) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page, size, sort);
    }
}

public record OffsetPageResponse<T>(
        List<T> items, int page, int size, long totalElements, int totalPages) {
    public static <T> OffsetPageResponse<T> from(Page<T> result) {
        return new OffsetPageResponse<>(List.copyOf(result.getContent()),
                result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}

public record CursorPageRequest(String cursor, @Min(1) @Max(100) int size) {
    public CursorPageRequest {
        if (size < 1 || size > 100 || (cursor != null && cursor.length() > 2048)) {
            throw new CustomException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    public Pageable toLookaheadPageable(Sort sort) {
        return PageRequest.of(0, size + 1, sort);
    }
}

public record CursorPageResponse<T>(List<T> items, boolean hasNext, String nextCursor) {
    public static <S, T> CursorPageResponse<T> from(
            List<S> rows, int size, Function<S, T> mapper, Function<S, String> encode) {
        boolean hasNext = rows.size() > size;
        List<S> visible = rows.subList(0, Math.min(size, rows.size()));
        String nextCursor = hasNext ? encode.apply(visible.getLast()) : null;
        return new CursorPageResponse<>(visible.stream().map(mapper).toList(),
                hasNext, nextCursor);
    }
}
```

예시는 정상적인 요청 객체에서 검증된 size를 넘기는 전제다. 컨트롤러 바인딩 시 기본 page=0/size=20을 명시하고 잘못된 커서 해석은 400으로 변환한다. 기존 쿼리 파라미터 이름은 유지할 수 있다.

### 6.2 각 로직에 적용

| 조회 | 권장 방식 | 이유 |
| --- | --- | --- |
| 기존 작품 목록 | 우선 오프셋 유지 | 현재 UI가 전체 개수와 totalPages를 사용함 |
| 설정의 더 보기 | 커서 우선 도입 후보 | ID 내림차순이며 전체 페이지 수 없이 다음 묶음을 읽기 쉬움 |
| 작품 더 보기 전환 | UI 계약 변경과 함께 커서 추가 | updatedAt/id 복합 정렬과 갱신에 따른 이동 정책이 필요함 |
| 선택된 설정을 분석에 일괄 조회 | 페이징을 적용하지 않음 | 최대 1000개 ID의 검증·스냅샷 구성은 전체 선택 집합이 필요함 |
| 단일 Workspace/단일 작업 상태 | 페이징을 적용하지 않음 | 목록이 아님 |

설정 커서의 실제 질의 예시:

```java
@Query("""
    select s from WorkSetting s
    where s.work.id = :workId
      and (:kind is null or s.kind = :kind)
      and (:status is null or s.status = :status)
      and (:beforeId is null or s.id < :beforeId)
    order by s.id desc
    """)
List<WorkSetting> searchAfter(Long workId, SettingKind kind, SettingStatus status,
        Long beforeId, Pageable limit);

// 서비스: 소유권 확인은 빈 페이지에도 수행한다.
workAccessService.requireOwned(ownerId, workId);
Long beforeId = settingCursorCodec.decodeId(request.cursor(), workId, kind, status);
List<WorkSetting> rows = workSettingRepository.searchAfter(workId, kind, status,
        beforeId, request.toLookaheadPageable(Sort.unsorted()));
return CursorPageResponse.from(rows, request.size(), SettingConverter::toResponse,
        setting -> settingCursorCodec.encode(setting.getId(), workId, kind, status));
```

설정 커서에는 마지막 id와 workId/kind/status 문맥을 담는다. 커서는 권한 증명이 아니므로 매 요청의 세션 소유권 검사를 유지한다. 필터를 바꾸면 커서를 초기화하고, 다른 문맥의 커서는 거절한다. Base64는 은닉·위변조 방지가 아니며 필요에 따라 서명 정책을 별도로 선택한다.

작품 목록은 현재 `updatedAt DESC, id DESC` 정렬이므로 다음 조건이 필요하다.

```sql
AND (updated_at < :lastUpdatedAt
     OR (updated_at = :lastUpdatedAt AND id < :lastId))
ORDER BY updated_at DESC, id DESC
LIMIT :sizePlusOne
```

id만 커서로 사용하면 정렬과 페이지 경계가 달라 누락·중복이 생긴다. 기존 ix_works_owner_updated는 이 정렬의 기반이 된다. timestamp는 DB의 마이크로초 정밀도를 보존해야 한다. 순회 중 updatedAt이 바뀌면 행이 이동할 수 있어 커서만으로 동일 시점의 목록을 보장할 수 없다. 실시간 목록 허용인지, 고정 스냅샷이 필요한지 제품 정책을 정한다.

[Spring Data 스크롤링 문서](https://docs.spring.io/spring-data/commons/reference/repositories/scrolling.html)는 안정적인 정렬과 키셋 조건을 설명한다. 해당 프레임워크 기능을 쓰더라도 현재 검색 조건과 실제 리포지터리 지원 범위를 확인해야 한다. 위 예시는 제한된 List 조회로 방향을 표현했다.

### 6.3 API·프런트엔드 전환 비용

현재 `LibraryPage`, `SettingsPanel`, `useWorksStore`, `settingClient`는 totalPages 또는 totalElements를 사용한다. CursorPageResponse로 타입만 바꾸면 깨진다.

1. 기존 오프셋 응답 JSON 필드는 유지하고 내부 공통 타입을 정리한다.
2. 커서용 조회 메서드와 명시적인 HTTP 계약을 추가한다. 예를 들어 `GET /api/work/{workId}/setting/scroll?cursor=...&size=20`을 검토할 수 있다. 현재 단일 설정 경로와 매핑 충돌을 검증한다.
3. 더 보기 화면의 page/totalPages 상태를 nextCursor/hasNext로 전환하고 필터 변경 시 초기화한다.
4. 전체 개수 표시가 꼭 필요한 화면은 오프셋을 유지하거나 별도 count 계약을 선택한다.
5. 같은 id가 들어왔을 때 병합 규칙과 재시도 중복을 검증한다. 페이징 계약 변경은 단순 내부 리팩터링과 구분한다.

## 7. service 패키지 안에서의 구체적인 책임 분리

다음은 목표 구조다. 첫 단계에서 모든 파일을 한꺼번에 만들 필요는 없다. 먼저 실제로 큰 분석 도메인과 Workspace 검증부터 분리한다.

```text
domain/analysis/
  service/
    AnalysisJobService.java                  # 생성·조회·취소 유스케이스 진입점
    input/
      AnalysisInputFactory.java              # 회차·설정·이전 원고·판단 스냅샷 조립
      AnalysisRequestValidator.java          # 선택 범위·확정 상태·요청 조합 검증
    execution/
      AnalysisDispatcher.java                # 실행 제출·취소·Future와 작업 ID 연결
      AnalysisWorker.java                    # 선점 -> 외부 호출 -> 검증 -> 완료 흐름
    state/
      AnalysisJobStateService.java            # 선점·성공·실패·취소의 짧은 트랜잭션
    result/
      AnalysisPromptBuilder.java              # 프롬프트 생성
      AnalysisResultValidator.java            # JSON 구조·원문·설정 근거·옵션 검사
  converter/
    AnalysisJobConverter.java

domain/work/
  service/
    WorkService.java                         # 현재 CRUD 업무 흐름 유지
    WorkAccessService.java                   # 소유권 조회와 명시적인 잠금 조회
  converter/WorkConverter.java

domain/setting/
  service/SettingService.java                # 현재 CRUD 규모에서는 유지
  converter/SettingConverter.java

domain/workspace/
  service/
    WorkspaceService.java                    # 조회·저장 업무 흐름
    validation/WorkspaceReferenceValidator.java
    support/WorkspaceCodec.java              # WorkspaceData <-> JSON
  converter/WorkspaceConverter.java

domain/gemini/
  service/
    GeminiService.java                       # 공급자 호출 흐름
    limit/GeminiRateLimiter.java              # 사용자 간격·실제 동시 호출 수

global/config/AnalysisExecutionConfig.java    # 관리되는 실행기·설정값·종료 정책
global/pagination/...                        # 도메인 독립 공통 계약
```

분리 기준은 클래스 길이만이 아니라 변경 이유다. 프롬프트 변경, 업무 검증 변경, DB 트랜잭션 변경, 실행 용량 변경을 각각 독립적으로 수정할 수 있어야 한다.

| 책임 | 트랜잭션 | 금지할 의존 |
| --- | --- | --- |
| AnalysisJobService.create | 권한·멱등성·예약·스냅샷 저장의 일관성을 보장하는 쓰기 트랜잭션 | 공급자 호출 |
| AnalysisInputFactory/Validator | 별도 트랜잭션을 만들지 않음. 필요 데이터는 호출자가 확보 | Executor·HTTP 응답·SecurityContext |
| AnalysisDispatcher | DB 변경은 상태 서비스에 위임 | Repository 직접 변경 |
| AnalysisWorker | 전체 메서드를 트랜잭션으로 감싸지 않음 | 세션 ThreadLocal에 의존한 사용자 조회 |
| AnalysisJobStateService | 선점·완료·실패마다 짧은 트랜잭션. afterCommit 실패 처리는 REQUIRES_NEW | 외부 API 호출 |
| Converter/Codec | 자체 트랜잭션 없음 | Repository·권한 조회·상태 변경 |
| WorkAccessService | 상위 트랜잭션에 참여. 잠금 메서드에는 필요 시 MANDATORY 적용 | 다른 유스케이스를 역호출 |

검증기는 순수 로직이면 일반 클래스로 두고, Validator/ObjectMapper 같은 협력 객체를 주입받을 때 @Component를 사용한다. 모든 지원 클래스를 @Service로 만들지 않는다. 같은 패키지 안에서도 서비스 간 순환 참조를 만들지 않는다.

흐름 예시:

```text
HTTP create
  -> AnalysisJobService [접수 트랜잭션]
     -> WorkAccessService / 용량 예약
     -> AnalysisInputFactory + Validator
     -> AnalysisJobRepository.saveAndFlush
     -> 커밋 후 Dispatcher.submit(jobId)

Dispatcher
  -> AnalysisWorker [전체 트랜잭션 없음]
     -> StateService.claim [짧은 트랜잭션, 스냅샷 반환]
     -> PromptBuilder -> GeminiService -> GeminiClient [DB 트랜잭션 없음]
     -> ResultValidator
     -> StateService.succeed/fail [짧은 트랜잭션]
```

실행기는 빈으로 주입하고 스레드 이름·큐 길이·거절·종료 대기 시간을 설정으로 관리한다. 현재 수치 2개 실행/8개 대기를 임의로 늘리는 것은 해결이 아니다.

## 8. 추가로 개선할 비용과 위험

### 8.1 전체 JSON 저장과 부모 작품 잠금

위치: `WorkspaceService.java:46–65`, `WorkspaceData.java`, `Workspace.java:25–26`.

작은 판단 변경도 전체 JSON을 직렬화·검증·저장한다. 분석 생성도 Workspace 전체를 해석한다. 크기 상한이 있으므로 무제한이라는 뜻은 아니지만, 원고와 분석 이력이 증가할수록 네트워크·JSON CPU·DB 쓰기량이 커지고 부모 잠금 유지 시간도 늘어난다.

1차로 순수 직렬화·참조 검증을 잠금 전에 수행하는 비트랜잭션 진입점과, 잠금·버전·멱등성·저장만 수행하는 별도 트랜잭션 빈을 검토한다. 데이터 스냅샷은 검증 후 변하지 않는 값이어야 한다.

```java
public WorkspaceResponse save(Long ownerId, Long workId, SaveWorkspaceRequest request) {
    workspaceReferenceValidator.validate(request.content());
    String content = workspaceCodec.encode(request.content());
    return workspacePersistenceService.save(ownerId, workId, request, content);
}
```

그다음 실제 크기·저장 빈도를 측정해 회차·판단·이벤트를 별도 테이블/부분 갱신으로 나눌지 결정한다. 스키마를 나눌 때 원문 revision·설정 version·근거 snapshot·작가 판단의 연결을 잃지 않아야 한다.

SettingService도 매 변경에서 work.touch()를 호출한다. 서로 다른 설정을 수정해도 동일 Work의 @Version에서 충돌할 수 있다. 전체 작품 버전 갱신이 의도된 계약인지 확인하고, 최신 수정 시각과 내용 버전이 과도하게 결합되어 있으면 분리한다. 이를 없애기 전에 프런트엔드의 workVersion 사용처를 확인해야 한다.

### 8.2 중복 권한 조회

위치: `AnalysisJobService.java:61–73`, `WorkspaceService.java:30–33`.

분석 생성이 작품을 잠가 읽은 뒤 WorkspaceService.get이 같은 소유권 조건으로 작품을 다시 질의한다. JPQL 조건 조회는 같은 엔티티가 영속성 컨텍스트에 있어도 질의 실행을 없애주지 않는다.

WorkAccessService로 권한 검사를 통일하되 한 유스케이스에서 이미 확인한 Work를 내부 스냅샷 조회 컴포넌트에 전달하거나, AnalysisInputFactory가 현재 트랜잭션 안에서 WorkspaceRepository를 직접 사용하게 한다. 검증 없는 메서드를 새 공개 HTTP 경로에서 호출할 수 없게 경계를 제한한다.

### 8.3 필터·부분 검색·count 비용 — 측정 후 결정

위치: `WorkRepository.java:22–28`, `WorkSettingRepository.java:17–23`, 마이그레이션 V2/V3/V6.

- 작품 검색의 locate(lower(keyword), lower(title))는 일반 title B-tree로 부분 문자열 위치를 찾기 어렵다. 다만 owner_id로 범위를 좁힐 수 있으므로 전체 테이블 풀 스캔이라고 단정하지 않는다.
- 선택 필터를 OR로 처리하는 질의는 필터별 인덱스 사용이 최적이 아닐 수 있다.
- 설정에는 work_id/id와 work_id/kind/id 인덱스가 있지만 status 조합은 실제 사용량을 보고 판단해야 한다.
- Page는 전체 개수가 필요할 때 count 비용이 발생할 수 있다. Spring Data가 count를 생략하는 경우도 있어 항상 정확히 두 SQL이라고 단정하지 않는다.

MySQL의 실제 Hibernate 생성 SQL에 EXPLAIN ANALYZE를 적용하고, 사용자별 데이터 수·선택도·정렬·깊은 페이지를 비교한다. 필요하면 Specification/Criteria로 있는 조건만 구성하고, 확인된 패턴에 `(work_id, status, id)` 또는 복합 필터 인덱스를 추가한다. 모든 조합을 미리 인덱싱하면 쓰기·저장 비용이 늘어난다. Full-text 검색은 한국어 검색 의미가 달라질 수 있어 제품 요구 확인 없이 대체하지 않는다.

### 8.4 Gemini 호출 제한의 책임과 인증 경계

위치: `GeminiService.java:45–88`.

generate가 SecurityContext를 직접 읽고 principal이 없으면 owner=0으로 호출한다. 현재 보안 필터는 비인증 HTTP 요청을 차단하므로 곧바로 인증 우회가 되는 것은 아니다. 하지만 서비스 재사용과 테스트에서 숨은 사용자 값이 생긴다.

```java
// Controller에서 인증된 ID를 전달하고 서비스가 명시적으로 받는다.
public GeminiResponse generate(Long ownerId, GeminiRequest request) {
    return converter.toResponse(generateForOwner(ownerId, request.prompt(), 1024));
}
```

rate limiter를 분리해 공유 정책을 한곳에서 검사한다. 현재 reserve는 synchronized 안에서 최대 1만 사용자 항목을 매번 정리한다. 사용자가 많을 때 전역 임계 구역 비용 후보이며 현재 병목으로 실측한 것은 아니다. 만료 정리를 주기적으로 나누거나 만료 자료구조를 선택한다. 단일 프로세스 메모리 제한은 여러 인스턴스에 공유되지 않는다.

분석 큐와 일반 Gemini API는 같은 전체 동시 호출 2개를 경쟁한다. QUEUED 작업을 RUNNING으로 바꾼 뒤 공급자 슬롯을 못 얻으면 즉시 FAILED가 된다. 접수 시의 작업 수와 실제 공급자 슬롯을 분리해 스케줄링하거나, 외부 요청을 보내기 전의 슬롯 대기를 명시적으로 관리한다. 이미 공급자에 전송한 생성 요청을 자동 재시도하는 해결책은 피한다.

### 8.5 로그인 재조회·전략 객체·이메일 예외 판별

위치: `UserDetailsServiceImpl.java:20–27`, `SessionAuthenticationService.java:37–51`, `UserService.java:58–74`.

인증 과정에서 이메일로 사용자를 읽은 뒤 로그인 서비스가 다시 같은 사용자를 읽는다. 인증 결과의 custom UserDetails에 ID·이메일·이름을 포함해 재조회를 줄일 수 있다. 비밀번호 해시가 들어간 인증 객체 전체를 세션에 저장해서는 안 되며, 현재처럼 공개 필드만 가진 LoginUser로 교체해야 한다.

```java
AccountPrincipal account = (AccountPrincipal) authenticated.getPrincipal();
LoginUser principal = new LoginUser(account.userId(), account.email(), account.displayName());
UserResponse response = UserConverter.toResponse(principal);
```

AccountPrincipal은 새 UserDetails 구현을 뜻하며 기존 Spring Security User를 그대로 캐스팅하는 예시가 아니다. 세션 고정 방지 전략과 CSRF 저장소도 SecurityConfig의 공유 빈으로 주입하면 로그인마다 전략을 만들고 독립 저장소 기본값에 의존하는 점을 줄일 수 있다. 현재 기본 설정이 일치하므로 지금 CSRF가 깨졌다는 주장은 아니다.

이메일 제약 예외 판별은 constraintName과 메시지에 email이 포함되는지 넓게 검사한다. 현재 제약 uk_users_email을 기준으로 판별하는 전용 persistence 예외 번역기로 옮기고 MySQL/H2 각각 검증한다. 일반 무결성 오류를 이메일 중복으로 오인하지 않도록 기존 재던지기 동작을 유지한다.

회원가입에 @Transactional이 없다는 점은 결함으로 보지 않는다. 고비용 비밀번호 해싱 동안 커넥션을 점유하지 않고 saveAndFlush에서 저장 트랜잭션을 열도록 한 의도이며, 이를 보장하는 SignupConnectionTests도 있다.

### 8.6 입력·결과 자료구조와 오류 진단

위치: `AnalysisJobService.java:111–118`, `AnalysisWorker.java:100–130`, `WorkspaceService.java:76–100`.

previousIds.contains를 각 회차에 반복하고, 후보별 설정·과거 판단을 매번 선형 검색한다. 현재 크기 상한이 있어 우선순위는 낮지만 의미가 명확한 Set/Map으로 변환하면 중복 탐색과 복잡도를 줄일 수 있다.

```java
Set<String> previousIds = new HashSet<>(request.previousIds());
Map<String, AnalysisInput.SettingReference> settingsById = input.settings().stream()
        .collect(Collectors.toMap(AnalysisInput.SettingReference::id, Function.identity()));
```

판단 이력은 quote·settingId·evidence 같은 복합 키로 조회하되 같은 문장 반복과 버전 차이를 어떻게 다룰지 기획의 맥락 유지 조건을 확인한다. 현재 데이터 구조를 바꾸면서 '의도된 설정'을 작품 전체 예외로 확대하면 안 된다.

예외 로그는 개인정보 보호를 고려해 본문을 출력하지 않고 있지만 AnalysisWorker는 예외 타입만 기록한다. jobId·오류 코드·실패 단계·허용된 코드 위치를 추가하면 문의와 DB 상태를 연결할 수 있다. 원고·프롬프트·토큰·SDK 예외 전문은 로그에 넣지 않는다.

### 8.7 작업 입력 보존과 인증 요청 한도

analysis_jobs는 원고 스냅샷·요청·결과를 계속 누적하며 현재 정리 경로가 없다. 조회 성능 수치와 별개로 저장량·백업량·민감 원고 보존 범위가 증가한다. 멱등 키의 유효기간과 원고 삭제 정책을 먼저 정하고, terminal 상태 및 보존 기한 기준으로 제한된 배치 정리를 설계한다. 임의로 보존 기간을 정해 삭제하지 않는다.

로그인·가입·이메일 확인에는 Gemini처럼 별도 호출 제한이 보이지 않는다. 외부 공개 전에 인증 시도 제한과 비밀번호 해싱 동시 작업 상한을 확인한다. 이는 서비스 메서드 안의 중복 조회 제거와 별개인 운영 보완이며, 전송 계층 또는 앞단 제한이 이미 있는지는 이번 코드만으로 확인하지 못했다.

## 9. 검증 결과와 이후 회귀 검증

실행한 검증:

| 명령·방법 | 결과 |
| --- | --- |
| git status --short, 관련 파일·설정·마이그레이션·테스트 전체 확인 | 구현 코드 수정 없이 점검 |
| backend에서 `.\gradlew.bat test` | 성공. UP-TO-DATE여서 실제 재실행은 다음 명령으로 수행 |
| `.\gradlew.bat test --rerun-tasks` | 15개 테스트 클래스, 62개 테스트, 실패·오류·건너뜀 0 |
| 임시 소스 경로를 Gradle init script로 추가해 ReviewProbeTests만 실행 | 2개 진단 테스트 통과. 정상 동작을 보장하는 테스트가 아니라 결함 현상을 확인한 테스트 |

임시 진단 파일은 저장소 밖 `%TEMP%/magister-review-4c45e45ffc1646dcb1a3e39ee15c945b/`에 있다. 실행 명령은 다음과 같다.

```powershell
.\gradlew.bat test --tests com.magister.backend.review.ReviewProbeTests --init-script 'C:\Users\kys14\AppData\Local\Temp\magister-review-4c45e45ffc1646dcb1a3e39ee15c945b\review.init.gradle'
```

- rejectedDispatchDoesNotPersistFailedStatusAfterCommit: executor.shutdown으로 거절을 유도하고 DB 상태가 QUEUED임을 확인했다. 수정 후에는 FAILED를 기대하는 정식 회귀 테스트로 바꿔야 한다.
- claimDecodeFailureEscapesAndLeavesJobQueued: 잘못된 입력 JSON에서 예외가 전파되고 작업이 QUEUED에 남음을 확인했다. 수정 후에는 실패 상태·안전한 오류 응답을 기대해야 한다.

프런트엔드는 변경하지 않았으며 빌드·테스트를 실행하지 않았다. 서버를 띄우거나 추가 포트를 열지 않았다. MySQL 실부하·EXPLAIN·다중 인스턴스·실제 공급자 취소·정확한 SQL 개수는 미검증이다. 현재 통합 테스트 DB는 H2 MySQL 모드이므로 MySQL의 실제 잠금·실행 계획과 같다고 간주하지 않는다. 컴파일 시 기존 RequestSizeFilter와 UserAuthenticationTests의 deprecated API 경고가 있었다.

구현 단계의 필수 회귀 검증:

1. 접수 거절·커밋 실패·선점 실패·실패 상태 저장 실패·종료 처리.
2. 동일 사용자/서로 다른 작품의 동시 요청과 전체 용량 상한. MySQL에서 잠금 순서·타임아웃 검증.
3. 중복 작업 전달, QUEUED/RUNNING 취소, 완료와 취소의 경합, 재시작 복구.
4. 상태 GET이 쓰기 잠금을 잡지 않고 입력 LONGTEXT를 조회하지 않는지 확인.
5. 목록 SQL 수가 반환 건수에 비례하지 않는지, 선택 필터·깊은 페이지의 실행 계획 확인.
6. 커서 경계의 같은 timestamp, 빈 목록, 마지막 페이지, 잘못된 커서, 다른 작품·필터, 재시도·동시 변경.
7. 컨버터 적용 전후 API JSON 필드·타입·오류·버전 응답 유지와 세션에 비밀이 없는지 확인.

## 10. 권장 적용 순서

1. P1·실패 경로 결함과 회귀 테스트부터 수정한다. 상태 조회의 쓰기 잠금도 함께 제거한다.
2. AnalysisJobStateService와 관리형 실행기/Dispatcher를 분리해 트랜잭션·실행 생명주기를 명확히 한다.
3. AnalysisInputFactory·ResultValidator·WorkspaceReferenceValidator와 도메인 Converter를 추출한다. API 계약은 유지한다.
4. 명시적 타입·이름·import·메서드 계약 주석을 정리한다. 큰 로직 이동과 기능 변경은 분리한다.
5. 오프셋 공통 계약을 정리하고 설정 더 보기에 커서를 먼저 추가한다. 프런트엔드와 함께 전환한다.
6. 실제 MySQL 측정 결과에 따라 인덱스·검색 조건·JSON 저장 단위를 개선한다. 다중 서버 전환 시 공유 용량 예약·lease 복구를 추가한다.


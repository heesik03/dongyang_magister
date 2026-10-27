# 백엔드 리팩터링 적용 및 검증 결과

작성일: 2026-10-26. 기준 브랜치: `feat/#10-mvp-review-flow`.

## 적용 내용

| 대상 | 적용 결과 |
| --- | --- |
| 타입·이름·포맷 | 운영·테스트 Java의 var와 와일드카드 import 제거. 서비스 의존성 이름을 Repository/Encoder 등의 역할로 명시. 한 줄 제어문에 중괄호 적용. 기존 google-java-format 1.28.0 AOSP 스타일 사용 |
| 메서드·주요 로직 주석 | 서비스·사용자 정의 Repository 메서드 계약과 잠금, 멱등성, 버전, 취소, 근거 재사용, 복구 이유 보강. 의미가 바뀌는 단계 사이에 빈 줄 배치 |
| Analysis 책임 | 과도한 12개 service 파일을 6개로 통합. 입력 검증·조립, 프롬프트·결과 규격은 각각 응집된 지원 컴포넌트로 관리 |
| 접수 경합 | 공유 DB의 단일 접수 행을 먼저 잠가 사용자별·전체 활성 작업 count와 생성 직렬화. 동일 requestId·본문의 재요청은 기존 작업 반환 |
| 작업 상태 | enum과 명시적 상태 전이 메서드로 관리. 중복 RUNNING 선점과 종료 상태 덮어쓰기 방지 |
| 실패 처리 | 선점·입력 JSON 해석부터 예외 처리. 커밋 후 실행 접수 거절도 별도 REQUIRES_NEW 트랜잭션으로 FAILED 저장 |
| 상태 폴링 | 소유권 조건을 포함한 projection 한 번으로 조회. 쓰기 잠금과 입력 원고·요청 본문 LONGTEXT 로딩 제거 |
| 취소·종료 | 작업 ID와 Future 연결. 커밋 후 큐 제거·인터럽트 전파. Future 취소와 실제 외부 호출 종료를 구분 |
| 복구 | 프로세스별 2분 lease, 10초 갱신. 실제 로컬 실행기에 남은 작업만 갱신하고 만료된 미완료 작업만 실패 처리 |
| 보존 정책 | 종료 작업 정리 코드 제공. 기본 0s는 삭제 비활성. 운영 설정으로 보존 기간을 지정할 때만 제한된 배치로 정리 |
| DTO | 도메인별 converter로 통일. 서비스·컨트롤러에서 응답 DTO 직접 조립 제거. Analysis 변환도 한 converter로 통합 |
| 권한·JSON | WorkAccessService로 소유권 확인 통합. 분석 생성 시 이미 확인한 작품 재사용. Workspace 순수 검증·JSON 직렬화를 DB 잠금 전에 수행하고 저장 응답의 재해석 제거 |
| 하위 수정 | 부모 작품 잠금 순서 통일. 다른 설정의 동시 수정이 부모 @Version에서 불필요하게 충돌하는 경합 완화 |
| 페이징 | global.pagination에 오프셋·커서 요청/응답·코덱 추가. 기존 오프셋 JSON 유지, 설정 더 보기와 전체 설정 순회를 ID 커서로 전환 |
| 조회 조건 | 지정된 필터만 Specification으로 구성해 선택 조건의 OR 제거. 부분 문자열 검색 의미 유지. 무조건 EAGER·fetch join·새 인덱스를 추가하지 않음 |
| 공급자 호출 | SecurityContext 의존 제거, 사용자 ID 명시. 일반 생성은 즉시 한도 검사, 접수된 분석은 전송 전에 슬롯 대기. 사용자 만료 항목의 전체 정리를 매 요청 수행하지 않음 |
| 인증 | 인증 결과에 공개 프로필 포함해 로그인 후 재조회 제거. 세션에는 비밀 없는 LoginUser만 저장. CSRF 저장소와 세션 회전 전략 공유 |
| 이메일 | 정규화 공통화. 알려진 유일 제약만 업무 오류로 번역하며 MySQL 실제 동시 가입으로 검증 |
| 인증 자원 제한 | IP·경로별 시도 횟수, 고비용 해싱 동시 작업 수, 추적 항목 수 제한. 일반 필터 오류 응답의 JSON·상태·인코딩 통일 |
| 안전한 로그 | 분석 jobId·단계·오류 타입 기록. JDBC 오류의 원문 입력값 대신 오류 코드·SQLState·벤더 번호 기록 |

## 최종 Analysis 구조

```text
domain/analysis/
  service/
    AnalysisJobService.java        # 접수·조회·취소 업무 경계
    AnalysisJobStateService.java   # 짧은 상태 전환 트랜잭션과 lease 영속 처리
    AnalysisWorker.java            # 선점 -> 호출 -> 검증 -> 완료 흐름
    AnalysisDispatcher.java        # 실행 제출·취소·종료·주기 유지
    support/
      AnalysisInputFactory.java    # 선택 범위 검증·스냅샷 조립
      AnalysisResultProcessor.java # 프롬프트와 결과 근거 검증
  converter/
    AnalysisConverter.java
global/config/
  AnalysisExecutionConfig.java
  AnalysisExecutionProperties.java
```

트랜잭션 프록시가 필요한 상태 저장은 실행기와 분리한다. 외부 공급자 호출을 워커 전체 트랜잭션으로 감싸지 않는다. 작은 처리마다 새 파일을 만들지 않고 함께 변경되는 규칙을 모았다.

## 코딩 원칙 문서

로컬 `docs/engineering/backend.md`에 다음 원칙을 추가했다.

- 운영 코드·테스트의 var 금지, 명시적 타입·import와 역할이 드러나는 이름.
- 서비스는 업무 흐름·권한·트랜잭션을 조율하고 DTO 변환·외부 통신·복잡한 검증 책임을 분리.
- 실제 변경 이유·트랜잭션 경계로 분리하며 작은 helper 하나씩 파일로 만드는 과도한 분리 방지.
- 공개 서비스·사용자 정의 Repository 메서드 위의 계약 주석과 주요 로직의 이유·제약 주석.
- 처리 단계 사이 빈 줄, 한 줄 제어문의 중괄호, 사용하지 않는 import 제거.
- 공통 오프셋·커서 객체 사용과 커서의 정렬·필터 문맥·권한 검사.

루트 AGENTS.md의 상세 문서 진입점·확인 기준일·현재 구현 상태도 갱신했다. 저장소 정책에 따라 AGENTS.md와 docs/engineering 지침은 Git에서 제외된 로컬 문서로 유지했다. `docs/engineering/testing.md`에는 별도 mysqlTest 실행 기준도 추가했다.

## 검증

| 검증 | 결과 |
| --- | --- |
| `.\gradlew.bat test` | 19개 클래스, 83개 테스트 통과. 실패·오류·건너뜀 0 |
| `.\gradlew.bat build mysqlTest` | 빌드 성공. 일반 83개와 별도 MySQL 3개 통과 |
| `.\gradlew.bat help` | 추가한 mysqlTest 태스크 설정 구문 확인 성공 |
| `pnpm test --maxWorkers=1` | 6개 파일, 52개 테스트 통과 |
| `pnpm build` | TypeScript·Vite 빌드 통과 |
| `pnpm lint` | 통과 |
| `pnpm format:check` | 통과 |
| Java formatter dry-run | google-java-format 1.28.0 AOSP, 변경 필요 파일 0 |
| Java 타입·import 검색 | var·와일드카드 import 0건 |
| `git diff --check` | 통과 |

주요 회귀 검증은 실행 거절, 손상·null·불완전한 저장 입력, 중복 선점, 서로 다른 작품의 동일 사용자 동시 접수, 큐 취소, 실행 중 인터럽트, 다른 서버의 정상 lease 보존, projection SQL 수, 커서의 다른 작품·필터 재사용·형식 오류·페이지 크기 오류, 새 항목 삽입 중 다음 페이지, DTO·세션 비밀 노출, CSRF 회전, 해싱 중 커넥션 비점유를 포함한다.

### 실제 MySQL

MySQL 8.0.41에서 새 임시 스키마 `magister_refactor_936581f71863`을 만들어 검증했다. 기존 개발·운영 DB에는 검증 데이터를 넣지 않았다.

1. 서로 다른 작품의 동시 접수 중 같은 사용자에게 정확히 하나만 허용.
2. 동시에 해싱을 시작한 동일 이메일 가입 두 건 중 하나만 생성되고 다른 하나는 EMAIL_ALREADY_EXISTS로 번역.
3. 설정의 실제 반환 열 전체와 같은 조건의 EXPLAIN ANALYZE에서 기존 `ix_settings_work_kind_id` 사용 확인.

마지막 실행 계획의 핵심:

```text
Limit: 21 row(s) ... actual time=0.0386..0.142 rows=21 loops=1
Index range scan ... using ix_settings_work_kind_id ... (reverse)
... actual time=0.0376..0.14 rows=21 loops=1
```

검증 회차마다 비교용 작품 21개에 설정 100개씩을 추가하고 페이지 크기 20의 size+1 조회를 확인했다. 위 시간은 격리된 로컬 데이터의 한 번의 계획 결과이며 운영 지연 시간이나 부하 성능을 의미하지 않는다. 이 경로에는 추가 인덱스가 필요하다는 근거가 없어 기존 복합 인덱스를 유지했다.

### 실제 화면

실제 Spring Boot 검증 서버는 8080만 사용했고, H2 임시 데이터와 Vite 화면으로 조작했다. 설정 25개를 준비해 처음 20개, 더 보기 후 25개 전체·마지막 버튼 제거, 다른 필터의 빈 결과, 전체 필터 복귀 시 커서 초기화, 마지막 설정 선택을 확인했다. 외부 Gemini 호출은 수행하지 않았다.

화면 증거는 [로컬 화면 이미지](C:/Users/kys14/.codex/visualizations/2026/10/26/01a175e0-1752-71b3-9d6b-2d5888a9e9af/settings-cursor-verification.jpg)에 저장했다. 검증용 서버와 임시 DB는 종료·정리했다.

## 운영 범위와 적용 선택

- V7은 공유 접수 행·상태 제약을, V8은 worker_id·lease_until과 복구·보존 조회 인덱스를 추가한다. 기존 Flyway 경로로 적용한다.
- 원고 보존 기간은 임의로 정하지 않았다. `ANALYSIS_JOB_RETENTION=0s`가 기본이며 자동 삭제는 비활성이다.
- 작업 취소는 인터럽트를 요청한다. 실제 공급자의 즉시 중단·과금 중단은 보장하지 않는다. 테스트 대역으로 로컬 취소 전파를 검증했다.
- 접수 한도와 lease는 공유 DB를 사용한다. 공급자 슬롯·인증 시도 제한·세션은 프로세스 단위이며 다중 서버의 공유 제한·공유 세션은 배포 구성에서 별도로 선택한다.
- Workspace는 현재 API·편집 버전 계약의 JSON 집합 저장을 유지한다. 이번에는 잠금 밖 직렬화·검증과 응답 재해석 제거를 적용했다. 회차·판단별 테이블 분리와 부분 갱신은 원고 버전·근거 보존 모델 및 실제 저장 부하를 확인해야 하는 후속 설계 선택이다.
- 검색의 문자열 포함 의미를 바꾸는 full-text 전환이나 모든 필터 조합의 인덱스 증설은 수행하지 않았다. 확인한 실행 계획과 기존 API 의미에 맞춘 동적 조건·커서 조회를 적용했다.

## 구현 커밋

| 커밋 | 주요 구현 |
| --- | --- |
| 30d41d7 | 분석 접수·상태 전환 동시성과 실패 처리 |
| 46c04b3 | 도메인 converter·작품 권한·Workspace 책임 분리 |
| c8f73c2 | 취소 전파·lease 복구·공급자 슬롯 대기 |
| 8d7be68 | Analysis 파일 통합·명시적 타입·읽기 흐름 |
| cf46cb4 | Analysis 계약·잠금·멱등성·복구 주석 |
| c0c5f31 | 공통 오프셋·커서 페이징과 프런트엔드 적용 |
| b17f0f2 | 인증 재조회 제거·세션·이메일 정책 통합 |
| 50fd915 | 인증 요청·해싱 제한과 필터 응답 공통화 |
| 2f010e9 | 격리 MySQL 검증과 안전한 DB 오류 로깅 |
| 387e92d | 명시적 import·메서드 계약·가독성 최종 통일 |

구현 전 점검 보고서와 본 검증 기록은 별도 문서 커밋으로 보존한다.

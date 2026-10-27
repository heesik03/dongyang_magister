# 백엔드 실행 설정

## Gemini API

1. `backend/.env.example`을 `backend/.env`로 복사한다.
2. `GEMINI_API_KEY`에 실제 키를 입력한다. Gemini 3.5 Flash-Lite의 모델 ID는 `gemini-3.5-flash-lite`이며 `.env.example`에 반영되어 있다.
3. 실행 작업 디렉터리를 `backend/`로 설정하고 `./gradlew bootRun`(Windows: `.\gradlew.bat bootRun`)을 실행한다.

`.env`는 Spring Config Import로 Properties 형식으로 읽는다. `KEY=value` 형식으로 작성하고 `export`와 값의 따옴표를 사용하지 않는다. `.env`는 Git에 포함하지 않는다. OS 환경 변수로 동일한 이름의 값을 지정하면 파일 값보다 우선한다. 키나 모델이 누락되거나 타임아웃이 유효하지 않으면 애플리케이션 시작이 실패한다.

`GEMINI_TIMEOUT` 기본값은 `30s`이며 1ms 이상, 2147483647ms 이하여야 한다. SDK 자동 재시도는 비활성화한다. 타임아웃 이후 공급자가 이미 생성 작업을 수행했을 수 있으므로 업무 계층에서 무조건 재시도하지 않는다.

업무 Service에서 `GeminiClient`를 생성자 주입하고 `generateText(prompt)`를 호출한다. 출력 한도가 필요하면 `generateText(prompt, maxOutputTokens)`를 사용한다. 공급자 응답이 비어 있거나 오류가 발생하면 `GeminiApiException`의 오류 코드로 처리한다. 프롬프트와 응답 본문, API 키를 로그로 기록하지 않는다.

공식 SDK `com.google.genai:google-genai:1.76.0`을 고정하여 사용한다. Java 21에서 빌드 검증하며 SDK 오류와 응답 형식은 `global.gemini` 내부에서 처리한다. SDK 업데이트 시 API 및 전이 의존성 호환성을 재검증한다.

- SDK 문서: https://github.com/googleapis/java-genai
- Spring 설정 문서: https://docs.spring.io/spring-boot/reference/features/external-config.html
- Gemini 3.5 Flash-Lite 모델 ID: https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite

## Controller 호출 예시

`domain/gemini`의 `GeminiController → GeminiService → global.gemini.GeminiClient` 흐름으로 호출한다. DB 저장이나 작품 분석 로직은 포함하지 않는 연결 확인용 예시이다.

서버는 `127.0.0.1`에 바인딩된다. `backend/`에서 다음 명령으로 실행한다. 별도로 기존 MySQL 연결 설정도 준비해야 한다.

```powershell
.\gradlew.bat bootRun
```

요청:

```http
POST http://localhost:8080/api/gemini
Content-Type: application/json

{"prompt":"연결 확인용으로 안녕하세요라고 짧게 답해줘."}
```

PowerShell 호출은 아래 세션 인증 절차에서 얻은 세션과 CSRF 토큰을 함께 사용합니다. 인증 없는 Gemini 요청은 거절됩니다.

성공은 HTTP 200과 `{"success":true,"code":"SUCCESS","message":"요청이 성공했습니다.","data":{"text":"안녕하세요!"},"errors":[]}` 형태로 응답한다. 실제 텍스트는 모델에 따라 달라진다. 요청에 모델이나 API 키를 전달하지 않으며 서버 설정을 사용한다. 기존 `text` 필드를 사용하던 클라이언트는 `data.text`를 읽도록 변경한다.

- `prompt`: 필수, 공백 불가, 최대 2,000자. 출력은 최대 1,024토큰.
- 호출 제한: 프로세스 내 동시 2건, 사용자별 시작 간격 최소 5초. 일반 생성은 초과 시 429이며, 접수된 비동기 분석은 공급자에 전송하기 전에 슬롯을 기다린다. 입력 오류·잘못된 JSON은 400.
- 공급자 인증 실패·요청 거절·빈 응답은 502, 공급자 호출 제한·서버 오류는 503, 타임아웃은 504. 실패 응답은 `success=false`, 오류 `code`와 안전한 `message`, `data=null`, 필드 오류 목록 `errors`를 포함한다.
- 세션 인증과 CSRF 토큰이 필요하다. 공급자 슬롯·인증 요청 제한은 프로세스 단위이며 여러 인스턴스 운영 시 앞단의 공유 호출 제한도 설정한다.

## Swagger / OpenAPI

Spring Boot 4.1에 맞는 `springdoc-openapi-starter-webmvc-ui:3.1.1`을 사용한다. 모든 YAML 설정은 `application.yaml`에서 관리하며 Swagger UI와 OpenAPI 문서는 기본 실행에서 활성화된다.

```powershell
.\gradlew.bat bootRun
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- `/api/**` 경로를 문서화하며 Gemini 요청·응답 예시를 제공한다. `Try it out` 후 `Execute`를 누르면 실제 Gemini 요청이 실행된다.
- `global/config/SwaggerConfig`에서 제목·설명·버전을 관리한다. 인증 방식이 확정되면 해당 방식의 보안 스키마를 추가한다.
- 엔드포인트 문서는 `domain/gemini/docs/GeminiControllerDocs`, DTO 필드 설명은 `GeminiRequestDocs`와 `GeminiResponseDocs`에서 관리한다. Controller와 DTO가 해당 인터페이스를 구현하며 런타임 매핑과 입력 검증은 구현 클래스에 유지한다.
- `global/exception/GlobalExceptionHandler`가 전체 Controller의 오류를 `ApiResponse`와 `application/json`으로 처리한다. 입력 검증은 `VALIDATION_ERROR`와 필드별 오류, 잘못된 요청은 `INVALID_REQUEST`, 예상하지 못한 오류는 `INTERNAL_SERVER_ERROR`로 응답한다. 기타 Spring MVC 오류는 HTTP 상태 이름을 사용한다. 실제 HTTP 상태 코드와 `Allow` 같은 응답 헤더를 유지하고 내부 오류 메시지와 거절된 입력값은 포함하지 않는다.
- `CommonErrorCode`와 `GeminiErrorCode`가 공통 `ErrorCode` 계약을 구현한다. 일반 업무 오류는 `new CustomException(CommonErrorCode.TOO_MANY_REQUESTS)`, Gemini 오류는 `new GeminiApiException(GeminiErrorCode.TIMEOUT)`처럼 생성한다. 오류 메시지는 서버에서 정의하며 공급자 오류 본문은 보존하지 않는다.
- 성공 응답은 Controller에서 `ApiResponse.success(data)`로 생성한다. 예외 핸들러는 Spring의 null 계약에 맞춰 `@NullMarked`를 적용하며 null을 허용하는 인자와 반환값만 `@Nullable`로 선언한다.
- 공식 호환성 및 의존성 문서: https://springdoc.org/

## 검증 명령

`backend/`에서 `.\gradlew.bat test`와 `.\gradlew.bat build`를 실행한다. 일반 통합 테스트는 격리된 H2 MySQL 모드와 가짜 Gemini 설정·SDK 테스트 대역을 사용하며 실제 Gemini API 호출이나 과금이 발생하지 않는다. 실제 MySQL 잠금·실행 계획과 공급자 연동은 별도로 검증한다.

MySQL 검증은 별도로 준비한 임시 DB만 사용한다. `MAGISTER_MYSQL_TEST_SCHEMA`에 `magister_refactor_` 뒤 12자리 소문자 16진수 이름을 지정하고 `.\gradlew.bat mysqlTest`를 실행한다. 테스트는 해당 DB에 마이그레이션·검증 데이터를 생성하며 일반 `test`에서는 제외된다. 연결 비밀번호는 로컬 `.env`의 `DB_PASSWORD`에서 읽고 출력하지 않는다. 검증 후에는 자신이 만든 임시 DB만 정리한다.

Hibernate JDBC 오류 전문은 중복 이메일 등 입력값을 포함할 수 있어 직접 출력하지 않는다. 애플리케이션 예외 처리기가 오류 코드·타입·코드 위치·SQLState·벤더 오류 번호를 기록하며 비밀번호·원고·SQL 전문은 기록하지 않는다.

## 사용자·세션 인증

- `POST /api/user/email/check`: 가입 전 이메일 중복 확인 (`available` 반환, CSRF 필요)
- `POST /api/user`: 이메일·필명·비밀번호로 회원가입 (201)
- `GET /api/user/csrf`: 변경 요청의 CSRF 헤더 이름·토큰 조회
- `POST /api/user/login`: 이메일·비밀번호 로그인
- `GET /api/user/me`: 현재 사용자 조회
- `POST /api/user/logout`: 로그아웃 및 세션 무효화 (Spring Security 필터 처리)

변경 요청 전 `/api/user/csrf`를 같은 쿠키 세션으로 호출하고 응답의 `headerName` 헤더에 `token`을 전달합니다. 로그인 후에는 세션 ID가 변경되고 기존 CSRF 토큰이 폐기되므로 다시 조회합니다. 로그아웃 후에도 새 토큰을 조회해야 합니다. CSRF 누락·만료는 403, 미인증 조회는 401입니다.

이메일은 소문자로 저장하며 중복 가입은 409입니다. 필명은 중복을 허용합니다. 비밀번호는 8~128자이며 영문·숫자·특수문자를 포함하고 공백을 허용하지 않습니다. PBKDF2 해시를 저장하며 평문·해시는 응답과 세션에 포함하지 않습니다. 로그인 실패 사유는 계정 존재 여부와 관계없이 같습니다.

세션은 서버 메모리에 저장하고 1시간 미사용 시 만료됩니다. 동시 로그인은 허용하며 서버 재시작 시 재로그인이 필요합니다. 쿠키는 HttpOnly·SameSite=Lax이며 HTTPS 배포 시 `SESSION_COOKIE_SECURE=true`를 설정합니다. 개발은 Vite 프록시를 통해 동일 출처로 호출합니다.

인증 요청은 `auth.rate-limit` 설정으로 제한한다. 기본값은 IP·경로별 1분 동안 로그인 20회, 가입 20회, 이메일 확인 60회이며 가입·로그인의 해싱 작업은 합쳐서 최대 4개다. 초과 시 공통 오류 JSON과 429를 반환한다. 클라이언트 추적 항목은 최대 10,000개이며 만료된다. 임의의 `X-Forwarded-For`는 신뢰하지 않는다. 프록시·다중 서버 운영에서는 신뢰할 프록시 주소와 공유 제한을 별도로 구성한다.

## 분석 작업과 페이징

### 분석 범위 확인과 작업 복원

1. `POST /api/work/{workId}/analysis/preview`에 기존 분석 선택 필드(`requestId/chapterId/chapterRevision/workspaceVersion/settingIds/previousIds/consistency/motivation`)를 보내 실제 전송할 원고·확정 설정·이전 회차·같은 회차의 저장 판단을 확인합니다. 이 요청은 Gemini를 호출하거나 작업을 생성하지 않습니다.
2. 반환한 `previewToken`과 `policyVersion`을 확인한 뒤 같은 선택 필드에 `previewToken/consentPolicyVersion`을 추가해 `POST /analysis`로 접수합니다. 확인 토큰은 사용자·작품·전체 입력·정책·모델·보관 설정에 묶여 10분 동안 유효합니다. 확인 누락은 400, 자료 변경·만료·변조는 409입니다. 기존 클라이언트도 이 계약으로 전환해야 합니다. 이미 접수된 동일 요청의 재전송은 새 동의 없이 기존 작업을 반환합니다.
3. `GET /analysis?chapterId=...&status=RUNNING&page=0&size=10`은 생성 시각·ID 내림차순 이력입니다. size 상한은 100이며 원고·결과 본문을 조회하지 않습니다. 화면은 이 목록과 기존 `GET /analysis/{id}`로 작업을 복원하고, 화면 이동은 서버 작업을 취소하지 않습니다.
4. `GET /analysis/{id}/snapshot`은 고정 입력·개별 자료·현재 자료 변경 여부를 반환합니다. V10 이전 작업은 `legacy=true`로 기존 입력을 보존하며, 기존 입력이 손상되어 복원할 수 없으면 409입니다. 회차를 식별할 수 있는 과거 작업은 이력 필터를 위해 회차 ID·제목만 이관합니다.

작업 접수와 `analysis_sources`의 TARGET/PREVIOUS/SETTING 자료는 같은 트랜잭션으로 저장됩니다. 워커용 `analysis_jobs.input`은 같은 자료의 직렬화 형태이며 수정하지 않습니다. 완료 결과는 작업 자체에 저장되므로 화면이 닫혀 있어도 유지됩니다. 작가 판단은 후속 단계까지 workspace에 별도 저장합니다. 작업 보존 기간 정리 시 개별 스냅샷도 FK CASCADE로 삭제됩니다. 원본 회차·설정의 FK는 RESTRICT이며, 작품 삭제를 구현할 때는 분석 기록을 먼저 삭제해야 합니다. MySQL은 같은 FK 컬럼에 SET NULL 자동 처리와 해당 컬럼의 CHECK 조건을 함께 허용하지 않으므로 원본 종류 제약과 삭제 순서를 명시적으로 유지합니다.

`ANALYSIS_PREVIEW_SIGNING_KEY`를 생략하면 프로세스별 임시 키를 사용하므로 재시작 뒤에는 미리보기를 다시 확인해야 합니다. 여러 서버에서는 같은 32자 이상 비밀 키를 환경 변수로 주입합니다. 키는 프런트엔드·로그에 노출하지 않습니다.

화면은 서버 보관 설정과 현재 삭제 기능의 범위, [Google Gemini 공식 정책](https://ai.google.dev/gemini-api/terms)을 전송 전에 안내합니다. 계정의 유료·무료 구분과 공급자 설정을 추측해 보관 기간이나 학습 제외를 보장하지 않습니다. 이 단계에서는 서비스 자료 삭제 화면을 제공하지 않습니다.

### 근거가 연결된 검토 결과 (이슈 #10, 3단계)

- `GET /api/work/{workId}/analysis/{jobId}/review?page=0&size=20`: 후보 페이지(`candidates`), `legacy`, `stale` 반환. 최대 100개, ID 오름차순. 후보에는 문제 문장·대상 스냅샷 ID·위치·검토 이유·수정 방향과 최대 10개의 설정/이전 원고 근거가 포함됩니다.
- `GET /api/work/{workId}/analysis/{jobId}/source/{sourceId}`: 분석 당시 고정한 원문 반환. 숫자 `id`는 스냅샷 ID, 문자열 `sourceId`는 원래 회차/설정의 키입니다. 다른 작업의 출처와 다른 사용자의 작품은 404, 미인증 401, 잘못된 페이지·ID는 400입니다. 구형 입력 복원 불가는 409입니다.
- 위치는 Java와 JavaScript가 공통으로 사용하는 UTF-16 코드 단위의 `[startOffset,endOffset)`입니다. 인용문·범위·출처·요청한 검토 종류를 모두 검사하며 서로게이트 쌍을 자른 위치도 거절합니다. 하나라도 잘못되면 부분 결과나 빈 성공으로 바꾸지 않고 작업을 실패 처리합니다.
- 예시 공급자 결과: `{"reviews":[{"type":"설정 충돌 후보","quote":"마법 사용","startOffset":0,"endOffset":5,"evidences":[{"sourceType":"SETTING","sourceKey":"1","quote":"마법 불가","startOffset":0,"endOffset":5,"explanation":"능력 제약"}],"reason":"변화의 계기 확인","suggestion":"계기 설명 보완"}]}`. `PREVIOUS` 근거는 선택한 이전 회차 키를 사용합니다. 근거 없는 단정은 피하고 빈 후보를 작품 전체의 통과 판정으로 해석하지 않습니다.
- Flyway V11은 `review_candidates`, `review_evidences`, `analysis_jobs.result_format_version`을 추가합니다. 작업 완료와 후보·근거 저장은 같은 트랜잭션입니다. 복합 FK로 다른 분석의 스냅샷 연결을 차단하고 작업 삭제 시 함께 삭제합니다. 기존 JSON 결과는 보존하되 없는 위치를 만들어 이관하지 않습니다(`legacy=true`).
- 화면은 반복 문장도 검증된 위치를 선택합니다. 현재 원고와 버전이 같으면 해당 회차로 이동하고, 변경된 원고·설정은 읽기 전용 스냅샷을 엽니다. 대상 원고·이전 회차·설정 변경은 이전 결과로 표시합니다.
- 결과 JSON의 기존 호환 필드 `settingId`는 이전 회차만 근거인 경우 `previous:<회차 키>`로 표현합니다. 위치·범위 없는 과거 workspace 판단은 보존만 하고 새로운 후보에 자동 복사하지 않습니다. 현재 판단 저장·다음 회차 반영은 아래 전용 API를 사용합니다. 실제 Gemini 품질 평가는 후속 단계입니다.

### 작가 판단 이력과 다음 분석 반영 (이슈 #10, 4단계)

신규 판단은 workspace 대신 아래 API에 저장합니다. 기존 workspace 판단은 원본을 보존하며, 없는 적용 범위나 정확한 위치를 추측해서 이관하지 않습니다. 과거 형식의 결과는 재분석 후 범위를 정해 새 판단을 기록합니다.

| API | 계약 |
| --- | --- |
| `GET /api/work/{workId}/review/{reviewId}/decision` | 최신 판단과 현재 `basisHash`, 근거 변경 `stale`, 인용 위치 재확인 가능 여부 `canReconfirm`. 미저장은 version=0 |
| `PUT /api/work/{workId}/review/{reviewId}/decision` | `status`, `reason`, `expectedVersion`, `mutationId`, `basisHash`, `reconfirm`, `scopes`. 동일 mutation·내용 재전송은 같은 버전 반환 |
| `GET /api/work/{workId}/review/{reviewId}/decision/history?page=0&size=20` | 버전 내림차순 이력, size 상한 100 |
| `GET /api/work/{workId}/decision?chapterId=&status=&page=0&size=20` | 작품 내 최신 판단 목록. 원래 검토 회차·상태 필터, ID 내림차순, size 상한 100 |

판단·범위 예시: `{"status":"의도된 설정","reason":"봉인 해제로 얻은 능력","expectedVersion":0,"mutationId":"writer-choice-1","basisHash":"GET에서 받은 64자리 해시","reconfirm":false,"scopes":[{"scopeType":"CHAPTER_RANGE","fromChapterId":"chapter-2","toChapterId":null,"sceneSourceId":null,"startOffset":null,"endOffset":null,"conditionText":"봉인 해제 이후 주인공의 능력 변화에만 적용"}]}`.

- `의도된 설정`은 사유·적용 범위·조건이 필수입니다. `CHAPTER_RANGE`는 같은 작품의 시작/종료 회차 키이며 종료 null은 이후 회차를 포함합니다. `SCENE`은 해당 후보의 대상 스냅샷 ID와 검증한 문제 위치만 허용합니다. 최대 10개 범위이며 수용·보류에서 범위를 생략하면 해당 장면에만 기록합니다. `미검토`와 빈 scopes는 철회 버전을 추가합니다. 원고·설정은 수정하지 않습니다.
- 본인 작품만 조회·저장합니다. 성공 200, 형식·범위 오류 400, 미인증 401, CSRF 누락 403, 다른 사용자·작품의 후보 404, 다른 탭/멱등 키 충돌 `409 DECISION_CONFLICT`, 근거 변경 `409 DECISION_BASIS_CHANGED`입니다. 공개 해시는 인증 수단이 아닙니다.
- 부모 작품 → 분석 작업 순서로 잠그고 버전을 검사합니다. 판단과 범위는 같은 트랜잭션으로 추가하며 덮어쓰지 않습니다. 프런트엔드는 실패해도 사유·범위를 유지하고, 서버 최신 판단과 비교한 뒤 명시적으로 새 버전을 기준으로 준비합니다. 응답 유실 재시도는 같은 mutation을 유지합니다.
- 현재 대상 원고·관련 근거의 전체 내용/버전/메타데이터가 달라지면 해당 판단은 분석 입력에서 제외됩니다. 인용 위치와 확정 상태가 유지되면 변경 자료를 직접 확인한 후 `reconfirm=true`로 새 버전을 저장할 수 있습니다. 인용 자체가 달라졌으면 재분석하며 철회는 계속 가능합니다.
- 미리보기/접수는 최신 유효 판단 중 적용 회차·장면이 맞고 원래 근거가 모두 현재 선택에 포함된 것만 `input.decisionReferences`에 고정합니다. 조건·원래 문제 문장·관련 인용·정확한 판단 버전과 범위를 함께 전송하며, 최대 100개를 넘으면 400으로 범위를 제한하도록 합니다. 미리보기 이후 판단이 변경·철회되면 접수를 409로 거절합니다.
- 판단은 관련 상황의 참고 정보이며 후보를 자동 제거하거나 새 후보의 처리 상태로 복사하지 않습니다. 상충하는 판단도 임의로 우선순위를 정하지 않고 프롬프트에서 확인 질문을 요구합니다. 실제 모델의 조건 해석·오탐 평가는 5단계에서 확인합니다.
- Flyway V12는 `review_decisions`, `review_decision_scopes`, `analysis_decision_references`를 추가합니다. `(review_id,decision_version)`와 `(review_id,mutation_id)`가 유일합니다. 실제 전송한 참조는 FK로 추적하며 판단 수정 후에도 당시 입력·참조는 유지합니다. 판단 이력이 있는 작업은 자동 보관 정리에서 제외하고, 삭제 직전에도 다시 검사합니다. 작품 삭제 시 참조 작업을 먼저 삭제하는 의존 순서가 필요합니다.

이 단계의 검증은 변경 영향 범위로 제한합니다. 백엔드는 `DecisionFlowTests`, `AnalysisFlowTests`, `AnalysisJobLifecycleTests`, `WorkspaceApiTests`, MySQL은 `MySqlRefactoringTests.decisionVersionsScopesAndReferencesOnMySql`, 프런트엔드는 `decision-flow`, `domainClients`, `review-flow`, `analysis-flow` 테스트를 선택 실행합니다.

### 회차별 원고 저장

`/api/work/{workId}/chapter`에서 POST(201)·GET 목록(200), `/{chapterId}`에서 GET·PUT(200)을 제공합니다. 로그인한 소유자만 접근하고 쓰기 요청에는 CSRF가 필요합니다. 목록은 회차 번호 오름차순이며 `page=0&size=20`, size 상한은 100입니다. 작품당 최대 100회차, 제목 100자, 본문 200,000자를 허용합니다.

생성 예시: `{"id":"chapter-1","title":"1화","content":"원고","mutationId":"create-1"}`. 신규 공개 ID는 영문·숫자·밑줄·하이픈 1~64자입니다. 응답은 `id/chapterNo/title/content/revision/version/updatedAt/workVersion`을 포함합니다.

저장 예시: `{"title":"1화","content":"수정 원고","version":0,"mutationId":"save-1"}`. 같은 mutationId·내용을 다시 보내면 중복 저장하지 않습니다. 이전 버전이나 같은 키의 다른 내용은 409, 입력 오류는 400, 미인증은 401, CSRF 실패는 403, 타인 작품·없는 회차는 404입니다. 원고의 version은 다른 회차·검토 판단·사건 저장과 독립적입니다.

Flyway V9는 기존 workspace JSON의 회차 ID·순서·본문·revision을 `chapters`로 이관하고 검토·사건 참조와 workspace 버전을 보존합니다. 물리 PK와 작품별 공개 ID를 구분해 서로 다른 작품의 같은 ID도 보존합니다. 이후 원고의 저장 원본은 `chapters` 한 곳입니다. workspace 조회와 기존 분석은 이 테이블의 현재 원고를 합쳐 읽습니다.

새 workspace 저장은 `separateChapters:true`와 빈 `content.chapters`를 보내 검토·사건만 저장합니다. 구형 workspace 쓰기는 최초 빈 작품의 초안 가져오기만 허용하고, 이후 회차 변경은 409로 거절합니다. 기존 클라이언트는 새 회차 API로 전환해야 합니다. 새 분석 요청은 저장된 `chapterRevision`을 함께 보내 최신 원고 여부를 검사하며, 참고 회차는 대상보다 앞선 회차만 허용합니다.

적용된 V1~V9 마이그레이션은 DB 이력 검증과 새 환경 구축에 필요하므로 유지합니다. 임시 스크립트는 검증 후 제거합니다. 마이그레이션 파일 삭제는 기준 스키마 통합과 기존 DB 전환이 완료된 뒤 진행해야 합니다.

`analysis.execution`의 기본값은 실행 스레드 2개, 대기 8개, 전체 활성 접수 10개, 사용자별 활성 접수 1개다. 접수 검사는 공유 DB 행 잠금으로 직렬화하며 동일 작품의 같은 `requestId`·본문은 기존 작업을 반환한다.

작업은 프로세스 ID와 2분 lease를 가진다. 실제 로컬 실행기가 보유한 작업만 10초마다 연장하고, 만료된 미완료 작업만 실패 처리한다. 새 서버가 시작되었다고 다른 서버의 정상 작업을 실패시키지 않는다. 서버 시각은 동기화해야 한다. 취소는 DB 커밋 후 큐 제거·인터럽트로 전파하지만 SDK와 공급자의 즉시 중단·과금 중단을 보장하지 않는다.

`ANALYSIS_JOB_RETENTION` 기본값 `0s`는 자동 삭제 비활성이다. 운영자가 보존 정책을 정해 양수 기간으로 설정하면 그 기간보다 오래된 종료 작업을 100건씩 정리한다. 해당 작업의 멱등 키도 함께 만료되므로 보존 기간은 재요청 유효기간이기도 하다.

- 작품 목록과 기존 설정 목록은 `global.pagination.OffsetPageResponse`의 `items/page/size/totalElements/totalPages`를 유지한다.
- `GET /api/work/{workId}/setting/scroll?size=20&cursor=...`은 `items/hasNext/nextCursor`를 반환한다. `size`는 1~100이며 첫 요청은 cursor를 생략한다.
- 설정 커서는 ID 내림차순 위치다. 다른 사용자·작품·종류·상태 필터에 재사용하면 400이며 권한도 매 요청 재검사한다. 필터 변경 시 커서를 버리고 처음부터 조회한다.
- 설정 더 보기는 count 없이 size+1개만 읽는다. 작품 부분 검색은 기존 문자열 포함 의미를 유지하며 지정된 필터만 동적 질의에 포함한다.

`users` 스키마는 Flyway Java 마이그레이션으로 생성하고 Hibernate는 `validate`로 확인합니다. Spring Security·Flyway·테스트 H2 버전은 Spring Boot 4.1.1의 의존성 관리에 따릅니다. 테스트는 `test` 프로필에서 격리된 H2의 MySQL 모드와 실제 마이그레이션을 사용하며, 실제 MySQL 동작은 E2E로 별도 검증합니다.

```powershell
# 비밀 값은 .env 또는 OS 환경 변수로 설정
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat bootRun
```

Java 코드는 google-java-format 1.28.0의 AOSP 스타일(4칸 들여쓰기)로 정리했습니다. 주요 인증 로직은 `SessionAuthenticationService`, DB 저장은 `UserService`, 접근 정책은 `SecurityConfig`에서 관리합니다. Controller는 입력 검증과 응답 조립을 담당합니다.

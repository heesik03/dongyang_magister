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

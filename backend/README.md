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

PowerShell 호출:

```powershell
$body = @{ prompt = '연결 확인용으로 안녕하세요라고 짧게 답해줘.' } | ConvertTo-Json
Invoke-RestMethod -Uri 'http://localhost:8080/api/gemini' -Method Post -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

성공은 HTTP 200과 `{"text":"안녕하세요!"}` 형태로 응답한다. 실제 텍스트는 모델에 따라 달라진다. 요청에 모델이나 API 키를 전달하지 않으며 서버 설정을 사용한다.

- `prompt`: 필수, 공백 불가, 최대 2,000자. 출력은 최대 1,024토큰.
- 호출 제한: 프로세스 전체에서 동시 1건, 시작 간격 최소 5초. 초과 시 429. 입력 오류·잘못된 JSON은 400.
- 공급자 인증 실패·요청 거절·빈 응답은 502, 공급자 호출 제한·서버 오류는 503, 타임아웃은 504. 공급자 오류는 `ProblemDetail`에 `code`를 포함한다.
- 예시 엔드포인트는 인증 없이 호출하는 로컬 개발용이다. 운영 API로 확장할 때 사용자 인증·권한과 분산 호출 제한, 원고 전송 안내를 설계한다.

## Swagger / OpenAPI

Spring Boot 4.1에 맞는 `springdoc-openapi-starter-webmvc-ui:3.1.1`을 사용한다. 모든 YAML 설정은 `application.yaml`에서 관리하며 Swagger UI와 OpenAPI 문서는 기본 실행에서 활성화된다.

```powershell
.\gradlew.bat bootRun
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- `/api/**` 경로를 문서화하며 Gemini 요청·응답 예시를 제공한다. `Try it out` 후 `Execute`를 누르면 실제 Gemini 요청이 실행된다.
- `global/config/SwaggerConfig`에서 제목·설명·버전을 관리한다. 인증 방식이 확정되면 해당 방식의 보안 스키마를 추가한다.
- 공식 호환성 및 의존성 문서: https://springdoc.org/

## 검증 명령

`backend/`에서 `.\gradlew.bat test`와 `.\gradlew.bat build`를 실행한다. 테스트는 가짜 Gemini 설정과 SDK 테스트 대역을 사용하며 실제 Gemini API 호출이나 과금이 발생하지 않는다. 전체 애플리케이션 컨텍스트를 사용하는 테스트에는 별도의 테스트용 MySQL 연결 설정이 필요하다. 실제 API 연동은 유효한 키와 모델을 준비한 후 별도로 확인한다.

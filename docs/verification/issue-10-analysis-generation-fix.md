# #10 Gemini 반전 오탐 수정 및 재검증 (2026-10-27)

## 수정

원고 분석이 짧은 연결 확인과 같은 일반 텍스트 호출을 사용해 모델의 기본 추론 설정에 의존하고 있었다. 구성된 `gemini-3.5-flash-lite`의 기본 추론 수준은 minimal이다. 프롬프트만 보완했을 때는 반전 오탐이나 관련 없는 충돌 누락이 반복 실행에서 남았다.

- 분석 호출을 `GeminiClient.generateAnalysis`로 분리했다. Gemini 3 계열에는 `thinkingLevel=HIGH`를 명시하고, 다른 계열에는 지원되지 않는 thinkingLevel을 보내지 않는다.
- 분석 전용 JSON 응답 스키마에 검토 유형·출처 enum·필수 필드를 지정했다. JSON 형식만 요청했을 때 발생한 `SETSETTING` 출처 오타를 생성 규격에서 제한한다. 처음 범위 제약까지 포함한 스키마는 공급자가 400으로 거절해, 구조와 enum을 지정한 규격으로 정리했다.
- 추론 토큰과 JSON 출력이 같은 상한을 쓰므로 분석 한도를 8,192토큰으로 설정했다. `/api/gemini` 연결 확인은 기존 1,024토큰 설정을 유지한다.
- 후보·근거 개수, 문자열 크기, 실제 선택 출처, UTF-16 구간과 인용문 일치는 기존 서버 검증이 계속 확인한다. 잘못된 출처의 교정·후보 제외·기대값 변경으로 통과시키지 않았다. `SETSETTING`도 서버가 거절하는 회귀 사례에 추가했다.
- 테스트가 운영과 같은 분석 메서드를 사용하도록 맞추고, 표현이 다른 조건 종료 사례와 조건이 계속 유효한 실제 충돌을 추가했다. `geminiTest`의 호출 상한은 10회이며 선택 실행도 지원한다.

설정은 [Google thinking 문서](https://ai.google.dev/gemini-api/docs/generate-content/thinking)와 [구조화 응답 문서](https://ai.google.dev/gemini-api/docs/generate-content/structured-output), 현재 SDK 1.76.0 소스를 확인했다. 의존성·DB 스키마·프런트엔드는 변경하지 않았다.

## 실제 공급자 재검증

최종 코드로 `MAGISTER_RUN_GEMINI_TESTS=true`와 `.\gradlew.bat geminiTest --max-workers=2`를 두 번 연속 실행했다. **각 10/10, 합계 20/20에서 응답 계약과 정의한 의미 기대를 모두 통과했다.** 고정 합성 자료만 전송했다.

| 사례 | 1차(ms) | 2차(ms) | 계약·의미 기대 |
| --- | ---: | ---: | --- |
| normal | 3671 | 3607 | 통과 |
| setting-conflict | 4937 | 4462 | 통과 |
| motivation-gap | 4392 | 5645 | 통과 |
| explicit-twist | 3704 | 2884 | 통과 |
| insufficient-evidence | 2272 | 2883 | 통과 |
| intent-inside-scope | 6289 | 4872 | 통과 |
| intent-unrelated-conflict | 7147 | 6363 | 통과 |
| intent-outside-scope | 4600 | 4661 | 통과 |
| condition-ended-paraphrase | 3640 | 3180 | 통과 |
| condition-still-active | 5491 | 5139 | 통과 |

평균 호출·검증 시간은 1차 4614ms, 2차 4370ms다. 정상·설명된 반전·근거 부족·범위 안의 의도·조건 종료 사례는 빈 후보를 유지했고, 설정 충돌·동기 설명 부족·다른 인물의 무관한 충돌·범위 밖 충돌·유효 조건 위반은 탐지했다. 마지막 두 실행은 각각 음성 5건·양성 5건이다.

수정 중 발견한 enum 오타·중간 의미 누락·요청 거절·공급자 일시 실패도 [평가 JSON](issue-10-analysis-generation-verification.json)에 함께 남겼다. 실패 실행을 성공으로 바꾸거나 여러 실행에서 통과 사례만 모으지 않았다. 이전 7/8 검토 기록은 과거 결과로 보존한다.

## 로컬 검증

- 호출 경로 변경: `test --tests '*GeminiClientTests' --tests '*GeminiServiceTests' --tests '*AnalysisFlowTests' --tests '*AnalysisJobLifecycleTests' --tests '*WorkspaceApiTests' --max-workers=2` — 43개 통과.
- 최종 스키마·추론 설정과 잘못된 출처 거절: `test --tests '*GeminiClientTests' --tests '*ReviewValidationTests' build --max-workers=2` — 17개와 빌드 통과.
- 요청 설정을 캡처해 HIGH·JSON 스키마·8,192토큰, 다른 모델의 thinkingLevel 미전송, 연결 확인 호출의 기존 설정을 확인했다.
- `git diff --check` 통과. 이번 수정에는 프런트엔드·DB 변경이 없어 이전에 통과한 69개 프런트엔드 테스트와 MySQL 7개를 반복하지 않았다.

## 범위

남았던 반전 오탐과 이번 반복 검증에서 확인한 오류를 수정했으므로 PR을 정식 검토 상태로 전환한다. 검증 대상은 구성된 모델과 합성 10개 사례이며, 모든 작품에서 오탐·누락이 없다는 뜻은 아니다. 추론 설정에 따른 시간·토큰 사용량은 입력에 따라 달라지며 공급자 장애는 명시적인 실패로 유지한다.

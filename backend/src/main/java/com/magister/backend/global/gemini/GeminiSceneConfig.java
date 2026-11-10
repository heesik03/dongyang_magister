package com.magister.backend.global.gemini;

import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Schema;

/** 장면 보드 분석 전용 JSON 출력 규격이다. 인용 범위와 참고 카드 ID는 서버가 다시 검증한다. */
final class GeminiSceneConfig {
    private static final int MAX_OUTPUT_TOKENS = 4096;
    private static final Schema RESPONSE_SCHEMA =
            Schema.fromJson(
                    """
                    {
                      "type":"OBJECT",
                      "required":["facts","inferences","gaps","options","references"],
                      "properties":{
                        "facts":{"type":"ARRAY","items":{
                          "type":"OBJECT",
                          "required":["description","quote"],
                          "properties":{
                            "description":{"type":"STRING"},
                            "quote":{"type":"STRING"}
                          }
                        }},
                        "inferences":{"type":"ARRAY","items":{"type":"STRING"}},
                        "gaps":{"type":"ARRAY","items":{"type":"STRING"}},
                        "options":{"type":"ARRAY","items":{"type":"STRING"}},
                        "references":{"type":"ARRAY","items":{
                          "type":"OBJECT",
                          "required":["id","question"],
                          "properties":{
                            "id":{"type":"STRING"},
                            "question":{"type":"STRING"}
                          }
                        }}
                      }
                    }
                    """);

    private GeminiSceneConfig() {}

    static GenerateContentConfig create() {
        return GenerateContentConfig.builder()
                .maxOutputTokens(MAX_OUTPUT_TOKENS)
                .responseMimeType("application/json")
                .responseSchema(RESPONSE_SCHEMA)
                .build();
    }
}

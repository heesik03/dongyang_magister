package com.magister.backend.global.gemini;

import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Schema;
import com.google.genai.types.ThinkingConfig;

/** 공급자의 생성 규격이다. 서버의 AnalysisOutput·인용 검증을 대체하지 않는다. */
final class GeminiAnalysisConfig {
    private static final int MAX_OUTPUT_TOKENS = 8192;
    // 구조·enum은 공급자가 제한하고 크기·개수·실제 인용 범위는 서버가 검증한다.
    // https://ai.google.dev/gemini-api/docs/generate-content/structured-output
    private static final Schema RESPONSE_SCHEMA =
            Schema.fromJson(
                    """
                    {
                      "type":"OBJECT","required":["reviews"],"properties":{
                        "reviews":{"type":"ARRAY","items":{
                          "type":"OBJECT",
                          "required":["type","quote","startOffset","endOffset","evidences","reason","suggestion"],
                          "properties":{
                            "type":{"type":"STRING","enum":["설정 충돌 후보","설명 보완"]},
                            "quote":{"type":"STRING"},
                            "startOffset":{"type":"INTEGER"},
                            "endOffset":{"type":"INTEGER"},
                            "evidences":{"type":"ARRAY","items":{
                              "type":"OBJECT",
                              "required":["sourceType","sourceKey","quote","startOffset","endOffset","explanation"],
                              "properties":{
                                "sourceType":{"type":"STRING","enum":["SETTING","PREVIOUS"]},
                                "sourceKey":{"type":"STRING"},
                                "quote":{"type":"STRING"},
                                "startOffset":{"type":"INTEGER"},
                                "endOffset":{"type":"INTEGER"},
                                "explanation":{"type":"STRING"}
                              }
                            }},
                            "reason":{"type":"STRING"},
                            "suggestion":{"type":"STRING"}
                          }
                        }}
                      }
                    }
                    """);

    private GeminiAnalysisConfig() {}

    /** 추론과 최종 JSON이 출력 상한을 공유한다. Gemini 3만 지원하는 thinkingLevel은 다른 모델에 보내지 않는다. */
    static GenerateContentConfig create(String model) {
        GenerateContentConfig.Builder config =
                GenerateContentConfig.builder()
                        .maxOutputTokens(MAX_OUTPUT_TOKENS)
                        .responseMimeType("application/json")
                        .responseSchema(RESPONSE_SCHEMA);
        // https://ai.google.dev/gemini-api/docs/generate-content/thinking
        if (model.startsWith("gemini-3")) {
            config.thinkingConfig(ThinkingConfig.builder().thinkingLevel("HIGH").build());
        }
        return config.build();
    }
}

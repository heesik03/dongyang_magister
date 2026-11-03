package com.magister.backend.global.gemini;

import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Schema;

/** 설정 검토의 reviews 응답과 사건 추출의 events 응답은 공급자 규격부터 분리한다. */
final class GeminiEventConfig {
    private GeminiEventConfig() {}

    static GenerateContentConfig create() {
        Schema schema =
                Schema.fromJson(
                        """
                        {"type":"OBJECT","required":["events"],"properties":{
                          "events":{"type":"ARRAY","items":{"type":"OBJECT",
                            "required":["title","description","time","characters","chapterId","quote","occurrence"],
                            "properties":{
                              "title":{"type":"STRING"},"description":{"type":"STRING"},
                              "time":{"type":"STRING"},"characters":{"type":"STRING"},
                              "chapterId":{"type":"STRING"},"quote":{"type":"STRING"},
                              "occurrence":{"type":"INTEGER"}
                            }
                          }}
                        }}
                        """);
        return GenerateContentConfig.builder()
                .maxOutputTokens(8192)
                .responseMimeType("application/json")
                .responseSchema(schema)
                .build();
    }
}

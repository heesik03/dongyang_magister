package com.magister.backend.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.magister.backend.domain.gemini.controller.GeminiController;
import com.magister.backend.domain.gemini.service.GeminiService;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSpecPropertiesConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(GeminiController.class)
@Import(SwaggerConfig.class)
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocSpecPropertiesConfiguration.class, SpringDocWebMvcConfiguration.class,
        org.springdoc.webmvc.ui.SwaggerConfig.class, SwaggerUiConfigProperties.class,
        SwaggerUiOAuthProperties.class})
class SwaggerTests {

    @MockitoBean
    private GeminiService service;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void documentsGeminiEndpointAndRequestSchema() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Magister API"))
                .andExpect(jsonPath("$.paths['/api/gemini'].post.summary").value("Gemini 텍스트 생성"))
                .andExpect(jsonPath("$.components.schemas.GeminiRequest.properties.prompt.maxLength")
                        .value(2000))
                .andExpect(jsonPath("$.components.schemas.GeminiRequest.properties.prompt.example")
                        .value("안녕하세요라고 짧게 답해줘."))
                .andExpect(jsonPath("$.components.schemas.GeminiResponse.properties.text.example")
                        .value("안녕하세요!"))
                .andExpect(jsonPath("$.paths['/api/gemini'].post.tags[0]").value("Gemini"))
                .andExpect(jsonPath("$.paths['/api/gemini'].post.responses['502']").exists());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/gemini'].post.responses['200'].content['application/json'].schema['$ref']")
                        .value(containsString("ApiResponse")))
                .andExpect(jsonPath("$.paths['/api/gemini'].post.responses['502'].content['application/json'].schema['$ref']")
                        .value(containsString("ApiResponse")));
    }

    @Test
    void servesSwaggerUiAndConfiguration() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }
}

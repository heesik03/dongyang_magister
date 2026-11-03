package com.magister.backend.domain.chapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.magister.backend.domain.analysis.service.AnalysisDispatcher;
import com.magister.backend.domain.user.entity.User;
import com.magister.backend.domain.user.repository.UserRepository;
import com.magister.backend.domain.user.security.LoginUser;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.domain.work.repository.WorkRepository;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChapterExportImportApiTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired WorkRepository workRepository;
    @MockitoBean AnalysisDispatcher analysisDispatcher;

    private Long workId;
    private UsernamePasswordAuthenticationToken owner;

    @BeforeEach
    void prepare() {
        User user =
                userRepository.saveAndFlush(
                        new User(UUID.randomUUID() + "@example.com", "작가", "hash"));
        Work createdWork =
                workRepository.saveAndFlush(new Work(user, "별의 연대기", "판타지", "중세", "설명"));
        workId = createdWork.getId();
        owner =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(user.getId(), user.getEmail(), user.getName()),
                        null,
                        List.of());
    }

    private String chapterPath() {
        return "/api/work/" + workId + "/chapter";
    }

    private void createChapter(String chapterKey, String title, String content) throws Exception {
        String payload =
                objectMapper.writeValueAsString(
                        Map.of(
                                "id",
                                chapterKey,
                                "title",
                                title,
                                "content",
                                content,
                                "mutationId",
                                "create-" + chapterKey));
        mockMvc.perform(
                        post(chapterPath())
                                .with(authentication(owner))
                                .with(csrf())
                                .contentType("application/json")
                                .content(payload))
                .andExpect(status().isCreated());
    }

    @Test
    void exportsSingleChapterInTxtAndMdKeepingRawMarkdownAndMetadata() throws Exception {
        String markdown =
                "### 첫 번째 장면\n\n**빛**이 ~~어둠~~을 가른다.\n> 오래된 예언\n\n- 첫 단서\n1. 출발";
        createChapter("ch-1", "1화. 시작의 날", markdown);

        MvcResult txtResult =
                mockMvc.perform(
                                get(chapterPath() + "/ch-1/export")
                                        .with(authentication(owner))
                                        .param("format", "txt"))
                        .andExpect(status().isOk())
                        .andReturn();

        assertThat(txtResult.getResponse().getContentType()).startsWith("text/plain");
        assertThat(txtResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment")
                .contains(".txt");

        String exportedTxt =
                new String(txtResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(exportedTxt)
                .startsWith("제목: 별의 연대기\n장르: 판타지\n회차: 1화. 시작의 날\n---\n\n")
                .contains("### 첫 번째 장면")
                .contains("**빛**이 ~~어둠~~을 가른다.");

        MvcResult mdResult =
                mockMvc.perform(
                                get(chapterPath() + "/ch-1/export")
                                        .with(authentication(owner))
                                        .param("format", "md"))
                        .andExpect(status().isOk())
                        .andReturn();

        assertThat(mdResult.getResponse().getContentType()).startsWith("text/markdown");
        String exportedMd =
                new String(mdResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(exportedMd).isEqualTo(exportedTxt);
    }

    @Test
    void exportsPdfAndDocxConvertingMarkdownHeadingsIntoStyledTitles() throws Exception {
        String markdown = "# 대제목\n\n### 소제목\n\n일반 본문 문장입니다.";
        createChapter("ch-1", "1화. 서막", markdown);

        MvcResult pdfResult =
                mockMvc.perform(
                                get(chapterPath() + "/ch-1/export")
                                        .with(authentication(owner))
                                        .param("format", "pdf"))
                        .andExpect(status().isOk())
                        .andReturn();

        assertThat(pdfResult.getResponse().getContentType()).isEqualTo("application/pdf");
        byte[] pdfBytes = pdfResult.getResponse().getContentAsByteArray();

        Map<String, Float> lineFontSizes = new HashMap<>();
        String extractedPdfText;
        try (PDDocument pdfDocument = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper fontInspectingStripper =
                    new PDFTextStripper() {
                        @Override
                        protected void writeString(
                                String text, List<TextPosition> textPositions) throws IOException {
                            super.writeString(text, textPositions);
                            if (text != null && !text.isBlank() && !textPositions.isEmpty()) {
                                lineFontSizes.put(
                                        text.strip(), textPositions.getFirst().getFontSizeInPt());
                            }
                        }
                    };
            extractedPdfText = fontInspectingStripper.getText(pdfDocument);
        }

        assertThat(extractedPdfText)
                .contains("제목: 별의 연대기")
                .contains("장르: 판타지")
                .contains("회차: 1화. 서막")
                .contains("대제목")
                .contains("소제목")
                .contains("일반 본문 문장입니다.")
                .doesNotContain("# 대제목")
                .doesNotContain("### 소제목");

        assertThat(lineFontSizes.get("대제목")).isEqualTo(22.0f);
        assertThat(lineFontSizes.get("소제목")).isEqualTo(15.0f);
        assertThat(lineFontSizes.get("일반 본문 문장입니다.")).isEqualTo(11.0f);

        MvcResult docxResult =
                mockMvc.perform(
                                get(chapterPath() + "/ch-1/export")
                                        .with(authentication(owner))
                                        .param("format", "docx"))
                        .andExpect(status().isOk())
                        .andReturn();

        assertThat(docxResult.getResponse().getContentType())
                .isEqualTo(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        byte[] docxBytes = docxResult.getResponse().getContentAsByteArray();

        try (XWPFDocument docxDocument = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            List<String> paragraphTexts = new ArrayList<>();
            XWPFParagraph h3Paragraph = null;
            for (XWPFParagraph paragraph : docxDocument.getParagraphs()) {
                paragraphTexts.add(paragraph.getText());
                if ("소제목".equals(paragraph.getText())) {
                    h3Paragraph = paragraph;
                }
            }
            assertThat(paragraphTexts)
                    .contains("제목: 별의 연대기", "장르: 판타지", "회차: 1화. 서막", "대제목", "소제목", "일반 본문 문장입니다.")
                    .doesNotContain("### 소제목");
            assertThat(h3Paragraph).isNotNull();
            assertThat(h3Paragraph.getStyle()).isEqualTo("Heading3");
            assertThat(h3Paragraph.getRuns().getFirst().getFontSizeAsDouble()).isEqualTo(15.0);
        }
    }

    @Test
    void exportsFullWorkInChapterOrderAndRejectsEmptyWork() throws Exception {
        mockMvc.perform(
                        get("/api/work/" + workId + "/export")
                                .with(authentication(owner))
                                .param("format", "txt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_EXPORT_CHAPTERS"));

        createChapter("c1", "1화. 만남", "### 1화 소제목\n첫 회차 본문");
        createChapter("c2", "2화. 여정", "### 2화 소제목\n둘째 회차 본문");

        MvcResult fullTxt =
                mockMvc.perform(
                                get("/api/work/" + workId + "/export")
                                        .with(authentication(owner))
                                        .param("format", "txt"))
                        .andExpect(status().isOk())
                        .andReturn();

        String content =
                new String(fullTxt.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(content)
                .startsWith("제목: 별의 연대기\n장르: 판타지\n---\n\n")
                .contains("=== 1화. 만남 ===")
                .contains("=== 2화. 여정 ===");
        assertThat(content.indexOf("1화. 만남")).isLessThan(content.indexOf("2화. 여정"));

        mockMvc.perform(
                        get("/api/work/" + workId + "/export")
                                .with(authentication(owner))
                                .param("format", "pdf"))
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/work/" + workId + "/export")
                                .with(authentication(owner))
                                .param("format", "docx"))
                .andExpect(status().isOk());
    }

    @Test
    void importsTxtMdPdfAndDocxFilesWithHeaderSeparationAndUpdatesChapters() throws Exception {
        String originalMarkdown = "### 귀환의 장\n\n검을 거두고 성문으로 걸어갔다.";
        createChapter("origin", "1화. 귀환", originalMarkdown);

        byte[] exportedTxtBytes =
                mockMvc.perform(
                                get(chapterPath() + "/origin/export")
                                        .with(authentication(owner))
                                        .param("format", "txt"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        MockMultipartFile txtUpload =
                new MockMultipartFile(
                        "file", "exported.txt", "text/plain", exportedTxtBytes);

        mockMvc.perform(
                        multipart(chapterPath() + "/import/parse")
                                .file(txtUpload)
                                .with(authentication(owner))
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workTitle").value("별의 연대기"))
                .andExpect(jsonPath("$.data.genre").value("판타지"))
                .andExpect(jsonPath("$.data.chapterTitle").value("1화. 귀환"))
                .andExpect(jsonPath("$.data.content").value(originalMarkdown))
                .andExpect(jsonPath("$.data.format").value("txt"));

        byte[] exportedPdfBytes =
                mockMvc.perform(
                                get(chapterPath() + "/origin/export")
                                        .with(authentication(owner))
                                        .param("format", "pdf"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        MockMultipartFile pdfUpload =
                new MockMultipartFile(
                        "file", "exported.pdf", "application/pdf", exportedPdfBytes);

        MvcResult createdFromPdf =
                mockMvc.perform(
                                multipart(chapterPath() + "/import")
                                        .file(pdfUpload)
                                        .param("id", "imported-pdf")
                                        .with(authentication(owner))
                                        .with(csrf()))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.data.id").value("imported-pdf"))
                        .andExpect(jsonPath("$.data.title").value("1화. 귀환"))
                        .andExpect(jsonPath("$.data.chapterNo").value(2))
                        .andReturn();

        String importedPdfContent =
                objectMapper
                        .readTree(createdFromPdf.getResponse().getContentAsString())
                        .path("data")
                        .path("content")
                        .asString();
        assertThat(importedPdfContent)
                .doesNotContain("제목: 별의 연대기")
                .doesNotContain("장르: 판타지")
                .contains("귀환의 장")
                .contains("검을 거두고 성문으로 걸어갔다.");

        byte[] exportedDocxBytes =
                mockMvc.perform(
                                get(chapterPath() + "/origin/export")
                                        .with(authentication(owner))
                                        .param("format", "docx"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsByteArray();

        MockMultipartFile docxUpload =
                new MockMultipartFile(
                        "file",
                        "exported.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        exportedDocxBytes);

        mockMvc.perform(
                        multipart(chapterPath() + "/import/parse")
                                .file(docxUpload)
                                .with(authentication(owner))
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapterTitle").value("1화. 귀환"))
                .andExpect(jsonPath("$.data.content").value("### 귀환의 장\n검을 거두고 성문으로 걸어갔다."));

        MockMultipartFile mdReplacement =
                new MockMultipartFile(
                        "file",
                        "2화_초안.md",
                        "text/markdown",
                        "### 수정된 장면\n\n새로운 전개".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(
                        multipart(HttpMethod.PUT, chapterPath() + "/origin/import")
                                .file(mdReplacement)
                                .param("version", "0")
                                .param("mutationId", "import-update-1")
                                .with(authentication(owner))
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.revision").value(2))
                .andExpect(jsonPath("$.data.content").value("### 수정된 장면\n\n새로운 전개"));

        mockMvc.perform(
                        multipart(HttpMethod.PUT, chapterPath() + "/origin/import")
                                .file(mdReplacement)
                                .param("version", "0")
                                .param("mutationId", "import-update-conflict")
                                .with(authentication(owner))
                                .with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void enforcesOwnershipCsrfAndValidationOnExportAndImport() throws Exception {
        createChapter("c1", "1화", "원고 내용");
        UsernamePasswordAuthenticationToken otherUser =
                UsernamePasswordAuthenticationToken.authenticated(
                        new LoginUser(-99L, "other@example.com", "타인"), null, List.of());

        mockMvc.perform(get(chapterPath() + "/c1/export"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(chapterPath() + "/c1/export").with(authentication(otherUser)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/work/" + workId + "/export").with(authentication(otherUser)))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        get(chapterPath() + "/c1/export")
                                .with(authentication(owner))
                                .param("format", "exe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_EXPORT_FORMAT"));

        MockMultipartFile validTxt =
                new MockMultipartFile(
                        "file", "draft.txt", "text/plain", "내용".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(
                        multipart(chapterPath() + "/import")
                                .file(validTxt)
                                .with(authentication(owner)))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        multipart(chapterPath() + "/import")
                                .file(validTxt)
                                .with(authentication(otherUser))
                                .with(csrf()))
                .andExpect(status().isNotFound());

        MockMultipartFile unsupportedFile =
                new MockMultipartFile(
                        "file",
                        "malware.exe",
                        "application/octet-stream",
                        new byte[] {1, 2, 3});
        mockMvc.perform(
                        multipart(chapterPath() + "/import")
                                .file(unsupportedFile)
                                .with(authentication(owner))
                                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMPORT_FORMAT"));

        MockMultipartFile corruptedPdf =
                new MockMultipartFile(
                        "file",
                        "broken.pdf",
                        "application/pdf",
                        "not-a-valid-pdf".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(
                        multipart(chapterPath() + "/import")
                                .file(corruptedPdf)
                                .with(authentication(owner))
                                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MANUSCRIPT_FILE"));

        MockMultipartFile oversizedManuscript =
                new MockMultipartFile(
                        "file",
                        "too_long.txt",
                        "text/plain",
                        "가".repeat(200_001).getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(
                        multipart(chapterPath() + "/import")
                                .file(oversizedManuscript)
                                .with(authentication(owner))
                                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MANUSCRIPT_TOO_LONG"));
    }
}

package com.qiniu.voice_calendar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.voice_calendar.dto.CreateTagRequest;
import com.qiniu.voice_calendar.dto.TagVO;
import com.qiniu.voice_calendar.dto.UpdateTagRequest;
import com.qiniu.voice_calendar.exception.BusinessException;
import com.qiniu.voice_calendar.exception.GlobalExceptionHandler;
import com.qiniu.voice_calendar.service.TagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TagController 接口测试")
class TagControllerTest {

    @Mock
    private TagService tagService;

    @InjectMocks
    private TagController tagController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(tagController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(1L);
        when(auth.getDetails()).thenReturn("testuser");
        SecurityContext ctx = org.mockito.Mockito.mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    // ══════════════════════════════════════
    //   GET /api/tags
    // ══════════════════════════════════════

    @Nested
    @DisplayName("GET /api/tags — 获取标签列表")
    class ListTags {

        @Test
        @DisplayName("正常返回标签列表 + 事件数")
        void shouldReturnTagsWithEventCount() throws Exception {
            List<TagVO> tags = List.of(
                    TagVO.builder().id(1L).name("工作").color("#409eff").eventCount(5L).build(),
                    TagVO.builder().id(2L).name("学习").color("#67c23a").eventCount(2L).build()
            );
            when(tagService.listTags(1L)).thenReturn(tags);

            mockMvc.perform(get("/api/tags"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("工作"))
                    .andExpect(jsonPath("$.data[0].eventCount").value(5))
                    .andExpect(jsonPath("$.data[1].id").value(2));
        }

        @Test
        @DisplayName("空列表返回 []")
        void shouldReturnEmptyList() throws Exception {
            when(tagService.listTags(1L)).thenReturn(List.of());

            mockMvc.perform(get("/api/tags"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    // ══════════════════════════════════════
    //   POST /api/tags
    // ══════════════════════════════════════

    @Nested
    @DisplayName("POST /api/tags — 创建标签")
    class CreateTag {

        @Test
        @DisplayName("正常创建 → 200")
        void shouldCreateTag() throws Exception {
            TagVO created = TagVO.builder().id(3L).name("健康").color("#e6a23c").eventCount(0L).build();
            when(tagService.createTag(eq(1L), any(CreateTagRequest.class))).thenReturn(created);

            CreateTagRequest req = new CreateTagRequest();
            req.setName("健康");
            req.setColor("#e6a23c");

            mockMvc.perform(post("/api/tags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("创建成功"))
                    .andExpect(jsonPath("$.data.id").value(3))
                    .andExpect(jsonPath("$.data.name").value("健康"));
        }

        @Test
        @DisplayName("标签名重复 → 409")
        void shouldRejectDuplicateName() throws Exception {
            when(tagService.createTag(eq(1L), any(CreateTagRequest.class)))
                    .thenThrow(new BusinessException(409, "标签名已存在"));

            CreateTagRequest req = new CreateTagRequest();
            req.setName("工作");

            mockMvc.perform(post("/api/tags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.message").value("标签名已存在"));
        }

        @Test
        @DisplayName("标签名为空 → 400")
        void shouldRejectEmptyName() throws Exception {
            CreateTagRequest req = new CreateTagRequest();
            req.setName("");

            mockMvc.perform(post("/api/tags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("不传颜色 → 使用默认值")
        void shouldUseDefaultColor() throws Exception {
            TagVO created = TagVO.builder().id(4L).name("运动").color("#909399").eventCount(0L).build();
            when(tagService.createTag(eq(1L), any(CreateTagRequest.class))).thenReturn(created);

            String body = "{\"name\": \"运动\"}";

            mockMvc.perform(post("/api/tags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.color").value("#909399"));
        }
    }

    // ══════════════════════════════════════
    //   PUT /api/tags/{id}
    // ══════════════════════════════════════

    @Nested
    @DisplayName("PUT /api/tags/{id} — 修改标签")
    class UpdateTag {

        @Test
        @DisplayName("重命名 → 200")
        void shouldRename() throws Exception {
            TagVO updated = TagVO.builder().id(1L).name("运动健康").color("#409eff").eventCount(3L).build();
            when(tagService.updateTag(eq(1L), eq(1L), any(UpdateTagRequest.class))).thenReturn(updated);

            UpdateTagRequest req = new UpdateTagRequest();
            req.setName("运动健康");

            mockMvc.perform(put("/api/tags/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("修改成功"))
                    .andExpect(jsonPath("$.data.name").value("运动健康"));
        }

        @Test
        @DisplayName("标签不存在 → 404")
        void shouldReturn404() throws Exception {
            when(tagService.updateTag(eq(1L), eq(999L), any(UpdateTagRequest.class)))
                    .thenThrow(new BusinessException(404, "标签不存在"));

            String body = "{\"color\": \"#f56c6c\"}";

            mockMvc.perform(put("/api/tags/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("标签不存在"));
        }
    }

    // ══════════════════════════════════════
    //   DELETE /api/tags/{id}
    // ══════════════════════════════════════

    @Nested
    @DisplayName("DELETE /api/tags/{id} — 删除标签")
    class DeleteTag {

        @Test
        @DisplayName("正常删除 → 200")
        void shouldDeleteTag() throws Exception {
            mockMvc.perform(delete("/api/tags/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("已删除"));
        }

        @Test
        @DisplayName("标签不存在 → 404")
        void shouldReturn404() throws Exception {
            org.mockito.Mockito.doThrow(new BusinessException(404, "标签不存在"))
                    .when(tagService).deleteTag(1L, 999L);

            mockMvc.perform(delete("/api/tags/999"))
                    .andExpect(jsonPath("$.code").value(404));
        }
    }
}

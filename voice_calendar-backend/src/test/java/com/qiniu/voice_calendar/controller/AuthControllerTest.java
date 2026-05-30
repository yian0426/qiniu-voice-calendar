package com.qiniu.voice_calendar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.voice_calendar.common.Result;
import com.qiniu.voice_calendar.dto.LoginRequest;
import com.qiniu.voice_calendar.dto.LoginResponse;
import com.qiniu.voice_calendar.dto.ProfileResponse;
import com.qiniu.voice_calendar.dto.RegisterRequest;
import com.qiniu.voice_calendar.dto.UpdateProfileRequest;
import com.qiniu.voice_calendar.exception.BusinessException;
import com.qiniu.voice_calendar.exception.GlobalExceptionHandler;
import com.qiniu.voice_calendar.service.UserService;
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

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthController 接口测试")
class AuthControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private RegisterRequest validRegister;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        validRegister = new RegisterRequest();
        validRegister.setUsername("testuser");
        validRegister.setPassword("password123");
        validRegister.setEmail("test@example.com");

        // Clear security context between tests
        SecurityContextHolder.clearContext();
    }

    /** Simulate an authenticated user in the SecurityContext. */
    private void mockAuthenticatedUser(Long userId, String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(userId);
        when(auth.getDetails()).thenReturn(username);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    // ══════════════════════════════════════
    //   POST /api/auth/register
    // ══════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/register — 注册")
    class Register {

        @Test
        @DisplayName("正常注册返回 200 + token")
        void shouldRegisterSuccessfully() throws Exception {
            LoginResponse resp = LoginResponse.builder()
                    .userId(1L).username("testuser").token("jwt.token.here").build();
            when(userService.register(any(RegisterRequest.class))).thenReturn(resp);

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRegister)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("注册成功"))
                    .andExpect(jsonPath("$.data.userId").value(1))
                    .andExpect(jsonPath("$.data.username").value("testuser"))
                    .andExpect(jsonPath("$.data.token").value("jwt.token.here"));
        }

        @Test
        @DisplayName("重复用户名 → 409")
        void shouldRejectDuplicateUsername() throws Exception {
            when(userService.register(any(RegisterRequest.class)))
                    .thenThrow(new BusinessException(409, "用户名已被注册"));

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRegister)))
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.message").value("用户名已被注册"));
        }

        @Test
        @DisplayName("用户名空白 → 400")
        void shouldRejectBlankUsername() throws Exception {
            validRegister.setUsername("");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRegister)))
                    .andExpect(status().is(400))
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("密码过短 → 400")
        void shouldRejectShortPassword() throws Exception {
            validRegister.setPassword("12345");

            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRegister)))
                    .andExpect(status().is(400))
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ══════════════════════════════════════
    //   POST /api/auth/login
    // ══════════════════════════════════════

    @Nested
    @DisplayName("POST /api/auth/login — 登录")
    class Login {

        @Test
        @DisplayName("正确密码 → 200 + token")
        void shouldLoginSuccessfully() throws Exception {
            LoginResponse resp = LoginResponse.builder()
                    .userId(1L).username("testuser").token("jwt.token.here").build();
            when(userService.login(any(LoginRequest.class))).thenReturn(resp);

            LoginRequest req = new LoginRequest();
            req.setUsername("testuser");
            req.setPassword("password123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("登录成功"))
                    .andExpect(jsonPath("$.data.token").value("jwt.token.here"));
        }

        @Test
        @DisplayName("密码错误 → 401")
        void shouldRejectWrongPassword() throws Exception {
            when(userService.login(any(LoginRequest.class)))
                    .thenThrow(new BusinessException(401, "用户名或密码错误"));

            LoginRequest req = new LoginRequest();
            req.setUsername("testuser");
            req.setPassword("wrong");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.message").value("用户名或密码错误"));
        }

        @Test
        @DisplayName("用户不存在 → 401")
        void shouldRejectNonexistentUser() throws Exception {
            when(userService.login(any(LoginRequest.class)))
                    .thenThrow(new BusinessException(401, "用户名或密码错误"));

            LoginRequest req = new LoginRequest();
            req.setUsername("nobody");
            req.setPassword("password123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("用户名为空 → 400")
        void shouldRejectEmptyUsername() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setUsername("");
            req.setPassword("password123");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().is(400))
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ══════════════════════════════════════
    //   GET /api/auth/profile
    // ══════════════════════════════════════

    @Nested
    @DisplayName("GET /api/auth/profile — 获取个人信息")
    class GetProfile {

        @Test
        @DisplayName("已认证 → 200 + 用户信息")
        void shouldReturnProfileWhenAuthenticated() throws Exception {
            mockAuthenticatedUser(1L, "testuser");

            ProfileResponse profile = ProfileResponse.builder()
                    .id(1L).username("testuser").email("test@example.com")
                    .createdAt(LocalDateTime.of(2026, 5, 1, 10, 0)).build();
            when(userService.getProfile(1L)).thenReturn(profile);

            mockMvc.perform(get("/api/auth/profile"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.username").value("testuser"))
                    .andExpect(jsonPath("$.data.email").value("test@example.com"))
                    .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
        }

        @Test
        @DisplayName("未认证 → 500 (SecurityContextUtil 抛异常)")
        void shouldFailWhenNotAuthenticated() throws Exception {
            // SecurityContext is empty → SecurityContextUtil throws IllegalStateException
            // → GlobalExceptionHandler catches as 500
            mockMvc.perform(get("/api/auth/profile"))
                    .andExpect(status().is(500))
                    .andExpect(jsonPath("$.code").value(500));
        }
    }

    // ══════════════════════════════════════
    //   PUT /api/auth/profile
    // ══════════════════════════════════════

    @Nested
    @DisplayName("PUT /api/auth/profile — 修改个人信息")
    class UpdateProfile {

        @Test
        @DisplayName("正常修改 → 200")
        void shouldUpdateProfile() throws Exception {
            mockAuthenticatedUser(1L, "testuser");

            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setEmail("new@example.com");

            mockMvc.perform(put("/api/auth/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("修改成功"));
        }

        @Test
        @DisplayName("邮箱冲突 → 409")
        void shouldRejectDuplicateEmail() throws Exception {
            mockAuthenticatedUser(1L, "testuser");

            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setEmail("taken@example.com");

            doThrow(new BusinessException(409, "邮箱已被其他用户使用"))
                    .when(userService).updateProfile(eq(1L), any(UpdateProfileRequest.class));

            mockMvc.perform(put("/api/auth/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.message").value("邮箱已被其他用户使用"));
        }

        @Test
        @DisplayName("未认证 → 500")
        void shouldFailWhenNotAuthenticated() throws Exception {
            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setEmail("new@example.com");

            mockMvc.perform(put("/api/auth/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().is(500))
                    .andExpect(jsonPath("$.code").value(500));
        }
    }
}

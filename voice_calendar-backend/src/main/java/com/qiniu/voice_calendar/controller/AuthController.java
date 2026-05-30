package com.qiniu.voice_calendar.controller;

import com.qiniu.voice_calendar.common.Result;
import com.qiniu.voice_calendar.dto.LoginRequest;
import com.qiniu.voice_calendar.dto.LoginResponse;
import com.qiniu.voice_calendar.dto.ProfileResponse;
import com.qiniu.voice_calendar.dto.RegisterRequest;
import com.qiniu.voice_calendar.dto.UpdateProfileRequest;
import com.qiniu.voice_calendar.service.UserService;
import com.qiniu.voice_calendar.util.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        LoginResponse response = userService.register(request);
        return Result.ok("注册成功", response);
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.ok("登录成功", response);
    }

    @GetMapping("/profile")
    public Result<ProfileResponse> getProfile() {
        Long userId = SecurityContextUtil.getCurrentUserId();
        ProfileResponse profile = userService.getProfile(userId);
        return Result.ok(profile);
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UpdateProfileRequest request) {
        Long userId = SecurityContextUtil.getCurrentUserId();
        userService.updateProfile(userId, request);
        return Result.ok("修改成功");
    }
}

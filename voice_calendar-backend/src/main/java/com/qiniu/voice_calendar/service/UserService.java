package com.qiniu.voice_calendar.service;

import com.qiniu.voice_calendar.dto.LoginRequest;
import com.qiniu.voice_calendar.dto.LoginResponse;
import com.qiniu.voice_calendar.dto.ProfileResponse;
import com.qiniu.voice_calendar.dto.RegisterRequest;
import com.qiniu.voice_calendar.dto.UpdateProfileRequest;

public interface UserService {
    LoginResponse register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    ProfileResponse getProfile(Long userId);
    void updateProfile(Long userId, UpdateProfileRequest request);
}

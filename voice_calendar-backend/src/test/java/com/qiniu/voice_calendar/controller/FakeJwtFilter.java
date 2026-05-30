package com.qiniu.voice_calendar.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Fake JWT filter for testing.
 * Accepts any "Bearer valid-token" as authenticated user id=1, username=testuser.
 * All other tokens are rejected (passed through without setting auth context).
 */
public class FakeJwtFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.equals("Bearer valid-token")) {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(1L, null, Collections.emptyList());
            auth.setDetails("testuser");
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}

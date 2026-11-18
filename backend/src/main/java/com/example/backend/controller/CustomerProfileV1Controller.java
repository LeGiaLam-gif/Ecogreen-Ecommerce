package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.dto.CustomerProfileRequest;
import com.example.backend.dto.CustomerProfileResponse;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.service.CustomerProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
public class CustomerProfileV1Controller {

    @Autowired private CustomerProfileService profileService;

    @GetMapping
    public ApiResponse<CustomerProfileResponse> get(HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        return ApiResponse.of(CustomerProfileResponse.from(profileService.get(current.getUserId())));
    }

    @PutMapping
    public ApiResponse<CustomerProfileResponse> update(@Valid @RequestBody CustomerProfileRequest body,
                                                       HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        return ApiResponse.of(CustomerProfileResponse.from(profileService.update(current.getUserId(), body)));
    }

    private static CurrentUser requireCurrent(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) throw new UnauthorizedException("Vui lòng đăng nhập để tiếp tục.");
        return current;
    }
}

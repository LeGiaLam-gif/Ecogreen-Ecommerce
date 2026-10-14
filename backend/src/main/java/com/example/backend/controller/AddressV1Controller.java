package com.example.backend.controller;

import com.example.backend.api.ApiResponse;
import com.example.backend.dto.AddressRequest;
import com.example.backend.dto.AddressResponse;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.service.AddressService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The caller's own address book (B08). Any logged-in user may use it; there is no permission code because the rights are
 * ownership-based. The user id is always taken from the verified token, never from the path or body, and AddressService
 * checks the owner of every {@code /{id}}.
 *
 * <p>The list is not paged on purpose: a book holds at most {@link AddressService#MAX_ADDRESSES} addresses.
 */
@RestController
@RequestMapping("/api/v1/users/me/addresses")
public class AddressV1Controller {

    @Autowired private AddressService addressService;

    @GetMapping
    public ApiResponse<List<AddressResponse>> list(HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        return ApiResponse.of(addressService.list(current.getUserId()).stream().map(AddressResponse::from).toList());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> create(@Valid @RequestBody AddressRequest body,
                                                              HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        return ResponseEntity.status(201)
                .body(ApiResponse.of(AddressResponse.from(addressService.create(current.getUserId(), body))));
    }

    @PutMapping("/{id}")
    public ApiResponse<AddressResponse> update(@PathVariable Long id, @Valid @RequestBody AddressRequest body,
                                               HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        return ApiResponse.of(AddressResponse.from(addressService.update(current.getUserId(), id, body)));
    }

    @PatchMapping("/{id}/default")
    public ApiResponse<AddressResponse> setDefault(@PathVariable Long id, HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        return ApiResponse.of(AddressResponse.from(addressService.setDefault(current.getUserId(), id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        CurrentUser current = requireCurrent(request);
        addressService.delete(current.getUserId(), id);
        return ResponseEntity.noContent().build();
    }

    private static CurrentUser requireCurrent(HttpServletRequest request) {
        CurrentUser current = (CurrentUser) request.getAttribute(AuthInterceptor.REQUEST_ATTR);
        if (current == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để tiếp tục.");
        }
        return current;
    }
}

package com.example.backend.controller;

import com.example.backend.entity.Role;
import com.example.backend.entity.User;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.JwtService;
import com.example.backend.security.TestJwt;
import com.example.backend.service.AddressService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B08 + B01-P2 integration: the REAL JwtService and the REAL AuthInterceptor in front of a real controller. A valid access
 * token stops working on the very next call once the user is inactive (what "disable" does), without waiting for the token
 * to expire.
 */
class DisabledUserApiAccessTest {

    private static final long USER_ID = 7L;

    private final JwtService jwt = new JwtService(TestJwt.SECRET, 900);
    private final UserRepository users = mock(UserRepository.class);

    private MockMvc mvcWithRealInterceptor() {
        AddressV1Controller controller = new AddressV1Controller();
        AddressService addressService = mock(AddressService.class);
        when(addressService.list(USER_ID)).thenReturn(List.of());
        ReflectionTestUtils.setField(controller, "addressService", addressService);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(new AuthInterceptor(jwt, users))
                .build();
    }

    private String tokenOfCustomer() {
        User u = new User();
        u.setId(USER_ID);
        u.setRoles(new HashSet<>(Set.of(new Role(1L, Role.CUSTOMER))));
        return "Bearer " + jwt.issueAccessToken(u);
    }

    @Test
    void disabledUser_cannotCallApi() throws Exception {
        MockMvc mvc = mvcWithRealInterceptor();
        String bearer = tokenOfCustomer();

        // while active the very same token works ...
        when(users.existsByIdAndActiveTrue(USER_ID)).thenReturn(true);
        mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", bearer)).andExpect(status().isOk());

        // ... and after the account is disabled, the NEXT call is rejected although the JWT has not expired
        when(users.existsByIdAndActiveTrue(USER_ID)).thenReturn(false);
        mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));

        // re-enabling restores access with the same token (enable does not need a new login while the JWT is valid)
        when(users.existsByIdAndActiveTrue(USER_ID)).thenReturn(true);
        mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", bearer)).andExpect(status().isOk());
    }

    @Test
    void invalidOrMissingToken_is401() throws Exception {
        MockMvc mvc = mvcWithRealInterceptor();
        when(users.existsByIdAndActiveTrue(USER_ID)).thenReturn(true);

        mvc.perform(get("/api/v1/users/me/addresses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }
}

package com.example.backend.exception;

import com.example.backend.api.ApiPaging;
import com.example.backend.api.ApiResponse;
import com.example.backend.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B01-F1: GlobalExceptionHandler contract, tested with standalone MockMvc (no Spring context, no database).
 * The test controller below doubles as the documented example of a /api/v1 endpoint.
 */
class GlobalExceptionHandlerTest {

    private static final String SECRET_TEXT = "secret-internal-detail jdbc:postgresql://db:5432/ecogreen";

    static class CreateItemRequest {
        @NotBlank(message = "must not be blank")
        public String name;

        @Size(min = 8, message = "must be at least 8 characters")
        public String password;
    }

    @RestController
    static class ExampleController {

        @PostMapping("/api/v1/test/items")
        public ApiResponse<String> create(@Valid @RequestBody CreateItemRequest request) {
            return ApiResponse.of("created");
        }

        @GetMapping("/api/v1/test/boom")
        public String boomV1() {
            throw new IllegalStateException(SECRET_TEXT);
        }

        @GetMapping("/api/test/boom")
        public String boomLegacy() {
            throw new IllegalStateException(SECRET_TEXT);
        }

        @GetMapping("/api/test/not-found")
        public String notFoundLegacy() {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm.");
        }

        @GetMapping("/api/v1/test/not-found")
        public String notFoundV1() {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm.");
        }

        @GetMapping("/api/test/bad-request")
        public String badRequestLegacy() {
            throw new BadRequestException("Số lượng không hợp lệ.");
        }

        @GetMapping("/api/v1/test/bad-request")
        public String badRequestV1() {
            throw new BadRequestException("Số lượng không hợp lệ.");
        }

        @GetMapping("/api/v1/test/rule")
        public String ruleV1() {
            throw new BusinessRuleViolationException("Không thể hủy đơn đã giao.");
        }

        /** How a module returns a paged list: clamp + whitelist the params, query with Pageable, wrap with PageResponse. */
        @GetMapping("/api/v1/test/items")
        public ApiResponse<List<String>> list(@RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size,
                                              @RequestParam(required = false) String sort) {
            Pageable pageable = ApiPaging.of(page, size, sort,
                    Map.of("createdAt", "createdAt", "price", "price"), Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<String> result = new PageImpl<>(List.of("a", "b"), pageable, 250);
            return PageResponse.of(result);
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ExampleController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // 1. validation failure on /api/v1/** -> 400 VALIDATION_ERROR + fields (and no echo of secrets)
    @Test
    void v1_validationFailure_returns400ValidationErrorWithFields_andDoesNotEchoPassword() throws Exception {
        String body = "{\"name\":\"\",\"password\":\"hunter2\"}";

        MvcResult result = mvc.perform(post("/api/v1/test/items").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.name").exists())
                .andExpect(jsonPath("$.error.fields.password").exists())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains("hunter2"),
                "validation response must not echo the submitted password");
    }

    // 2. unknown runtime exception on /api/v1/** -> 500 INTERNAL_ERROR, original text absent
    @Test
    void v1_unknownRuntimeException_returns500InternalErrorWithoutOriginalText() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(GlobalExceptionHandler.GENERIC_ERROR))
                .andExpect(jsonPath("$.error.traceId").exists())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertFalse(content.contains("secret-internal-detail"));
        assertFalse(content.contains("jdbc:"));
        assertFalse(content.contains("IllegalStateException"));
        assertFalse(content.contains("java.lang"));
    }

    // 3. unknown runtime exception on legacy /api/** -> generic {message}, original text absent
    @Test
    void legacy_unknownRuntimeException_returnsGenericMessageWithoutOriginalText() throws Exception {
        MvcResult result = mvc.perform(get("/api/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(GlobalExceptionHandler.GENERIC_ERROR))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertFalse(content.contains("secret-internal-detail"));
        assertFalse(content.contains("jdbc:"));
        assertFalse(content.contains("IllegalStateException"));
    }

    // 4. legacy ResourceNotFoundException -> 404 {message}
    @Test
    void legacy_resourceNotFound_returns404WithMessageShape() throws Exception {
        mvc.perform(get("/api/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy sản phẩm."))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void v1_resourceNotFound_returns404NotFoundCode() throws Exception {
        mvc.perform(get("/api/v1/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Không tìm thấy sản phẩm."))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void legacy_badRequest_keeps400MessageShape() throws Exception {
        mvc.perform(get("/api/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Số lượng không hợp lệ."));
    }

    @Test
    void v1_badRequest_isValidationError() throws Exception {
        mvc.perform(get("/api/v1/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void v1_businessRuleViolation_returns400BusinessRuleViolation() throws Exception {
        mvc.perform(get("/api/v1/test/rule"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void v1_malformedJson_returns400ValidationErrorWithoutParserText() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/test/items").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertFalse(content.contains("Jackson"));
        assertFalse(content.contains("com.example"));
        assertFalse(content.contains("JSON parse error"));
    }

    // 5. PageResponse -> correct pagination metadata
    @Test
    void pageResponse_hasCorrectMeta() throws Exception {
        mvc.perform(get("/api/v1/test/items?page=2&size=20&sort=price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(250))
                .andExpect(jsonPath("$.meta.totalPages").value(13));
    }

    // 6. size=1000 -> clamped to 100 on the server
    @Test
    void pageSize1000_isClampedTo100() throws Exception {
        mvc.perform(get("/api/v1/test/items?size=1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.size").value(100))
                .andExpect(jsonPath("$.meta.totalPages").value(3));
    }

    @Test
    void defaultsApply_whenNoPagingParams() throws Exception {
        mvc.perform(get("/api/v1/test/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20));
    }

    @Test
    void unknownSortField_isRejectedAsValidationError_notPassedOn() throws Exception {
        mvc.perform(get("/api/v1/test/items").param("sort", "password;drop table users,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/test/items").param("sort", "price,sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void isV1_detectsOnlyApiV1Paths() {
        var req = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/products");
        assertTrue(GlobalExceptionHandler.isV1(req));
        assertFalse(GlobalExceptionHandler.isV1(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/products")));
        assertFalse(GlobalExceptionHandler.isV1(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v10/products")));
        assertFalse(GlobalExceptionHandler.isV1(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1x")));
    }
}

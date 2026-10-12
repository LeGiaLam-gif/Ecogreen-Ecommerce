package com.example.backend.service.inventory;

import com.example.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B03: InsufficientStockException is a BusinessRuleViolationException, so the unchanged GlobalExceptionHandler (B01-F1)
 * answers 400 BUSINESS_RULE_VIOLATION on /api/v1 and a plain 400 {message} on legacy paths (B05 had 409 CONFLICT). The
 * message names the product but never an internal id. Standalone MockMvc, no Spring context and no database.
 */
class InsufficientStockMappingTest {

    @RestController
    static class StockController {

        @GetMapping("/api/v1/test/stock")
        public String v1() {
            throw new InsufficientStockException(5L, 2, "Túi vải tái chế");
        }

        @GetMapping("/api/test/stock")
        public String legacy() {
            throw new InsufficientStockException(5L, 2, "Túi vải tái chế");
        }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new StockController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void v1_insufficientStock_returns400BusinessRuleViolation_namingTheProduct() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/test/stock"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("Túi vải tái chế"), "the message must name the product");
        assertFalse(body.contains("#5"), "the customer-facing message must not name the product id");
        assertFalse(body.contains("productId"), "no internal field may appear in the response");
    }

    @Test
    void legacy_insufficientStock_returns400_withTheLegacyMessageShape() throws Exception {
        mvc.perform(get("/api/test/stock"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.error").doesNotExist());
    }
}

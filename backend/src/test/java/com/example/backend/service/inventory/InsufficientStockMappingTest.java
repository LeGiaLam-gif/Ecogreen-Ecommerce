package com.example.backend.service.inventory;

import com.example.backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B05: InsufficientStockException is a ConflictException, so the unchanged GlobalExceptionHandler (B01-F1) answers 409 on
 * both API generations. Standalone MockMvc, no Spring context and no database.
 */
class InsufficientStockMappingTest {

    @RestController
    static class StockController {

        @GetMapping("/api/v1/test/stock")
        public String v1() {
            throw new InsufficientStockException(5L, 2);
        }

        @GetMapping("/api/test/stock")
        public String legacy() {
            throw new InsufficientStockException(5L, 2);
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
    void v1_insufficientStock_returns409Conflict_withTheStandardErrorShape() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/test/stock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains("#5"),
                "the customer-facing message must not name the product id");
    }

    @Test
    void legacy_insufficientStock_returns409_withTheLegacyMessageShape() throws Exception {
        mvc.perform(get("/api/test/stock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.error").doesNotExist());
    }
}

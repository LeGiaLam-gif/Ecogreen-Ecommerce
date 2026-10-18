package com.example.backend.controller;

import com.example.backend.dto.CartResponse;
import com.example.backend.entity.CartItem;
import com.example.backend.entity.Product;
import com.example.backend.entity.User;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.GlobalExceptionHandler;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.UserRepository;
import com.example.backend.security.AuthGuard;
import com.example.backend.security.AuthInterceptor;
import com.example.backend.security.CurrentUser;
import com.example.backend.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B04: the cart on /api/v1/cart through standalone MockMvc (real controller, real AuthGuard, real GlobalExceptionHandler,
 * mocked CartService): the envelope, the available quantity of every line, validation, and who the cart belongs to.
 */
class CartV1ControllerTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final long CALLER = 42L;

    private CartService cartService;
    private User caller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        cartService = mock(CartService.class);
        UserRepository users = mock(UserRepository.class);
        caller = new User();
        caller.setId(CALLER);
        when(users.findById(CALLER)).thenReturn(Optional.of(caller));
        AuthGuard authGuard = new AuthGuard();
        ReflectionTestUtils.setField(authGuard, "userRepository", users);

        CartV1Controller controller = new CartV1Controller();
        ReflectionTestUtils.setField(controller, "cartService", cartService);
        ReflectionTestUtils.setField(controller, "authGuard", authGuard);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static MockHttpServletRequestBuilder asCustomer(MockHttpServletRequestBuilder builder) {
        return builder.requestAttr(AuthInterceptor.REQUEST_ATTR, new CurrentUser(CALLER, List.of("CUSTOMER"), List.of()));
    }

    private static Product product(long id, String name, String price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setSlug("san-pham-" + id);
        product.setPrice(new BigDecimal(price));
        product.setStatus(Product.Status.ACTIVE);
        return product;
    }

    private static CartItem line(long id, Product product, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    /** Line 10: product 7 x3 with only 2 available (warns); line 11: product 8 x1 with 5 available (fine). */
    private static CartResponse cart() {
        return CartResponse.from(50L,
                List.of(line(10L, product(7L, "Bình nước", "100000"), 3), line(11L, product(8L, "Hộp cơm", "50000"), 1)),
                Map.of(7L, 2, 8L, 5));
    }

    // ---- who may call ----

    @Test
    void everyEndpoint_withoutToken_is401() throws Exception {
        mvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/cart/items").contentType(JSON).content("{\"productId\":7}")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/cart/items/10").contentType(JSON).content("{\"quantity\":2}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/cart/items/10")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/cart")).andExpect(status().isUnauthorized());

        verifyNoInteractions(cartService);
    }

    // ---- the response ----

    @Test
    void cart_response_includesAvailableQuantity() throws Exception {
        when(cartService.getCart(CALLER)).thenReturn(cart());

        mvc.perform(asCustomer(get("/api/v1/cart")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.data.cartId").value(50))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                // line 10: asked for 3, only 2 can be sold
                .andExpect(jsonPath("$.data.items[0].id").value(10))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3))
                .andExpect(jsonPath("$.data.items[0].availableQuantity").value(2))
                .andExpect(jsonPath("$.data.items[0].product.name").value("Bình nước"))
                .andExpect(jsonPath("$.data.items[0].product.price").value(100000))
                .andExpect(jsonPath("$.data.items[0].lineTotal").value(300000))
                .andExpect(jsonPath("$.data.items[0].warning").value("Kho chỉ còn 2 sản phẩm — vui lòng giảm số lượng trước khi đặt hàng."))
                // line 11: plenty of stock, no warning
                .andExpect(jsonPath("$.data.items[1].availableQuantity").value(5))
                .andExpect(jsonPath("$.data.items[1].lineTotal").value(50000))
                .andExpect(jsonPath("$.data.items[1].warning").doesNotExist())
                // 300 000 + 50 000 and 3 + 1 units: the items total only (discount and shipping come from the checkout preview)
                .andExpect(jsonPath("$.data.subtotal").value(350000))
                .andExpect(jsonPath("$.data.totalItems").value(4));
    }

    @Test
    void cart_response_marksSoldOutAndWithdrawnLines() {
        Product retired = product(9L, "Cũ", "10000");
        retired.setStatus(Product.Status.INACTIVE);

        CartResponse response = CartResponse.from(50L,
                List.of(line(1L, product(7L, "Hết", "10000"), 1), line(2L, retired, 1)), Map.of(7L, 0, 9L, 4));

        assertEquals(0, response.items.get(0).availableQuantity);
        assertEquals("Sản phẩm này hiện đã hết hàng.", response.items.get(0).warning);
        assertEquals("Sản phẩm này hiện không còn kinh doanh.", response.items.get(1).warning);
    }

    @Test
    void cart_ofAUserWithoutOne_isEmptyNotAnError() throws Exception {
        when(cartService.getCart(CALLER)).thenReturn(CartResponse.empty());

        mvc.perform(asCustomer(get("/api/v1/cart")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.subtotal").value(0))
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }

    // ---- changes: every answer is the whole updated cart ----

    @Test
    void addItem_defaultsTheQuantityToOne_andAnswersWithTheCart() throws Exception {
        when(cartService.addItem(caller, 7L, 1)).thenReturn(cart());

        mvc.perform(asCustomer(post("/api/v1/cart/items").contentType(JSON).content("{\"productId\":7}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2));

        verify(cartService).addItem(caller, 7L, 1);
    }

    @Test
    void addItem_validation_is400WithFields() throws Exception {
        mvc.perform(asCustomer(post("/api/v1/cart/items").contentType(JSON).content("{\"quantity\":2}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fields.productId").exists());
        mvc.perform(asCustomer(post("/api/v1/cart/items").contentType(JSON).content("{\"productId\":7,\"quantity\":0}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.quantity").exists());
        mvc.perform(asCustomer(post("/api/v1/cart/items").contentType(JSON).content("{\"productId\":7,\"quantity\":1001}")))
                .andExpect(status().isBadRequest());

        verify(cartService, never()).addItem(any(), anyLong(), anyInt());
    }

    @Test
    void addItem_theCartIsTheCallersOwn_whateverUserIdTheClientSends() throws Exception {
        when(cartService.addItem(any(), anyLong(), anyInt())).thenReturn(cart());

        mvc.perform(asCustomer(post("/api/v1/cart/items").contentType(JSON)
                        .content("{\"productId\":7,\"quantity\":2,\"userId\":999,\"cartId\":1}")))
                .andExpect(status().isOk());

        verify(cartService).addItem(caller, 7L, 2); // the user of the token
    }

    @Test
    void addItem_notEnoughStock_isTheServicesFriendlyMessage() throws Exception {
        when(cartService.addItem(any(), anyLong(), anyInt())).thenThrow(new BadRequestException("Kho chỉ còn 2 sản phẩm."));

        mvc.perform(asCustomer(post("/api/v1/cart/items").contentType(JSON).content("{\"productId\":7,\"quantity\":9}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Kho chỉ còn 2 sản phẩm."));
    }

    @Test
    void updateItem_isPatch_withTheNewQuantity() throws Exception {
        when(cartService.updateQuantity(caller, 10L, 3)).thenReturn(cart());

        mvc.perform(asCustomer(patch("/api/v1/cart/items/10").contentType(JSON).content("{\"quantity\":3}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value(3));

        mvc.perform(asCustomer(patch("/api/v1/cart/items/10").contentType(JSON).content("{}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.quantity").exists());
    }

    @Test
    void cart_itemOwnership_isEnforced_aForeignLineIsA404() throws Exception {
        when(cartService.updateQuantity(eq(caller), eq(77L), anyInt()))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy sản phẩm trong giỏ hàng #77"));
        when(cartService.removeItem(caller, 77L)).thenThrow(new ResourceNotFoundException("Không tìm thấy sản phẩm trong giỏ hàng #77"));

        mvc.perform(asCustomer(patch("/api/v1/cart/items/77").contentType(JSON).content("{\"quantity\":1}")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(asCustomer(delete("/api/v1/cart/items/77")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void removeItem_answersWithTheCart() throws Exception {
        when(cartService.removeItem(caller, 10L)).thenReturn(CartResponse.empty());

        mvc.perform(asCustomer(delete("/api/v1/cart/items/10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void clearCart_is204() throws Exception {
        mvc.perform(asCustomer(delete("/api/v1/cart"))).andExpect(status().isNoContent());

        verify(cartService).clearCart(caller);
    }
}

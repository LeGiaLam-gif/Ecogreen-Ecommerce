package com.example.backend.dto;

import com.example.backend.entity.CartItem;
import com.example.backend.entity.Product;
import java.math.BigDecimal;

/**
 * One cart line (B04). Built only from the line and its product, which the cart query loads together
 * ({@code CartItemRepository.findByCartIdWithProduct}): nothing here touches a lazy association (the category is
 * deliberately not part of a cart line). Price and name are read from the product row, never stored on the line.
 */
public class CartItemResponse {
    public Long id;
    public CartProductResponse product;
    public int quantity;
    /** {@code product.price x quantity}, computed on the server. */
    public BigDecimal lineTotal;
    /** What can be sold right now (on hand minus reserved). When it is below {@code quantity} the page warns the customer. */
    public int availableQuantity;
    /**
     * A ready-to-show message (Vietnamese) when checkout would refuse this line - product no longer sold, sold out, or fewer
     * units available than the cart holds - and null otherwise. The page displays it as is.
     */
    public String warning;

    /** The product fields a cart page needs. */
    public static class CartProductResponse {
        public Long id;
        public String name;
        public String slug;
        public String image;
        public BigDecimal price;
        public String status;
        /**
         * Same value as {@link CartItemResponse#availableQuantity}. Kept because the B03 contract defines the cart product's
         * {@code stockQuantity} as the available quantity; remove with the B11 storefront slice.
         */
        public int stockQuantity;
    }

    /** {@code available} is the sellable quantity of the item's product (see InventoryService.availableFor). */
    public static CartItemResponse from(CartItem item, int available) {
        CartProductResponse p = new CartProductResponse();
        p.id = item.getProduct().getId();
        p.name = item.getProduct().getName();
        p.slug = item.getProduct().getSlug();
        p.image = item.getProduct().getImage();
        p.price = item.getProduct().getPrice();
        p.status = item.getProduct().getStatus().name();
        p.stockQuantity = available;

        CartItemResponse r = new CartItemResponse();
        r.id = item.getId();
        r.product = p;
        r.quantity = item.getQuantity();
        r.lineTotal = item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        r.availableQuantity = available;
        if (item.getProduct().getStatus() != Product.Status.ACTIVE) {
            r.warning = "Sản phẩm này hiện không còn kinh doanh.";
        } else if (available <= 0) {
            r.warning = "Sản phẩm này hiện đã hết hàng.";
        } else if (item.getQuantity() > available) {
            r.warning = "Kho chỉ còn " + available + " sản phẩm — vui lòng giảm số lượng trước khi đặt hàng.";
        }
        return r;
    }
}

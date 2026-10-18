package com.example.backend.service;

import com.example.backend.dto.DiscountRequest;
import com.example.backend.entity.Discount;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.DiscountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** B04 discount rules with exact amounts: rounding, the cap at the subtotal, every rejection, and the admin CRUD rules. */
class DiscountServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);

    private DiscountRepository repository;
    private DiscountService service;

    @BeforeEach
    void setUp() {
        repository = mock(DiscountRepository.class);
        service = new DiscountService();
        ReflectionTestUtils.setField(service, "discountRepository", repository);
        when(repository.saveAndFlush(any(Discount.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Discount percent(int percentOff) {
        Discount d = new Discount();
        d.setId(1L);
        d.setCode("SALE");
        d.setPercentOff(percentOff);
        d.setActive(true);
        return d;
    }

    private static Discount amount(String amountOff) {
        Discount d = new Discount();
        d.setId(2L);
        d.setCode("MINUS");
        d.setAmountOff(new BigDecimal(amountOff));
        d.setActive(true);
        return d;
    }

    private void stored(Discount discount) {
        when(repository.findByUpperCode(discount.getCode())).thenReturn(Optional.of(discount));
    }

    // ---- amounts ----

    @Test
    void discount_percent_rounding() {
        // 10% of 15 555 = 1 555.5 -> 1 556 (HALF_UP)
        assertEquals(new BigDecimal("1556.00"), DiscountService.amountFor(percent(10), new BigDecimal("15555")));
        // 5% of 10 010 = 500.5 -> 501
        assertEquals(new BigDecimal("501.00"), DiscountService.amountFor(percent(5), new BigDecimal("10010")));
        // 12% of 12 345 = 1 481.4 -> 1 481
        assertEquals(new BigDecimal("1481.00"), DiscountService.amountFor(percent(12), new BigDecimal("12345")));
        // 33% of 1 005 = 331.65 -> 332
        assertEquals(new BigDecimal("332.00"), DiscountService.amountFor(percent(33), new BigDecimal("1005")));
        // 15% of 333 = 49.95 -> 50
        assertEquals(new BigDecimal("50.00"), DiscountService.amountFor(percent(15), new BigDecimal("333")));
        // exact results stay exact: 10% of 200 000
        assertEquals(new BigDecimal("20000.00"), DiscountService.amountFor(percent(10), new BigDecimal("200000")));
        // the subtotal may carry decimals: 10% of 99.99 = 9.999 -> 10
        assertEquals(new BigDecimal("10.00"), DiscountService.amountFor(percent(10), new BigDecimal("99.99")));
    }

    @Test
    void discount_amount_neverExceedsSubtotal() {
        // fixed amount larger than the items: capped at the subtotal
        assertEquals(new BigDecimal("80000.00"), DiscountService.amountFor(amount("100000"), new BigDecimal("80000")));
        // fixed amount equal to the items
        assertEquals(new BigDecimal("80000.00"), DiscountService.amountFor(amount("80000"), new BigDecimal("80000")));
        // fixed amount smaller: taken as is
        assertEquals(new BigDecimal("50000.00"), DiscountService.amountFor(amount("50000"), new BigDecimal("80000")));
        // 100% takes everything and no more
        assertEquals(new BigDecimal("80000.00"), DiscountService.amountFor(percent(100), new BigDecimal("80000")));
        // an empty / zero subtotal gets no discount
        assertEquals(new BigDecimal("0.00"), DiscountService.amountFor(amount("50000"), BigDecimal.ZERO));

        // through apply(): total = subtotal - discount never goes below zero
        Discount big = amount("100000");
        big.setCode("BIG");
        stored(big);
        AppliedDiscount applied = service.apply("big", new BigDecimal("80000"), NOW);
        assertEquals(new BigDecimal("80000.00"), applied.amount());
        assertEquals(0, new BigDecimal("80000").subtract(applied.amount()).signum());
    }

    @Test
    void apply_returnsTheCanonicalCode_andMatchesIgnoringCase() {
        Discount d = percent(10);
        d.setCode("SALE10");
        stored(d);

        AppliedDiscount applied = service.apply("  sale10 ", new BigDecimal("200000"), NOW);

        verify(repository).findByUpperCode("SALE10");
        assertEquals("SALE10", applied.code());
        assertEquals(new BigDecimal("20000.00"), applied.amount());
    }

    @Test
    void apply_blankCode_meansNoDiscount_andDoesNotTouchTheDatabase() {
        for (String blank : new String[] {null, "", "   "}) {
            AppliedDiscount applied = service.apply(blank, new BigDecimal("200000"), NOW);

            assertNull(applied.code());
            assertEquals(new BigDecimal("0.00"), applied.amount());
        }
        verifyNoInteractions(repository);
    }

    // ---- rejections ----

    @Test
    void discount_expiredInactiveOrBelowMinimum_isRejected() {
        BigDecimal subtotal = new BigDecimal("300000");

        Discount expired = percent(10);
        expired.setCode("EXPIRED");
        expired.setValidTo(NOW.minusSeconds(1));
        stored(expired);

        Discount notStarted = percent(10);
        notStarted.setCode("SOON");
        notStarted.setValidFrom(NOW.plusSeconds(1));
        stored(notStarted);

        Discount inactive = percent(10);
        inactive.setCode("OFF");
        inactive.setActive(false);
        stored(inactive);

        Discount minimum = percent(10);
        minimum.setCode("BIGSPENDER");
        minimum.setMinOrderAmount(new BigDecimal("300000.01"));
        stored(minimum);

        for (String code : new String[] {"EXPIRED", "SOON", "OFF", "BIGSPENDER", "NOSUCHCODE"}) {
            BusinessRuleViolationException e = assertThrows(BusinessRuleViolationException.class,
                    () -> service.apply(code, subtotal, NOW), code);
            // the SAME message for a code that does not exist and one that merely expired: no enumeration hint
            assertEquals(DiscountService.INVALID_CODE, e.getMessage(), code);
        }
    }

    @Test
    void apply_boundaries_areInclusive() {
        Discount window = percent(10);
        window.setCode("WINDOW");
        window.setValidFrom(NOW);
        window.setValidTo(NOW);
        window.setMinOrderAmount(new BigDecimal("300000"));
        stored(window);

        // now == valid_from == valid_to, and subtotal == min_order_amount: all accepted
        AppliedDiscount applied = service.apply("WINDOW", new BigDecimal("300000"), NOW);

        assertEquals(new BigDecimal("30000.00"), applied.amount());
    }

    @Test
    void apply_openEndedWindowAndNoMinimum_areAccepted() {
        Discount open = amount("10000");
        open.setCode("OPEN");
        stored(open);

        assertEquals(new BigDecimal("10000.00"), service.apply("OPEN", new BigDecimal("1000000"), NOW.plusYears(5)).amount());
    }

    // ---- admin CRUD ----

    private static DiscountRequest request(String code, Integer percentOff, String amountOff, LocalDateTime from, LocalDateTime to) {
        return new DiscountRequest(code, percentOff, amountOff == null ? null : new BigDecimal(amountOff), null, from, to, null);
    }

    @Test
    void create_storesTheCodeTrimmedInUpperCase_andDefaultsToActive() {
        when(repository.findByUpperCode("SUMMER-10")).thenReturn(Optional.empty());

        Discount created = service.create(request("  summer-10 ", 10, null, null, null));

        ArgumentCaptor<Discount> saved = ArgumentCaptor.forClass(Discount.class);
        verify(repository).saveAndFlush(saved.capture());
        assertEquals("SUMMER-10", saved.getValue().getCode());
        assertEquals("SUMMER-10", created.getCode());
        assertTrue(created.isActive());
        assertEquals(Integer.valueOf(10), created.getPercentOff());
        assertNull(created.getAmountOff());
    }

    @Test
    void create_duplicateCodeIgnoringCase_isAConflict() {
        when(repository.findByUpperCode("SALE")).thenReturn(Optional.of(percent(10)));

        assertThrows(ConflictException.class, () -> service.create(request("sale", 5, null, null, null)));

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_needsExactlyOneOfPercentOrAmount() {
        assertThrows(BadRequestException.class, () -> service.create(request("BOTH", 10, "5000", null, null)));
        assertThrows(BadRequestException.class, () -> service.create(request("NONE", null, null, null, null)));

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_windowEndingBeforeItStarts_isRejected() {
        assertThrows(BadRequestException.class,
                () -> service.create(request("BACKWARDS", 10, null, NOW, NOW.minusDays(1))));
    }

    @Test
    void update_toTheCodeOfAnotherDiscount_isAConflict_butKeepingItsOwnCodeIsFine() {
        Discount existing = percent(10);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsOtherWithUpperCode("TAKEN", 1L)).thenReturn(true);
        when(repository.existsOtherWithUpperCode("SALE", 1L)).thenReturn(false);

        assertThrows(ConflictException.class, () -> service.update(1L, request("taken", 10, null, null, null)));

        Discount updated = service.update(1L, request("sale", null, "20000", null, null));
        assertEquals("SALE", updated.getCode());
        assertNull(updated.getPercentOff());
        assertEquals(new BigDecimal("20000.00"), updated.getAmountOff());
    }

    @Test
    void getUpdateDelete_ofAMissingDiscount_areNotFound() {
        when(repository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(9L));
        assertThrows(ResourceNotFoundException.class, () -> service.update(9L, request("X", 10, null, null, null)));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L));
        verify(repository, never()).delete(any(Discount.class));
    }

    @Test
    void unknownCodeLookup_neverRevealsMoreThanTheGenericMessage() {
        when(repository.findByUpperCode(anyString())).thenReturn(Optional.empty());

        BusinessRuleViolationException e = assertThrows(BusinessRuleViolationException.class,
                () -> service.apply("whatever", new BigDecimal("100"), NOW));

        assertEquals("Mã giảm giá không hợp lệ hoặc đã hết hạn.", e.getMessage());
    }
}

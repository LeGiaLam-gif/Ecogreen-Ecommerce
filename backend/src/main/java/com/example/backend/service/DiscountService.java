package com.example.backend.service;

import com.example.backend.dto.DiscountRequest;
import com.example.backend.entity.Discount;
import com.example.backend.exception.BadRequestException;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.DiscountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Discount codes (B04): the admin CRUD, and {@link #apply} which checkout and its preview use to price a code.
 *
 * <p><b>Rules of {@link #apply}.</b> A code is accepted only when it exists, is active, {@code now} lies inside
 * {@code valid_from..valid_to} (both ends inclusive, either may be open) and the items total reaches
 * {@code min_order_amount}. Every failure gives the SAME message ({@link #INVALID_CODE}), so a caller cannot tell a code that
 * does not exist from one that expired. The amount never exceeds the items total; a percentage is rounded to whole VND with
 * HALF_UP.
 */
@Service
public class DiscountService {

    /** The one message for every rejected code: it must not reveal whether the code exists. */
    static final String INVALID_CODE = "Mã giảm giá không hợp lệ hoặc đã hết hạn.";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Autowired private DiscountRepository discountRepository;

    // ------------------------------------------------------------------------------------------------------------
    // Pricing (used by checkout and its preview)
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Prices {@code rawCode} against {@code subtotal}. A blank code means "no code" and gives {@link AppliedDiscount#none()}.
     *
     * @throws BusinessRuleViolationException {@link #INVALID_CODE} for a code that is unknown, inactive, outside its window or
     *                                        below its minimum order amount
     */
    @Transactional(readOnly = true)
    public AppliedDiscount apply(String rawCode, BigDecimal subtotal, LocalDateTime now) {
        if (rawCode == null || rawCode.isBlank()) {
            return AppliedDiscount.none();
        }
        Discount discount = discountRepository.findByUpperCode(normalise(rawCode))
                .orElseThrow(() -> new BusinessRuleViolationException(INVALID_CODE));
        if (!isUsable(discount, subtotal, now)) {
            throw new BusinessRuleViolationException(INVALID_CODE);
        }
        return new AppliedDiscount(discount.getCode(), amountFor(discount, subtotal));
    }

    /** True when the discount is active, inside its window at {@code now}, and {@code subtotal} reaches the minimum. */
    static boolean isUsable(Discount discount, BigDecimal subtotal, LocalDateTime now) {
        if (!discount.isActive()) {
            return false;
        }
        if (discount.getValidFrom() != null && now.isBefore(discount.getValidFrom())) {
            return false;
        }
        if (discount.getValidTo() != null && now.isAfter(discount.getValidTo())) {
            return false;
        }
        return discount.getMinOrderAmount() == null || subtotal.compareTo(discount.getMinOrderAmount()) >= 0;
    }

    /**
     * The amount taken off {@code subtotal}, scale 2, never negative and never above {@code subtotal}. Percentages are
     * rounded to whole VND (HALF_UP) before they are capped.
     */
    static BigDecimal amountFor(Discount discount, BigDecimal subtotal) {
        if (subtotal.signum() <= 0) {
            return zero();
        }
        BigDecimal raw;
        if (discount.getPercentOff() != null) {
            raw = subtotal.multiply(BigDecimal.valueOf(discount.getPercentOff()))
                    .divide(HUNDRED, 0, RoundingMode.HALF_UP);
        } else if (discount.getAmountOff() != null) {
            raw = discount.getAmountOff();
        } else {
            return zero();
        }
        return raw.min(subtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Admin CRUD
    // ------------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<Discount> list(Pageable pageable) {
        return discountRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Discount get(Long id) {
        return discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã giảm giá #" + id));
    }

    @Transactional
    public Discount create(DiscountRequest request) {
        requireValidRule(request);
        String code = normalise(request.code());
        if (discountRepository.findByUpperCode(code).isPresent()) {
            throw new ConflictException(duplicateMessage(code));
        }
        Discount discount = new Discount();
        copy(discount, request, code);
        return save(discount, code);
    }

    /** Full replacement of the discount's fields (the request is the new state). */
    @Transactional
    public Discount update(Long id, DiscountRequest request) {
        requireValidRule(request);
        Discount discount = get(id);
        String code = normalise(request.code());
        if (discountRepository.existsOtherWithUpperCode(code, id)) {
            throw new ConflictException(duplicateMessage(code));
        }
        copy(discount, request, code);
        return save(discount, code);
    }

    /** Hard delete: orders keep the code as text (no foreign key), so no order is affected. */
    @Transactional
    public void delete(Long id) {
        discountRepository.delete(get(id));
    }

    // ------------------------------------------------------------------------------------------------------------

    private Discount save(Discount discount, String code) {
        try {
            return discountRepository.saveAndFlush(discount);
        } catch (DataIntegrityViolationException e) {
            // Two admins creating the same code at once: the unique index on upper(code) is the backstop.
            throw new ConflictException(duplicateMessage(code));
        }
    }

    private static void requireValidRule(DiscountRequest request) {
        boolean percent = request.percentOff() != null;
        boolean amount = request.amountOff() != null;
        if (percent == amount) {
            throw new BadRequestException("Chỉ nhập một trong hai: phần trăm giảm hoặc số tiền giảm.");
        }
        if (request.validFrom() != null && request.validTo() != null && request.validFrom().isAfter(request.validTo())) {
            throw new BadRequestException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
        }
    }

    private static void copy(Discount discount, DiscountRequest request, String code) {
        discount.setCode(code);
        discount.setPercentOff(request.percentOff());
        discount.setAmountOff(request.amountOff() == null ? null : request.amountOff().setScale(2, RoundingMode.HALF_UP));
        discount.setMinOrderAmount(request.minOrderAmount() == null
                ? null : request.minOrderAmount().setScale(2, RoundingMode.HALF_UP));
        discount.setValidFrom(request.validFrom());
        discount.setValidTo(request.validTo());
        discount.setActive(request.active() == null || request.active());
    }

    /** Codes are stored trimmed and in upper case, and matched the same way. */
    static String normalise(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String duplicateMessage(String code) {
        return "Mã giảm giá \"" + code + "\" đã tồn tại.";
    }

    private static BigDecimal zero() {
        return new BigDecimal("0.00");
    }
}

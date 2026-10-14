package com.example.backend.service;

import com.example.backend.dto.AddressRequest;
import com.example.backend.entity.Address;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** B08 address book rules on a fake repository that enforces the one-default index (no Spring, no database). */
class AddressServiceTest {

    private static final long ALICE = 1L;
    private static final long BOB = 2L;

    private final InMemoryAddresses db = new InMemoryAddresses();
    private final AddressService service = db.service;

    private static AddressRequest request(String line1, Boolean isDefault) {
        return new AddressRequest("Nguyễn Văn A", "0901234567", line1, "Phường 1", "Quận 1", "TP. Hồ Chí Minh", isDefault);
    }

    private Address add(long userId, String line1, Boolean isDefault) {
        return service.create(userId, request(line1, isDefault));
    }

    // ── ownership ───────────────────────────────────────────────────────────────

    @Test
    void address_update_byNonOwner_isForbidden() {
        Address alices = add(ALICE, "1 Lê Lợi", null);

        assertThrows(ForbiddenException.class, () -> service.update(BOB, alices.getId(), request("hack", null)));

        assertEquals("1 Lê Lợi", db.rows.get(alices.getId()).getLine1()); // unchanged
    }

    @Test
    void address_delete_byNonOwner_isForbidden() {
        Address alices = add(ALICE, "1 Lê Lợi", null);

        assertThrows(ForbiddenException.class, () -> service.delete(BOB, alices.getId()));

        assertTrue(db.rows.containsKey(alices.getId())); // still there
    }

    @Test
    void address_setDefault_byNonOwner_isForbidden_andChangesNothing() {
        Address first = add(ALICE, "1 Lê Lợi", null);
        Address second = add(ALICE, "2 Lê Lợi", false);

        assertThrows(ForbiddenException.class, () -> service.setDefault(BOB, second.getId()));

        assertTrue(db.rows.get(first.getId()).isDefault());
        assertFalse(db.rows.get(second.getId()).isDefault());
    }

    @Test
    void getOwned_returnsTheAddressForItsOwner_andDistinguishesMissingFromForeign() {
        Address alices = add(ALICE, "1 Lê Lợi", null);

        assertSame(alices, service.getOwned(ALICE, alices.getId()));
        assertThrows(ForbiddenException.class, () -> service.getOwned(BOB, alices.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.getOwned(ALICE, 999L));
    }

    @Test
    void list_returnsOnlyTheCallersAddresses_defaultFirst() {
        Address a1 = add(ALICE, "1", null);
        Address a2 = add(ALICE, "2", false);
        add(BOB, "bob", null);

        List<Address> mine = service.list(ALICE);

        assertEquals(List.of(a1.getId(), a2.getId()), mine.stream().map(Address::getId).toList());
        assertTrue(mine.get(0).isDefault());
    }

    // ── default rules ───────────────────────────────────────────────────────────

    @Test
    void address_firstBecomesDefault() {
        Address first = add(ALICE, "1 Lê Lợi", false); // even when the client says false

        assertTrue(first.isDefault());
        assertEquals(1, db.defaultsOf(ALICE));
    }

    @Test
    void address_secondIsNotDefault_unlessAskedFor() {
        Address first = add(ALICE, "1", null);
        Address second = add(ALICE, "2", null);

        assertTrue(db.rows.get(first.getId()).isDefault());
        assertFalse(second.isDefault());
        assertEquals(1, db.defaultsOf(ALICE));
    }

    @Test
    void address_createWithIsDefaultTrue_movesTheDefault() {
        Address first = add(ALICE, "1", null);
        Address second = add(ALICE, "2", true);

        assertFalse(db.rows.get(first.getId()).isDefault());
        assertTrue(db.rows.get(second.getId()).isDefault());
        assertEquals(1, db.defaultsOf(ALICE));
    }

    @Test
    void address_setDefault_leavesExactlyOneDefault() {
        Address first = add(ALICE, "1", null);
        Address second = add(ALICE, "2", null);
        Address third = add(ALICE, "3", null);
        add(BOB, "bob", null); // another user's default must not be touched

        service.setDefault(ALICE, third.getId());

        assertEquals(1, db.defaultsOf(ALICE));
        assertTrue(db.rows.get(third.getId()).isDefault());
        assertFalse(db.rows.get(first.getId()).isDefault());
        assertFalse(db.rows.get(second.getId()).isDefault());
        assertEquals(1, db.defaultsOf(BOB));
    }

    @Test
    void address_setDefault_onTheCurrentDefault_isANoOp() {
        Address first = add(ALICE, "1", null);

        service.setDefault(ALICE, first.getId());

        assertTrue(db.rows.get(first.getId()).isDefault());
        assertEquals(1, db.defaultsOf(ALICE));
    }

    @Test
    void address_deleteDefault_promotesAnother() {
        Address first = add(ALICE, "1", null);   // default
        Address second = add(ALICE, "2", null);
        Address third = add(ALICE, "3", null);   // most recent

        service.delete(ALICE, first.getId());

        assertFalse(db.rows.containsKey(first.getId()));
        assertEquals(1, db.defaultsOf(ALICE));
        assertTrue(db.rows.get(third.getId()).isDefault(), "the most recently created remaining address is promoted");
        assertFalse(db.rows.get(second.getId()).isDefault());
    }

    @Test
    void address_deleteNonDefault_keepsTheDefault() {
        Address first = add(ALICE, "1", null);
        Address second = add(ALICE, "2", null);

        service.delete(ALICE, second.getId());

        assertTrue(db.rows.get(first.getId()).isDefault());
        assertEquals(1, db.defaultsOf(ALICE));
    }

    @Test
    void address_deleteLast_leavesAnEmptyBook() {
        Address only = add(ALICE, "1", null);

        service.delete(ALICE, only.getId());

        assertTrue(db.forUser(ALICE).isEmpty());
        // and the next address becomes the default again
        assertTrue(add(ALICE, "again", null).isDefault());
    }

    @Test
    void address_update_withIsDefaultFalse_cannotRemoveTheOnlyDefault() {
        Address first = add(ALICE, "1", null);

        service.update(ALICE, first.getId(), request("1 changed", false));

        assertTrue(db.rows.get(first.getId()).isDefault());
        assertEquals("1 changed", db.rows.get(first.getId()).getLine1());
    }

    @Test
    void address_update_withIsDefaultTrue_movesTheDefault() {
        Address first = add(ALICE, "1", null);
        Address second = add(ALICE, "2", null);

        service.update(ALICE, second.getId(), request("2 changed", true));

        assertFalse(db.rows.get(first.getId()).isDefault());
        assertTrue(db.rows.get(second.getId()).isDefault());
        assertEquals(1, db.defaultsOf(ALICE));
    }

    // ── limit ───────────────────────────────────────────────────────────────────

    @Test
    void address_eleventh_isRejected() {
        for (int i = 1; i <= AddressService.MAX_ADDRESSES; i++) add(ALICE, "addr " + i, null);
        assertEquals(10, db.forUser(ALICE).size());

        assertThrows(BusinessRuleViolationException.class, () -> add(ALICE, "addr 11", null));

        assertEquals(10, db.forUser(ALICE).size());
        assertEquals(1, db.defaultsOf(ALICE));
    }

    @Test
    void address_limitIsPerUser() {
        for (int i = 1; i <= AddressService.MAX_ADDRESSES; i++) add(ALICE, "addr " + i, null);

        Address bobs = add(BOB, "bob 1", null);

        assertTrue(bobs.isDefault());
    }

    @Test
    void address_fieldsAreTrimmed_andOwnerComesFromTheCaller() {
        Address saved = service.create(ALICE, new AddressRequest("  Lan  ", " 0901234567 ", " 1 Lê Lợi ", " P1 ", " Q1 ", " HCM ", null));

        assertEquals("Lan", saved.getRecipientName());
        assertEquals("0901234567", saved.getPhone());
        assertEquals("1 Lê Lợi", saved.getLine1());
        assertEquals(ALICE, saved.getUserId());
    }
}

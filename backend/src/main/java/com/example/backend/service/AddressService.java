package com.example.backend.service;

import com.example.backend.dto.AddressRequest;
import com.example.backend.entity.Address;
import com.example.backend.exception.BusinessRuleViolationException;
import com.example.backend.exception.ForbiddenException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.AddressRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * B08 address book. Rules enforced here (the partial unique index {@code uq_addresses_one_default} is only the backstop):
 * <ul>
 *   <li>at most {@link #MAX_ADDRESSES} addresses per user;</li>
 *   <li>exactly one default per non-empty book: the first address becomes the default, deleting the default promotes the
 *       most recently created remaining address, and a default can only be moved by choosing another default;</li>
 *   <li>every operation by id verifies that the address belongs to the caller (the user id always comes from the token).</li>
 * </ul>
 * Mutations lock the user's row first, so two concurrent requests of one user (two creates, create + delete) run one after
 * the other and the 10-address limit and the single default cannot be raced.
 */
@Service
public class AddressService {

    public static final int MAX_ADDRESSES = 10;

    static final String NOT_OWNER = "Bạn không có quyền truy cập địa chỉ này.";
    static final String LIMIT_REACHED = "Sổ địa chỉ chỉ lưu tối đa " + MAX_ADDRESSES + " địa chỉ. Hãy xóa bớt địa chỉ cũ.";

    @Autowired private AddressRepository addressRepository;
    @Autowired private UserRepository userRepository;

    /** The caller's address book: the default first, then newest first. */
    @Transactional(readOnly = true)
    public List<Address> list(Long userId) {
        return addressRepository.findAllByUser(userId);
    }

    /**
     * The address with {@code addressId} if it belongs to {@code userId}. Public contract for B04 (checkout address picker):
     * the checkout must call this with the user id from the token and copy the fields into the order snapshot.
     *
     * @throws ResourceNotFoundException the address does not exist (404)
     * @throws ForbiddenException        it exists but belongs to someone else (403)
     */
    @Transactional(readOnly = true)
    public Address getOwned(Long userId, Long addressId) {
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ #" + addressId));
        if (userId == null || !userId.equals(address.getUserId())) {
            throw new ForbiddenException(NOT_OWNER);
        }
        return address;
    }

    @Transactional
    public Address create(Long userId, AddressRequest request) {
        lockOwner(userId);
        long existing = addressRepository.countByUserId(userId);
        if (existing >= MAX_ADDRESSES) {
            throw new BusinessRuleViolationException(LIMIT_REACHED);
        }
        Address address = new Address();
        address.setUserId(userId);
        copyFields(address, request);
        boolean wantsDefault = existing == 0 || Boolean.TRUE.equals(request.isDefault());
        address.setDefault(false);
        Address saved = addressRepository.saveAndFlush(address);
        if (wantsDefault) {
            applyDefault(saved);
        }
        return saved;
    }

    @Transactional
    public Address update(Long userId, Long addressId, AddressRequest request) {
        lockOwner(userId);
        Address address = getOwned(userId, addressId);
        copyFields(address, request);
        // isDefault=true moves the default here; false / null keep the current flag (a book never ends up without a default).
        if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefault()) {
            applyDefault(address);
        }
        return addressRepository.save(address);
    }

    @Transactional
    public Address setDefault(Long userId, Long addressId) {
        lockOwner(userId);
        Address address = getOwned(userId, addressId);
        if (!address.isDefault()) {
            applyDefault(address);
        }
        return address;
    }

    @Transactional
    public void delete(Long userId, Long addressId) {
        lockOwner(userId);
        Address address = getOwned(userId, addressId);
        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);
        addressRepository.flush();
        if (wasDefault) {
            addressRepository.findFirstByUserIdOrderByIdDesc(userId).ifPresent(this::applyDefault);
        }
    }

    /** Makes {@code address} the only default of its owner: the other defaults are cleared first (and flushed). */
    private void applyDefault(Address address) {
        addressRepository.clearDefaultsExcept(address.getUserId(), address.getId(), LocalDateTime.now());
        address.setDefault(true);
        addressRepository.saveAndFlush(address);
    }

    /** Serialises the mutations of one user's book (also covers the empty-book case that a row lock on addresses cannot). */
    private void lockOwner(Long userId) {
        if (userId == null || userRepository.lockRowById(userId).isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy người dùng.");
        }
    }

    private static void copyFields(Address address, AddressRequest request) {
        address.setRecipientName(request.recipientName().trim());
        address.setPhone(request.phone().trim());
        address.setLine1(request.line1().trim());
        address.setWard(request.ward().trim());
        address.setDistrict(request.district().trim());
        address.setProvince(request.province().trim());
    }
}

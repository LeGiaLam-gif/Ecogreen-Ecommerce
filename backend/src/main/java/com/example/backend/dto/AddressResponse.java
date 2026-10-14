package com.example.backend.dto;

import com.example.backend.entity.Address;

import java.time.LocalDateTime;

/** One address of the caller's book. No user id is exposed (the book always belongs to the caller). */
public class AddressResponse {
    public Long id;
    public String recipientName;
    public String phone;
    public String line1;
    public String ward;
    public String district;
    public String province;
    public boolean isDefault;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public static AddressResponse from(Address a) {
        AddressResponse r = new AddressResponse();
        r.id = a.getId();
        r.recipientName = a.getRecipientName();
        r.phone = a.getPhone();
        r.line1 = a.getLine1();
        r.ward = a.getWard();
        r.district = a.getDistrict();
        r.province = a.getProvince();
        r.isDefault = a.isDefault();
        r.createdAt = a.getCreatedAt();
        r.updatedAt = a.getUpdatedAt();
        return r;
    }
}

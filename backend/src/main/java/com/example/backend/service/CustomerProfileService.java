package com.example.backend.service;

import com.example.backend.dto.CustomerProfileRequest;
import com.example.backend.entity.User;
import com.example.backend.exception.ConflictException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerProfileService {

    @Autowired private UserRepository userRepository;

    @Transactional(readOnly = true)
    public User get(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng."));
    }

    @Transactional
    public User update(Long userId, CustomerProfileRequest request) {
        User user = get(userId);
        String username = request.username().trim();
        String email = request.email().trim();
        if (userRepository.existsByUsernameIgnoreCase(username) && !user.getUsername().equalsIgnoreCase(username)) {
            throw new ConflictException("Tên đăng nhập đã được sử dụng.");
        }
        if (userRepository.existsByEmailIgnoreCase(email) && !user.getEmail().equalsIgnoreCase(email)) {
            throw new ConflictException("Email đã được sử dụng.");
        }
        user.setUsername(username);
        user.setEmail(email);
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Tên đăng nhập hoặc email đã được sử dụng.");
        }
    }
}

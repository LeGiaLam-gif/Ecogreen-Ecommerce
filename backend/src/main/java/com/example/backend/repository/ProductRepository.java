package com.example.backend.repository;

import com.example.backend.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByCategoryId(Long categoryId);

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByStatus(Product.Status status);

    @Override
    @EntityGraph(attributePaths = {"category"})
    List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findById(Long id);

    List<Product> findByNameContainingIgnoreCase(String name);
}

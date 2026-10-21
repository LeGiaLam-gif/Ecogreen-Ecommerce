package com.example.backend.entity;

import jakarta.persistence.*;

/**
 * A photo of a {@link Return}. Files uploaded with the return live in the same object storage as the catalogue images and
 * are served through {@code /api/v1/files/{key}}; a row copied from the legacy {@code image_url} keeps its external link.
 */
@Entity
@Table(name = "return_images")
public class ReturnImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id", nullable = false)
    private Return returnRequest;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public Long getId() { return id; }

    public Return getReturnRequest() { return returnRequest; }
    public void setReturnRequest(Return returnRequest) { this.returnRequest = returnRequest; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}

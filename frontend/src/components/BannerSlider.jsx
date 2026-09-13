import React, { useState, useEffect, useRef } from 'react';
import './BannerSlider.css';

// Tự động quét toàn bộ ảnh trong thư mục assets/banner
const bannerModules = import.meta.glob('../assets/banner/*.{png,jpg,jpeg,webp}', { eager: true });

const BannerSlider = () => {
    // Chuyển đổi các module ảnh thành mảng dữ liệu banner
    const bannerItems = Object.entries(bannerModules).map(([path, module], index) => {
        const filename = path.split('/').pop().split('.')[0];
        // Đổi tên "banner1" thành "Khuyến mãi 1" hoặc giữ nguyên tùy ý, ở đây tôi định dạng lại cho đẹp
        const title = filename.charAt(0).toUpperCase() + filename.slice(1);
        
        return {
            id: index + 1,
            image: module.default,
            title: title.replace(/\d+/, (match) => ` #${match}`), // ví dụ: Banner1 -> Banner #1
            description: "Khám phá các chương trình ưu đãi mới nhất chỉ có tại EcoGreen."
        };
    });

    const [currentIndex, setCurrentIndex] = useState(0);
    const [isAutoPlaying, setIsAutoPlaying] = useState(true);
    const timeoutRef = useRef(null);

    const resetTimeout = () => {
        if (timeoutRef.current) {
            clearTimeout(timeoutRef.current);
        }
    };

    const nextSlide = () => {
        setCurrentIndex((prev) => (prev === bannerItems.length - 1 ? 0 : prev + 1));
    };

    const prevSlide = () => {
        setCurrentIndex((prev) => (prev === 0 ? bannerItems.length - 1 : prev - 1));
    };

    const goToSlide = (index) => {
        setCurrentIndex(index);
    };

    useEffect(() => {
        if (isAutoPlaying) {
            resetTimeout();
            timeoutRef.current = setTimeout(nextSlide, 4000);
        }
        return () => resetTimeout();
    }, [currentIndex, isAutoPlaying]);

    if (!bannerItems.length) return null;

    return (
        <div 
            className="banner-slider"
            onMouseEnter={() => setIsAutoPlaying(false)}
            onMouseLeave={() => setIsAutoPlaying(true)}
        >
            <div className="slider-container-inner">
                <div 
                    className="slider-wrapper" 
                    style={{ transform: `translateX(-${currentIndex * 100}%)` }}
                >
                    {bannerItems.map((item, index) => (
                        <div className="slider-item" key={item.id}>
                            <img src={item.image} alt={item.title} className="slider-img" />
                        </div>
                    ))}
                </div>
            </div>

            {/* Điều hướng - Hiện đã nằm ngang hàng với slider-container-inner để có thể căn lọt ra ngoài */}
            <button className="nav-btn prev" onClick={prevSlide}>&#10094;</button>
            <button className="nav-btn next" onClick={nextSlide}>&#10095;</button>

            {/* Các chấm chỉ số */}
            <div className="slider-dots">
                {bannerItems.map((_, index) => (
                    <span 
                        key={index} 
                        className={`dot ${index === currentIndex ? 'active' : ''}`}
                        onClick={() => goToSlide(index)}
                    ></span>
                ))}
            </div>
        </div>
    );
};

export default BannerSlider;

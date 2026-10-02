import React, { useState, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import './BannerSlider.css';

// Tự động quét toàn bộ ảnh trong thư mục assets/banner
const bannerModules = import.meta.glob('../assets/banner/*.{png,jpg,jpeg,webp}', { eager: true });

// Nội dung text tương ứng với từng banner
const BANNER_CONTENT = [
  {
    title: 'Sống Xanh Mỗi Ngày',
    subtitle: 'Sản phẩm thân thiện môi trường — được làm từ vật liệu tái chế 100%',
    cta: 'Khám Phá Ngay',
    ctaLink: '/',
    tag: '🌿 Eco Lifestyle',
    align: 'left',
  },
  {
    title: 'Tái Chế Là Tương Lai',
    subtitle: 'Từ balo đay Nepal đến rèm polyester tái sinh — thiên nhiên cảm ơn bạn',
    cta: 'Mua Ngay',
    ctaLink: '/',
    tag: '♻️ Recycled Products',
    align: 'left',
  },
  {
    title: 'Không Rác Thải Nhựa',
    subtitle: 'Đóng gói xanh, giấy tái chế & sợi tự nhiên — ít nhựa hơn mỗi ngày',
    cta: 'Xem Bộ Sưu Tập',
    ctaLink: '/',
    tag: '🌍 Zero Waste',
    align: 'right',
  },
  {
    title: 'Ưu Đãi Cuối Tuần',
    subtitle: 'Sản phẩm sinh thái chọn lọc — giao hàng miễn phí cho đơn từ 500K',
    cta: 'Nhận Ưu Đãi',
    ctaLink: '/',
    tag: '🎁 Weekend Sale',
    align: 'center',
  },
];

const BannerSlider = () => {
  const bannerImages = Object.values(bannerModules).map((m) => m.default);

  const bannerItems = bannerImages.map((image, index) => ({
    id: index + 1,
    image,
    ...BANNER_CONTENT[index % BANNER_CONTENT.length],
  }));

  const [currentIndex, setCurrentIndex] = useState(0);
  const [isAutoPlaying, setIsAutoPlaying] = useState(true);
  const [animating, setAnimating] = useState(false);
  const timeoutRef = useRef(null);

  const resetTimeout = () => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current);
  };

  const goToSlide = (index) => {
    if (animating || index === currentIndex) return;
    setAnimating(true);
    setCurrentIndex(index);
    setTimeout(() => setAnimating(false), 700);
  };

  const nextSlide = () => goToSlide((currentIndex + 1) % bannerItems.length);
  const prevSlide = () => goToSlide((currentIndex - 1 + bannerItems.length) % bannerItems.length);

  useEffect(() => {
    if (isAutoPlaying) {
      resetTimeout();
      timeoutRef.current = setTimeout(nextSlide, 5000);
    }
    return () => resetTimeout();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentIndex, isAutoPlaying]);

  if (!bannerItems.length) return null;

  const current = bannerItems[currentIndex];

  return (
    <div
      className="banner-slider"
      onMouseEnter={() => setIsAutoPlaying(false)}
      onMouseLeave={() => setIsAutoPlaying(true)}
    >
      {/* Slides */}
      <div className="slider-container-inner">
        <div
          className="slider-wrapper"
          style={{ transform: `translateX(-${currentIndex * 100}%)` }}
        >
          {bannerItems.map((item) => (
            <div className="slider-item" key={item.id}>
              <img src={item.image} alt={item.title} className="slider-img" />
              <div className="slider-overlay" />
            </div>
          ))}
        </div>
      </div>

      {/* Text Overlay - re-animates on key change */}
      <div className={`banner-content banner-content--${current.align}`} key={`content-${currentIndex}`}>
        <span className="banner-tag">{current.tag}</span>
        <h2 className="banner-title">{current.title}</h2>
        <p className="banner-subtitle">{current.subtitle}</p>
        <Link to={current.ctaLink} className="banner-cta">{current.cta} →</Link>
      </div>

      {/* Nav Buttons */}
      <button className="nav-btn prev" onClick={prevSlide} aria-label="Slide trước">&#10094;</button>
      <button className="nav-btn next" onClick={nextSlide} aria-label="Slide tiếp">&#10095;</button>

      {/* Progress dots */}
      <div className="slider-dots">
        {bannerItems.map((_, index) => (
          <button
            key={index}
            className={`dot ${index === currentIndex ? 'active' : ''}`}
            onClick={() => goToSlide(index)}
            aria-label={`Banner ${index + 1}`}
          />
        ))}
      </div>

      {/* Auto-play progress bar */}
      {isAutoPlaying && (
        <div className="banner-progress">
          <div className="banner-progress-bar" key={`bar-${currentIndex}`} />
        </div>
      )}
    </div>
  );
};

export default BannerSlider;

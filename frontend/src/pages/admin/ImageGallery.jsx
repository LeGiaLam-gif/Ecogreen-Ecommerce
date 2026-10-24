import React, { useState } from 'react';

/**
 * Thumbnails of a list of image URLs with a simple viewer (previous / next / Esc-free close button). Used for the photos a
 * customer attached to a return. `urls` are the server's `/api/v1/files/...` paths, shown as they are.
 */
const ImageGallery = ({ urls, thumbSize = 56 }) => {
  const [openIndex, setOpenIndex] = useState(null);
  if (!urls || urls.length === 0) return <span className="admin-cell-muted">—</span>;

  const go = (delta) => setOpenIndex((i) => (i + delta + urls.length) % urls.length);

  return (
    <>
      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', maxWidth: `${(thumbSize + 6) * 3}px` }}>
        {urls.map((url, i) => (
          <button
            key={url}
            type="button"
            onClick={() => setOpenIndex(i)}
            aria-label={`Xem ảnh ${i + 1} / ${urls.length}`}
            style={{ border: '1px solid #e5e7eb', borderRadius: '6px', padding: 0, background: 'none', cursor: 'zoom-in' }}
          >
            <img src={url} alt={`Ảnh ${i + 1}`} style={{ width: thumbSize, height: thumbSize, objectFit: 'cover', borderRadius: '6px', display: 'block' }} />
          </button>
        ))}
      </div>

      {openIndex !== null && (
        <div className="admin-modal-overlay" onClick={() => setOpenIndex(null)}>
          <div className="admin-modal-card" style={{ maxWidth: '760px', textAlign: 'center' }} onClick={(e) => e.stopPropagation()}>
            <img src={urls[openIndex]} alt={`Ảnh ${openIndex + 1}`} style={{ maxWidth: '100%', maxHeight: '70vh', borderRadius: '8px' }} />
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '14px', marginTop: '12px' }}>
              <button type="button" disabled={urls.length < 2} onClick={() => go(-1)} className="admin-btn-secondary">← Trước</button>
              <span>Ảnh {openIndex + 1} / {urls.length}</span>
              <button type="button" disabled={urls.length < 2} onClick={() => go(1)} className="admin-btn-secondary">Sau →</button>
              <a href={urls[openIndex]} target="_blank" rel="noreferrer">Mở ảnh gốc</a>
              <button type="button" onClick={() => setOpenIndex(null)} className="admin-btn-primary">Đóng</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
};

export default ImageGallery;

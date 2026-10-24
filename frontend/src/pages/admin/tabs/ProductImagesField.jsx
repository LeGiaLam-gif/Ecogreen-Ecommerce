import React from 'react';

export const PRODUCT_IMAGE_MAX_BYTES = 5 * 1024 * 1024; // the server limit; the server also checks the real content type
export const PRODUCT_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * Gallery of a product: the images already saved (shown in `sortOrder`, deletable) and a queue of new files to upload.
 * The server gives a new image the next `sortOrder` and has no endpoint to reorder saved ones, so the ORDER OF THE QUEUE
 * is the order the files get (they are uploaded one after another). Move a file up or down before saving.
 */
const ProductImagesField = ({ savedImages, canEdit, queue, onQueueChange, onDeleteSaved, error }) => {
  const addFiles = (fileList) => {
    const added = [];
    const rejected = [];
    Array.from(fileList).forEach((file) => {
      if (!PRODUCT_IMAGE_TYPES.includes(file.type)) rejected.push(`${file.name}: chỉ nhận JPG, PNG hoặc WebP`);
      else if (file.size > PRODUCT_IMAGE_MAX_BYTES) rejected.push(`${file.name}: lớn hơn 5 MB`);
      else added.push({ key: `${file.name}-${file.size}-${file.lastModified}-${Math.random()}`, file, preview: URL.createObjectURL(file) });
    });
    onQueueChange([...queue, ...added], rejected);
  };

  const move = (index, delta) => {
    const next = [...queue];
    const target = index + delta;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    onQueueChange(next, []);
  };

  const remove = (index) => {
    URL.revokeObjectURL(queue[index].preview);
    onQueueChange(queue.filter((_, i) => i !== index), []);
  };

  const sorted = [...(savedImages || [])].sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
  const thumb = { width: '84px', height: '84px', objectFit: 'cover', borderRadius: '6px', border: '1px solid #e5e7eb' };

  return (
    <div>
      <label className="admin-form-label">Thư viện ảnh (JPG, PNG, WebP, mỗi ảnh tối đa 5 MB)</label>

      {sorted.length > 0 && (
        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', marginBottom: '10px' }}>
          {sorted.map((img, i) => (
            <div key={img.id} style={{ textAlign: 'center', width: '90px' }}>
              <img src={img.url} alt={img.altText || ''} style={thumb} />
              <div style={{ fontSize: '0.72rem', color: '#6b7280' }}>{i === 0 ? 'Ảnh chính' : `Thứ tự ${i + 1}`}</div>
              {canEdit && (
                <button type="button" className="admin-btn-action-delete" style={{ padding: '2px 8px', fontSize: '0.75rem' }}
                  onClick={() => onDeleteSaved(img)}>
                  Xóa
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {canEdit && (
        <>
          {queue.length > 0 && (
            <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', marginBottom: '10px' }}>
              {queue.map((item, i) => (
                <div key={item.key} style={{ textAlign: 'center', width: '96px' }}>
                  <img src={item.preview} alt="" style={thumb} />
                  <div style={{ fontSize: '0.72rem', color: '#6b7280' }}>Chờ tải lên · #{i + 1}</div>
                  <div style={{ display: 'flex', justifyContent: 'center', gap: '2px' }}>
                    <button type="button" aria-label="Chuyển lên" disabled={i === 0} onClick={() => move(i, -1)}>←</button>
                    <button type="button" aria-label="Chuyển xuống" disabled={i === queue.length - 1} onClick={() => move(i, 1)}>→</button>
                    <button type="button" aria-label="Bỏ ảnh" onClick={() => remove(i)}>✕</button>
                  </div>
                </div>
              ))}
            </div>
          )}
          <input
            type="file"
            accept={PRODUCT_IMAGE_TYPES.join(',')}
            multiple
            onChange={(e) => { addFiles(e.target.files); e.target.value = ''; }}
          />
          <div style={{ fontSize: '0.78rem', color: '#6b7280', marginTop: '4px' }}>
            Ảnh được lưu theo đúng thứ tự trong hàng chờ; ảnh đã lưu chỉ xóa được, chưa đổi thứ tự được.
          </div>
        </>
      )}
      {error && <p className="admin-error-text">{error}</p>}
    </div>
  );
};

export default ProductImagesField;

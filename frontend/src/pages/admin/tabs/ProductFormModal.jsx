import React from 'react';
import { usePermissions } from '../../../hooks/usePermission';
import { PERM } from '../../../utils/permissions';
import { categoryOptionLabel } from '../../../utils/categoryTree';
import ProductImagesField from './ProductImagesField';

const STATUS_OPTIONS_EDIT = [
  ['DRAFT', 'Bản nháp (DRAFT)'],
  ['OUT_OF_STOCK', 'Hết hàng (OUT_OF_STOCK)'],
  ['INACTIVE', 'Ngừng kinh doanh (INACTIVE)'],
  ['ARCHIVED', 'Lưu trữ (ARCHIVED)'],
];

/**
 * UX hint only: the four things the server requires before a product can be published (B02 section 6). The server decides
 * and lists what is missing in its 400 message, which is shown under the form.
 */
const publishChecklist = (p, savedImageCount, queuedCount) => [
  { ok: Boolean(p.name && p.name.trim()), label: 'Có tên sản phẩm' },
  { ok: Number(p.price) > 0, label: 'Giá bán lớn hơn 0' },
  { ok: Boolean(p.categoryId), label: 'Đã chọn danh mục' },
  { ok: savedImageCount + queuedCount > 0 || Boolean(p.image && p.image.trim()), label: 'Có ít nhất một ảnh' },
];

/**
 * Create / edit product dialog. The parent owns the form state and the save routine (see ProductsTab). Saving as a draft
 * and publishing are separate buttons: a new product is always created as DRAFT and goes live only through "Xuất bản".
 */
const ProductFormModal = ({
  currentProduct, setCurrentProduct, isEditProduct, categories, saving, formError,
  queue, onQueueChange, onDeleteSavedImage, imageError,
  onSave, onClose,
}) => {
  const { can } = usePermissions();
  const canPublish = can(PERM.PRODUCT_PUBLISH);
  // Uploading and deleting images is product:update on the server even for a product that is being created (a user with
  // product:create only can save the draft, then someone with product:update adds the pictures).
  const canImages = can(PERM.PRODUCT_UPDATE);
  const savedImages = currentProduct.images || [];
  const status = currentProduct.status;
  const publishable = !isEditProduct || status === 'DRAFT' || status === 'INACTIVE';
  const checklist = publishChecklist(currentProduct, savedImages.length, queue.length);
  const comparePriceTooLow = currentProduct.comparePrice !== '' && currentProduct.comparePrice != null
    && Number(currentProduct.comparePrice) < Number(currentProduct.price);

  const set = (patch) => setCurrentProduct({ ...currentProduct, ...patch });
  const submit = (e) => {
    e.preventDefault();
    onSave({ publish: false });
  };

  return (
    <div className="admin-modal-overlay">
      <div className="admin-modal-card">
        <h3 style={{ color: '#1b3a1f', marginTop: 0 }}>
          {isEditProduct ? '✏️ Chỉnh sửa sản phẩm' : '➕ Thêm sản phẩm mới'}
        </h3>
        <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
          <div>
            <label className="admin-form-label">Tên sản phẩm *</label>
            <input
              type="text"
              placeholder="Ví dụ: Balo Vải Đay Tự Nhiên"
              value={currentProduct.name}
              onChange={(e) => set({ name: e.target.value })}
              required
              className="admin-form-input"
            />
            <small className="admin-cell-muted">
              Đường dẫn (slug): {isEditProduct && currentProduct.slug
                ? <strong>{currentProduct.slug}</strong>
                : 'tự sinh từ tên khi lưu'}
              {' '}— máy chủ tự tạo và cập nhật khi đổi tên, chưa cho sửa tay.
            </small>
          </div>

          <div>
            <label className="admin-form-label">Danh mục sản phẩm *</label>
            <select
              value={currentProduct.categoryId}
              onChange={(e) => set({ categoryId: e.target.value })}
              required
              className="admin-form-select"
            >
              <option value="">-- Chọn danh mục --</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>{categoryOptionLabel(c)}</option>
              ))}
            </select>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
            <div>
              <label className="admin-form-label">Giá bán (₫) *</label>
              <input
                type="number"
                step="1000"
                min="0"
                placeholder="150000"
                value={currentProduct.price}
                onChange={(e) => set({ price: e.target.value })}
                required
                className="admin-form-input"
              />
            </div>
            <div>
              <label className="admin-form-label">{isEditProduct ? 'Có thể bán (chỉ xem)' : 'Tồn kho ban đầu *'}</label>
              <input
                type="number"
                min="0"
                placeholder="50"
                value={currentProduct.stockQuantity}
                onChange={(e) => set({ stockQuantity: parseInt(e.target.value, 10) || 0 })}
                disabled={isEditProduct}
                required
                className="admin-form-input"
              />
              {isEditProduct && <small className="admin-cell-muted">Đổi tồn kho ở tab “Kho hàng” (có lý do và lịch sử).</small>}
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '12px' }}>
            <div>
              <label className="admin-form-label">Giá so sánh (₫)</label>
              <input
                type="number"
                step="1000"
                min="0"
                placeholder="Tùy chọn, ≥ giá bán"
                value={currentProduct.comparePrice ?? ''}
                onChange={(e) => set({ comparePrice: e.target.value })}
                className="admin-form-input"
              />
              {comparePriceTooLow && <small className="admin-error-text">Giá so sánh phải không nhỏ hơn giá bán.</small>}
            </div>
            <div>
              <label className="admin-form-label">SKU</label>
              <input
                type="text"
                maxLength={64}
                value={currentProduct.sku || ''}
                onChange={(e) => set({ sku: e.target.value })}
                className="admin-form-input"
              />
            </div>
            <div>
              <label className="admin-form-label">Thương hiệu</label>
              <input
                type="text"
                maxLength={100}
                value={currentProduct.brand || ''}
                onChange={(e) => set({ brand: e.target.value })}
                className="admin-form-input"
              />
            </div>
          </div>

          <ProductImagesField
            savedImages={savedImages}
            canEdit={canImages}
            queue={queue}
            onQueueChange={onQueueChange}
            onDeleteSaved={onDeleteSavedImage}
            error={imageError}
          />

          <div>
            <label className="admin-form-label">Ảnh đơn cũ (URL hoặc tên file trong assets/products, tùy chọn)</label>
            <input
              type="text"
              placeholder="https://... hoặc ten-anh.jpg"
              value={currentProduct.image || ''}
              onChange={(e) => set({ image: e.target.value })}
              className="admin-form-input"
            />
          </div>

          <div>
            <label className="admin-form-label">Mô tả sản phẩm</label>
            <textarea
              placeholder="Mô tả chất liệu tái chế, nguồn gốc xuất xứ, đặc tính sinh thái..."
              rows={3}
              value={currentProduct.description || ''}
              onChange={(e) => set({ description: e.target.value })}
              className="admin-form-textarea"
            />
          </div>

          {isEditProduct ? (
            <div>
              <label className="admin-form-label">Trạng thái kinh doanh</label>
              <select value={status} onChange={(e) => set({ status: e.target.value })} className="admin-form-select">
                {status === 'ACTIVE' && <option value="ACTIVE">Đang kinh doanh (ACTIVE)</option>}
                {STATUS_OPTIONS_EDIT.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
              {status !== 'ACTIVE' && <small className="admin-cell-muted">Để bán sản phẩm, dùng nút “Lưu &amp; xuất bản”.</small>}
            </div>
          ) : (
            <small className="admin-cell-muted">Sản phẩm mới được lưu ở trạng thái <strong>Bản nháp</strong>; bấm “Lưu &amp; xuất bản” khi đã đủ thông tin.</small>
          )}

          {publishable && canPublish && (
            <div style={{ background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: '8px', padding: '10px 12px' }}>
              <strong style={{ fontSize: '0.88rem' }}>Điều kiện để xuất bản</strong>
              <ul style={{ margin: '6px 0 0', paddingLeft: '20px', fontSize: '0.85rem' }}>
                {checklist.map((c) => (
                  <li key={c.label} style={{ color: c.ok ? '#15803d' : '#b91c1c' }}>{c.ok ? '✓' : '✗'} {c.label}</li>
                ))}
              </ul>
            </div>
          )}

          {formError && <p className="admin-error-text" role="alert">{formError}</p>}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px', flexWrap: 'wrap' }}>
            <button type="button" onClick={onClose} className="admin-btn-secondary">Hủy</button>
            <button type="submit" disabled={saving} className="admin-btn-primary">
              {saving ? 'Đang lưu...' : (isEditProduct ? 'Lưu thay đổi' : 'Lưu bản nháp')}
            </button>
            {publishable && canPublish && (
              <button type="button" disabled={saving} onClick={() => onSave({ publish: true })} className="admin-btn-primary">
                Lưu &amp; xuất bản
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  );
};

export default ProductFormModal;

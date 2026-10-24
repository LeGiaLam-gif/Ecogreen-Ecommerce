import React from 'react';
import { categoryOptionLabel } from '../../../utils/categoryTree';

/**
 * Create / edit category dialog. The parent owns the form state and the submit handler (see CategoriesTab).
 * `parentOptions` is the flat tree WITHOUT the category being edited and its descendants (a category cannot become the
 * child of one of them; the server rejects that too, with a 400).
 */
const CategoryFormModal = ({ currentCategory, setCurrentCategory, isEditCategory, parentOptions, error, saving, onSubmit, onClose }) => (
  <div className="admin-modal-overlay">
    <div className="admin-modal-card" style={{ maxWidth: '460px' }}>
      <h3 style={{ color: '#1b3a1f', marginTop: 0 }}>
        {isEditCategory ? '✏️ Chỉnh sửa danh mục' : '➕ Thêm danh mục mới'}
      </h3>
      <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
        <div>
          <label className="admin-form-label">Tên danh mục *</label>
          <input
            type="text"
            placeholder="Ví dụ: Thời Trang & Phụ Kiện Xanh"
            value={currentCategory.name}
            onChange={(e) => setCurrentCategory({ ...currentCategory, name: e.target.value })}
            required
            className="admin-form-input"
          />
        </div>
        <div>
          <label className="admin-form-label">Danh mục cha</label>
          <select
            value={currentCategory.parentId ?? ''}
            onChange={(e) => setCurrentCategory({ ...currentCategory, parentId: e.target.value === '' ? null : Number(e.target.value) })}
            className="admin-form-select"
          >
            <option value="">— Không có (danh mục gốc) —</option>
            {parentOptions.map((c) => (
              <option key={c.id} value={c.id}>{categoryOptionLabel(c)}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="admin-form-label">Mô tả danh mục</label>
          <textarea
            placeholder="Mô tả về nhóm sản phẩm sinh thái trong danh mục này..."
            rows={3}
            value={currentCategory.description || ''}
            onChange={(e) => setCurrentCategory({ ...currentCategory, description: e.target.value })}
            className="admin-form-textarea"
          />
        </div>
        {error && <p className="admin-error-text" role="alert">{error}</p>}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '10px' }}>
          <button type="button" onClick={onClose} className="admin-btn-secondary">
            Hủy
          </button>
          <button type="submit" disabled={saving} className="admin-btn-primary">
            {saving ? 'Đang lưu...' : 'Lưu danh mục'}
          </button>
        </div>
      </form>
    </div>
  </div>
);

export default CategoryFormModal;

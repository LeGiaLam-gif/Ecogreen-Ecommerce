import React, { useEffect, useMemo, useState } from 'react';
import { getCategoryTree, createCategory, updateCategory, deleteCategory } from '../../../services/categoryApi';
import { flattenTree, descendantIds } from '../../../utils/categoryTree';
import { usePermissions } from '../../../hooks/usePermission';
import { PERM } from '../../../utils/permissions';
import CategoryFormModal from './CategoryFormModal';

const emptyCategory = { name: '', description: '', parentId: null };

/** "Danh mục" tab: the category tree (B02), expandable by parent. Create / edit / delete are gated by their permission. */
const CategoriesTab = () => {
  const { can } = usePermissions();
  const [errorMsg, setErrorMsg] = useState('');
  const [tree, setTree] = useState([]);
  const [collapsed, setCollapsed] = useState(() => new Set()); // ids of the nodes whose children are hidden
  const [isCategoryModalOpen, setIsCategoryModalOpen] = useState(false);
  const [currentCategory, setCurrentCategory] = useState(emptyCategory);
  const [isEditCategory, setIsEditCategory] = useState(false);
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  const reload = () => setReloadKey((n) => n + 1);

  useEffect(() => {
    let cancelled = false;
    getCategoryTree()
      .then((t) => {
        if (cancelled) return;
        setTree(t);
        setErrorMsg('');
      })
      .catch((error) => {
        if (!cancelled) setErrorMsg(error.friendlyMessage || 'Không thể tải dữ liệu.');
      });
    return () => { cancelled = true; };
  }, [reloadKey]);

  const flat = useMemo(() => flattenTree(tree), [tree]);

  // Rows in tree order, skipping the children of a collapsed node.
  const visibleRows = useMemo(() => {
    const rows = [];
    const walk = (nodes, depth) => nodes.forEach((node) => {
      rows.push({ ...node, depth });
      if (!collapsed.has(node.id)) walk(node.children || [], depth + 1);
    });
    walk(tree, 0);
    return rows;
  }, [tree, collapsed]);

  const toggle = (id) => setCollapsed((prev) => {
    const next = new Set(prev);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
  });

  const parentOptions = isEditCategory
    ? (() => {
      const blocked = descendantIds(flat, currentCategory.id);
      blocked.add(currentCategory.id);
      return flat.filter((c) => !blocked.has(c.id));
    })()
    : flat;

  const openCreate = (parentId = null) => {
    setFormError('');
    setSaving(false);
    setIsEditCategory(false);
    setCurrentCategory({ ...emptyCategory, parentId });
    setIsCategoryModalOpen(true);
  };

  const openEdit = (c) => {
    setFormError('');
    setSaving(false);
    setIsEditCategory(true);
    setCurrentCategory({ id: c.id, name: c.name, description: c.description || '', parentId: c.parentId ?? null });
    setIsCategoryModalOpen(true);
  };

  const handleDeleteCategory = async (c) => {
    if (!window.confirm(`Xóa danh mục "${c.name}"? Chỉ xóa được khi không còn sản phẩm hoặc danh mục con.`)) return;
    try {
      await deleteCategory(c.id);
      reload();
    } catch (e) {
      alert(e.friendlyMessage || 'Thao tác thất bại.');
    }
  };

  const handleCategorySubmit = async (e) => {
    e.preventDefault();
    setSaving(true);
    setFormError('');
    try {
      if (isEditCategory) await updateCategory(currentCategory.id, currentCategory);
      else await createCategory(currentCategory);
      setIsCategoryModalOpen(false);
      reload();
    } catch (err) {
      const fields = err.fields ? Object.values(err.fields).filter(Boolean).join(' ') : '';
      setFormError([err.friendlyMessage, fields].filter(Boolean).join(' ') || 'Không thể lưu thông tin danh mục.');
      setSaving(false);
    }
  };

  return (
    <>
      {errorMsg && <p className="admin-error-text">{errorMsg}</p>}
      <div className="admin-panel">
        <div className="admin-panel-toolbar admin-panel-toolbar-end">
          {can(PERM.CATEGORY_CREATE) && (
            <button onClick={() => openCreate(null)} className="admin-btn-primary">
              + Thêm danh mục mới
            </button>
          )}
        </div>

        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th style={{ width: '80px' }}>Mã DM</th>
                <th>Tên danh mục</th>
                <th>Slug</th>
                <th>Mô tả sinh thái</th>
                <th style={{ textAlign: 'center', width: '240px' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {visibleRows.map((c) => {
                const hasChildren = (c.children || []).length > 0;
                return (
                  <tr key={c.id}>
                    <td className="admin-cell-strong">#{c.id}</td>
                    <td className="admin-cell-strong" style={{ color: '#1b3a1f', paddingLeft: `${12 + c.depth * 22}px` }}>
                      {hasChildren ? (
                        <button
                          type="button"
                          onClick={() => toggle(c.id)}
                          aria-expanded={!collapsed.has(c.id)}
                          aria-label={collapsed.has(c.id) ? 'Mở rộng' : 'Thu gọn'}
                          style={{ border: 'none', background: 'none', cursor: 'pointer', width: '22px' }}
                        >
                          {collapsed.has(c.id) ? '▸' : '▾'}
                        </button>
                      ) : (
                        <span style={{ display: 'inline-block', width: '22px' }} />
                      )}
                      {c.name}
                    </td>
                    <td className="admin-cell-muted">{c.slug}</td>
                    <td className="admin-cell-muted">{c.description}</td>
                    <td style={{ textAlign: 'center' }}>
                      {can(PERM.CATEGORY_CREATE) && (
                        <button onClick={() => openCreate(c.id)} className="admin-btn-action-edit">+ Con</button>
                      )}
                      {can(PERM.CATEGORY_UPDATE) && (
                        <button onClick={() => openEdit(c)} className="admin-btn-action-edit">Sửa</button>
                      )}
                      {can(PERM.CATEGORY_DELETE) && (
                        <button onClick={() => handleDeleteCategory(c)} className="admin-btn-action-delete">Xóa</button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          {tree.length === 0 && (
            <div className="admin-empty">Chưa có danh mục nào. Hãy bấm thêm danh mục mới ở trên.</div>
          )}
        </div>
      </div>

      {isCategoryModalOpen && (
        <CategoryFormModal
          currentCategory={currentCategory}
          setCurrentCategory={setCurrentCategory}
          isEditCategory={isEditCategory}
          parentOptions={parentOptions}
          error={formError}
          saving={saving}
          onSubmit={handleCategorySubmit}
          onClose={() => setIsCategoryModalOpen(false)}
        />
      )}
    </>
  );
};

export default CategoriesTab;

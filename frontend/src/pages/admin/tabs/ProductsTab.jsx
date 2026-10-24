import React, { useEffect, useState } from 'react';
import {
  getAdminProducts, createProduct, updateProduct, deleteProduct, publishProduct,
  uploadProductImage, deleteProductImage,
} from '../../../services/productApi';
import { getCategoryTree } from '../../../services/categoryApi';
import { resolveProductImage } from '../../../utils/imageResolver';
import { flattenTree, categoryOptionLabel } from '../../../utils/categoryTree';
import { usePermissions } from '../../../hooks/usePermission';
import { PERM } from '../../../utils/permissions';
import ProductFormModal from './ProductFormModal';

const emptyProduct = {
  name: '', price: '', comparePrice: '', sku: '', brand: '', image: '', description: '', stockQuantity: 0, categoryId: '', status: 'DRAFT',
};

// B02: the product tab is paged by the server (admin endpoint).
const PRODUCT_PAGE_SIZE = 20;
const STATUS_LABELS = {
  ACTIVE: 'Đang bán', DRAFT: 'Bản nháp', OUT_OF_STOCK: 'Hết hàng', INACTIVE: 'Ngừng bán', ARCHIVED: 'Lưu trữ',
};

// The server's message plus its per-field messages (validation errors carry `fields`), as one Vietnamese sentence.
const describeError = (err, fallback) => {
  const fields = err?.fields ? Object.values(err.fields).filter(Boolean).join(' ') : '';
  return [err?.friendlyMessage, fields].filter(Boolean).join(' ') || fallback;
};

/** "Sản phẩm" tab. `initialStockFilter` comes from the dashboard's low-stock card. */
const ProductsTab = ({ initialStockFilter = 'ALL' }) => {
  const { can } = usePermissions();
  const [errorMsg, setErrorMsg] = useState('');
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]); // flat list of the tree, each with `depth`
  const [searchTerm, setSearchTerm] = useState('');
  const [productCategoryFilter, setProductCategoryFilter] = useState('ALL');
  const [productStockFilter, setProductStockFilter] = useState(initialStockFilter);
  const [productStatusFilter, setProductStatusFilter] = useState('ALL');
  const [productPage, setProductPage] = useState(0);
  const [productMeta, setProductMeta] = useState(null);
  const [productsLoading, setProductsLoading] = useState(false);
  const [productReload, setProductReload] = useState(0);

  const [isProductModalOpen, setIsProductModalOpen] = useState(false);
  const [currentProduct, setCurrentProduct] = useState(emptyProduct);
  const [isEditProduct, setIsEditProduct] = useState(false);
  // Status of the product as it was when the edit form opened. The save sends a field only if the admin changed it, so
  // a stale value never overwrites what changed on the server meanwhile (stock is no longer edited here: B03).
  const [loadedStatus, setLoadedStatus] = useState(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [imageError, setImageError] = useState('');
  // New image files waiting to be uploaded, in the order they will be uploaded (= their sortOrder on the server).
  const [queue, setQueue] = useState([]);

  useEffect(() => {
    getCategoryTree()
      .then((tree) => setCategories(flattenTree(tree)))
      .catch((e) => setErrorMsg(e.friendlyMessage || 'Không thể tải dữ liệu.'));
  }, []);

  // Search / category / status are applied by the server; a new request cancels the previous one.
  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      setProductsLoading(true);
      try {
        const result = await getAdminProducts({
          keyword: searchTerm.trim() || undefined,
          categoryId: productCategoryFilter === 'ALL' ? undefined : productCategoryFilter,
          status: productStatusFilter === 'ALL' ? undefined : productStatusFilter,
          page: productPage,
          size: PRODUCT_PAGE_SIZE,
        }, { signal: controller.signal });
        if (controller.signal.aborted) return;
        setProducts(result.items || []);
        setProductMeta(result.meta || null);
      } catch (err) {
        if (!controller.signal.aborted) setErrorMsg(err.friendlyMessage || 'Không thể tải danh sách sản phẩm.');
      } finally {
        if (!controller.signal.aborted) setProductsLoading(false);
      }
    }, 300);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [searchTerm, productCategoryFilter, productStatusFilter, productPage, productReload]);

  const refreshProducts = () => setProductReload((n) => n + 1);

  const clearQueue = () => {
    queue.forEach((item) => URL.revokeObjectURL(item.preview));
    setQueue([]);
  };

  const openCreate = () => {
    clearQueue();
    setFormError('');
    setImageError('');
    setIsEditProduct(false);
    setLoadedStatus(null);
    setCurrentProduct(emptyProduct);
    setIsProductModalOpen(true);
  };

  const openEdit = (p) => {
    clearQueue();
    setFormError('');
    setImageError('');
    setIsEditProduct(true);
    setLoadedStatus(p.status);
    setCurrentProduct({ ...p, categoryId: p.categoryId, comparePrice: p.comparePrice ?? '' });
    setIsProductModalOpen(true);
  };

  const closeModal = () => {
    clearQueue();
    setIsProductModalOpen(false);
  };

  const handleQueueChange = (next, rejected) => {
    setQueue(next);
    setImageError(rejected.length > 0 ? `Không thêm được: ${rejected.join('; ')}.` : '');
  };

  const handleDeleteSavedImage = async (img) => {
    if (!window.confirm('Xóa ảnh này khỏi sản phẩm?')) return;
    try {
      await deleteProductImage(currentProduct.id, img.id);
      setCurrentProduct((p) => ({ ...p, images: (p.images || []).filter((i) => i.id !== img.id) }));
      setImageError('');
      refreshProducts();
    } catch (e) {
      setImageError(describeError(e, 'Không thể xóa ảnh.'));
    }
  };

  const handlePublishProduct = async (id) => {
    try {
      await publishProduct(id);
      refreshProducts();
    } catch (e) {
      alert(e.friendlyMessage || 'Không thể xuất bản sản phẩm.');
    }
  };

  const handleDeleteProduct = async (id) => {
    if (!window.confirm('Bạn có chắc chắn muốn ngừng kinh doanh sản phẩm này?')) return;
    try {
      await deleteProduct(id);
      refreshProducts();
    } catch (e) {
      alert(e.friendlyMessage || 'Thao tác thất bại.');
    }
  };

  // Uploads the queued files one after another (the server gives each the next sortOrder). Returns the images saved and
  // the names that failed; a failure does not stop the rest.
  const uploadQueue = async (productId) => {
    const saved = [];
    const failed = [];
    for (const item of queue) {
      try {
        saved.push(await uploadProductImage(productId, item.file));
      } catch (e) {
        failed.push(`${item.file.name} (${e.friendlyMessage || 'lỗi'})`);
      }
    }
    return { saved, failed };
  };

  // "Lưu bản nháp" / "Lưu thay đổi" (publish = false) and "Lưu & xuất bản" (publish = true). A new product is always
  // created as DRAFT; it goes live only through the publish call, whose 400 message lists what is still missing.
  const handleSave = async ({ publish }) => {
    const p = currentProduct;
    if (p.comparePrice !== '' && p.comparePrice != null && Number(p.comparePrice) < Number(p.price)) {
      setFormError('Giá so sánh phải không nhỏ hơn giá bán.');
      return;
    }
    setSaving(true);
    setFormError('');
    let productId = isEditProduct ? p.id : null;
    try {
      const hasCompare = p.comparePrice !== '' && p.comparePrice != null;
      const payload = {
        name: p.name,
        price: Number(p.price),
        image: p.image,
        description: p.description,
        categoryId: Number(p.categoryId),
        sku: p.sku || '',
        brand: p.brand || '',
        ...(hasCompare ? { comparePrice: Number(p.comparePrice) } : {}),
      };
      if (isEditProduct) {
        // Only a changed status is sent (the server re-runs the publish checks whenever status = ACTIVE is sent).
        if (p.status !== loadedStatus) payload.status = p.status;
        // PATCH: an emptied compare-price field must be cleared explicitly. Stock is changed in the Kho hàng tab.
        await updateProduct(p.id, hasCompare ? payload : { ...payload, clearComparePrice: true });
      } else {
        const created = await createProduct({ ...payload, stockQuantity: Number(p.stockQuantity), status: 'DRAFT' });
        productId = created.id;
        // From here on a retry must UPDATE this product, never create a second one.
        setIsEditProduct(true);
        setLoadedStatus(created.status);
        setCurrentProduct((cur) => ({ ...cur, id: created.id, slug: created.slug, status: created.status, images: created.images || [] }));
      }

      const { saved, failed } = await uploadQueue(productId);
      clearQueue();
      if (saved.length > 0) {
        setCurrentProduct((cur) => ({ ...cur, images: [...(cur.images || []), ...saved] }));
      }
      if (failed.length > 0) {
        setFormError(`Đã lưu sản phẩm nhưng không tải được ảnh: ${failed.join('; ')}. Bạn có thể thêm lại ảnh rồi lưu tiếp.`);
        refreshProducts();
        setSaving(false);
        return;
      }
      if (publish) await publishProduct(productId);
      setIsProductModalOpen(false);
      refreshProducts();
    } catch (err) {
      const saved = productId != null;
      setFormError(`${saved && publish ? 'Đã lưu nhưng chưa xuất bản được. ' : ''}${describeError(err, 'Không thể lưu thông tin sản phẩm.')}`);
      if (saved) refreshProducts();
    } finally {
      setSaving(false);
    }
  };

  // Tìm kiếm, danh mục và trạng thái do máy chủ lọc; bộ lọc tồn kho áp dụng trên trang đang hiển thị
  const filteredProducts = products.filter(p => {
    const matchStock = productStockFilter === 'ALL'
      ? true
      : productStockFilter === 'LOW_STOCK'
        ? p.stockQuantity <= 10 && p.stockQuantity > 0
        : productStockFilter === 'OUT_OF_STOCK'
          ? p.stockQuantity <= 0
          : p.stockQuantity > 10;
    return matchStock;
  });

  return (
    <>
      {errorMsg && <p className="admin-error-text">{errorMsg}</p>}
      <div className="admin-panel">
        {/* Toolbar bộ lọc chuyên nghiệp */}
        <div className="admin-panel-toolbar">
          <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', flex: 1 }}>
            <input
              type="text"
              placeholder="Tìm sản phẩm theo tên..."
              value={searchTerm}
              onChange={(e) => { setSearchTerm(e.target.value); setProductPage(0); }}
              className="admin-search-input"
            />
            <select
              value={productCategoryFilter}
              onChange={(e) => { setProductCategoryFilter(e.target.value); setProductPage(0); }}
              className="admin-filter-select"
            >
              <option value="ALL">Tất cả danh mục ({categories.length})</option>
              {categories.map(c => <option key={c.id} value={c.id}>{categoryOptionLabel(c)}</option>)}
            </select>
            <select
              value={productStockFilter}
              onChange={(e) => setProductStockFilter(e.target.value)}
              className="admin-filter-select"
            >
              <option value="ALL">Tất cả tồn kho</option>
              <option value="LOW_STOCK">⚠️ Sắp hết hàng (≤ 10)</option>
              <option value="OUT_OF_STOCK">❌ Hết hàng (0)</option>
              <option value="IN_STOCK">✅ Còn nhiều (&gt; 10)</option>
            </select>
            <select
              value={productStatusFilter}
              onChange={(e) => { setProductStatusFilter(e.target.value); setProductPage(0); }}
              className="admin-filter-select"
            >
              <option value="ALL">Tất cả trạng thái</option>
              <option value="ACTIVE">Đang kinh doanh</option>
              <option value="DRAFT">Bản nháp</option>
              <option value="OUT_OF_STOCK">Hết hàng (trạng thái)</option>
              <option value="INACTIVE">Ngừng bán</option>
              <option value="ARCHIVED">Lưu trữ</option>
            </select>
          </div>

          {can(PERM.PRODUCT_CREATE) && (
            <button onClick={openCreate} className="admin-btn-primary">
              + Thêm sản phẩm mới
            </button>
          )}
        </div>

        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Hình ảnh</th>
                <th>Tên sản phẩm</th>
                <th>Danh mục</th>
                <th>Giá bán</th>
                <th>Tồn kho</th>
                <th>Trạng thái</th>
                <th style={{ textAlign: 'center' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {filteredProducts.map((p) => (
                <tr key={p.id}>
                  <td>
                    <img
                      src={resolveProductImage(p.image)}
                      alt=""
                      className="admin-thumb"
                    />
                  </td>
                  <td className="admin-cell-strong">
                    {p.name}
                    {(p.sku || p.brand) && (
                      <div className="admin-cell-muted" style={{ fontWeight: 400, fontSize: '0.8em' }}>
                        {[p.brand, p.sku].filter(Boolean).join(' · ')}
                      </div>
                    )}
                  </td>
                  <td className="admin-cell-muted">{p.categoryName}</td>
                  <td className="admin-cell-price">
                    {Number(p.price).toLocaleString('vi-VN')} ₫
                    {p.comparePrice != null && Number(p.comparePrice) > Number(p.price) && (
                      <div style={{ textDecoration: 'line-through', color: '#888', fontSize: '0.8em' }}>
                        {Number(p.comparePrice).toLocaleString('vi-VN')} ₫
                      </div>
                    )}
                  </td>
                  <td>
                    <span className={`admin-badge-stock ${p.stockQuantity <= 0 ? 'stock-empty' : p.stockQuantity <= 10 ? 'stock-low' : 'stock-ok'}`}>
                      {p.stockQuantity}
                    </span>
                  </td>
                  <td>
                    <span className={`admin-badge-status ${p.status === 'ACTIVE' ? 'status-active' : 'status-inactive'}`}>
                      {STATUS_LABELS[p.status] || p.status}
                    </span>
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    {can(PERM.PRODUCT_UPDATE) && (
                      <button onClick={() => openEdit(p)} className="admin-btn-action-edit">
                        Sửa
                      </button>
                    )}
                    {(p.status === 'DRAFT' || p.status === 'INACTIVE') && can(PERM.PRODUCT_PUBLISH) && (
                      <button onClick={() => handlePublishProduct(p.id)} className="admin-btn-action-edit">
                        {p.status === 'DRAFT' ? 'Xuất bản' : 'Mở bán lại'}
                      </button>
                    )}
                    {p.status !== 'INACTIVE' && can(PERM.PRODUCT_DELETE) && (
                      <button onClick={() => handleDeleteProduct(p.id)} className="admin-btn-action-delete">
                        Ngừng bán
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {filteredProducts.length === 0 && (
            <div className="admin-empty">
              {productsLoading ? 'Đang tải sản phẩm...' : 'Không tìm thấy sản phẩm nào phù hợp với bộ lọc.'}
            </div>
          )}
          {productMeta && productMeta.totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '12px', padding: '12px' }}>
              <button
                className="admin-btn-secondary"
                disabled={productPage <= 0}
                onClick={() => setProductPage((n) => Math.max(0, n - 1))}
              >
                ← Trước
              </button>
              <span>Trang {productMeta.page + 1} / {productMeta.totalPages} ({productMeta.totalElements} sản phẩm)</span>
              <button
                className="admin-btn-secondary"
                disabled={productPage + 1 >= productMeta.totalPages}
                onClick={() => setProductPage((n) => n + 1)}
              >
                Sau →
              </button>
            </div>
          )}
        </div>
      </div>
      {isProductModalOpen && (
        <ProductFormModal
          currentProduct={currentProduct}
          setCurrentProduct={setCurrentProduct}
          isEditProduct={isEditProduct}
          categories={categories}
          saving={saving}
          formError={formError}
          queue={queue}
          onQueueChange={handleQueueChange}
          onDeleteSavedImage={handleDeleteSavedImage}
          imageError={imageError}
          onSave={handleSave}
          onClose={closeModal}
        />
      )}
    </>
  );
};

export default ProductsTab;

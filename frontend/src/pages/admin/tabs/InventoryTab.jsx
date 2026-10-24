import React, { useEffect, useState } from 'react';
import { listInventory, adjustInventory, getInventoryTransactions } from '../../../services/inventoryApi';
import { usePermissions } from '../../../hooks/usePermission';
import { PERM } from '../../../utils/permissions';
import AdminPagination from '../AdminPagination';

const PAGE_SIZE = 20;
const DEFAULT_THRESHOLD = 10;
const MAX_REASON = 255;

const CHANGE_TYPE_LABELS = {
  RESERVE: 'Giữ hàng (đặt đơn)',
  RELEASE: 'Nhả hàng (hủy / hết hạn)',
  COMMIT: 'Xuất kho (giao hàng)',
  RESTOCK: 'Nhập kho',
  ADJUST: 'Điều chỉnh thủ công',
};

const formatDate = (value) => (value ? new Date(value).toLocaleString('vi-VN') : '—');

/** Dialog with the stock ledger of one product (newest first, paged by the server). */
const HistoryModal = ({ row, onClose }) => {
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    getInventoryTransactions(row.productId, { page, size: PAGE_SIZE })
      .then((r) => { if (!cancelled) { setResult(r); setError(''); } })
      .catch((e) => { if (!cancelled) setError(e.friendlyMessage || 'Không thể tải lịch sử kho.'); });
    return () => { cancelled = true; };
  }, [row.productId, page]);

  return (
    <div className="admin-modal-overlay">
      <div className="admin-modal-card" style={{ maxWidth: '900px' }}>
        <h3 style={{ color: '#1b3a1f', marginTop: 0 }}>📒 Lịch sử kho: {row.productName}</h3>
        {error && <p className="admin-error-text">{error}</p>}
        {!result && !error && <div className="admin-empty">Đang tải...</div>}
        {result && (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Thời gian</th>
                  <th>Loại</th>
                  <th>Số lượng</th>
                  <th>Tồn thực sau</th>
                  <th>Đang giữ sau</th>
                  <th>Tham chiếu</th>
                  <th>Lý do</th>
                  <th>Người thực hiện</th>
                </tr>
              </thead>
              <tbody>
                {result.items.map((t) => (
                  <tr key={t.id}>
                    <td className="admin-cell-muted">{formatDate(t.createdAt)}</td>
                    <td>{CHANGE_TYPE_LABELS[t.changeType] || t.changeType}</td>
                    <td className="admin-cell-strong">{t.quantity > 0 ? `+${t.quantity}` : t.quantity}</td>
                    <td>{t.onHandAfter}</td>
                    <td>{t.reservedAfter}</td>
                    <td className="admin-cell-muted">{t.referenceType ? `${t.referenceType} #${t.referenceId ?? ''}` : '—'}</td>
                    <td>{t.reason || '—'}</td>
                    <td className="admin-cell-muted">{t.actorUserId ? `#${t.actorUserId}` : 'Hệ thống'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {result.items.length === 0 && <div className="admin-empty">Chưa có giao dịch kho nào.</div>}
            <AdminPagination meta={result.meta} page={page} onPage={setPage} noun="giao dịch" />
          </div>
        )}
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '12px' }}>
          <button type="button" onClick={onClose} className="admin-btn-secondary">Đóng</button>
        </div>
      </div>
    </div>
  );
};

/** Dialog that adjusts the on-hand quantity of one product. The reason is mandatory (it is stored in the ledger). */
const AdjustModal = ({ row, onClose, onSaved }) => {
  const [delta, setDelta] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const amount = Number(delta);
    // UX checks only: the server validates again (non-zero integer, reason required, stock cannot go below reserved).
    if (!Number.isInteger(amount) || amount === 0) {
      setError('Số lượng điều chỉnh phải là số nguyên khác 0 (số âm để giảm kho).');
      return;
    }
    if (reason.trim() === '') {
      setError('Vui lòng nhập lý do điều chỉnh.');
      return;
    }
    setSaving(true);
    setError('');
    try {
      await adjustInventory(row.productId, { quantityDelta: amount, reason: reason.trim() });
      onSaved();
    } catch (err) {
      setError(err.friendlyMessage || 'Không thể điều chỉnh tồn kho.');
      setSaving(false);
    }
  };

  const preview = Number.isInteger(Number(delta)) && delta !== '' ? row.quantityOnHand + Number(delta) : null;

  return (
    <div className="admin-modal-overlay">
      <div className="admin-modal-card" style={{ maxWidth: '460px' }}>
        <h3 style={{ color: '#1b3a1f', marginTop: 0 }}>⚖️ Điều chỉnh kho: {row.productName}</h3>
        <p className="admin-cell-muted" style={{ marginTop: 0 }}>
          Tồn thực: <strong>{row.quantityOnHand}</strong> · Đang giữ: <strong>{row.reservedQuantity}</strong> · Có thể bán: <strong>{row.available}</strong>
        </p>
        <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
          <div>
            <label className="admin-form-label">Số lượng thay đổi * (âm = giảm, dương = tăng)</label>
            <input
              type="number"
              step="1"
              value={delta}
              onChange={(e) => setDelta(e.target.value)}
              placeholder="Ví dụ: 20 hoặc -3"
              required
              className="admin-form-input"
            />
            {preview !== null && <small className="admin-cell-muted">Tồn thực sau điều chỉnh: {preview}</small>}
          </div>
          <div>
            <label className="admin-form-label">Lý do * (lưu vào lịch sử kho)</label>
            <textarea
              rows={3}
              maxLength={MAX_REASON}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Ví dụ: Nhập thêm lô hàng mới / Kiểm kê phát hiện thiếu 3 sản phẩm"
              required
              className="admin-form-textarea"
            />
          </div>
          {error && <p className="admin-error-text">{error}</p>}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
            <button type="button" onClick={onClose} className="admin-btn-secondary">Hủy</button>
            <button type="submit" disabled={saving} className="admin-btn-primary">{saving ? 'Đang lưu...' : 'Lưu điều chỉnh'}</button>
          </div>
        </form>
      </div>
    </div>
  );
};

/** "Kho hàng" tab (B03). `initialLowStock` comes from the dashboard's low-stock card. */
const InventoryTab = ({ initialLowStock = false }) => {
  const { can } = usePermissions();
  const [rows, setRows] = useState([]);
  const [meta, setMeta] = useState(null);
  const [page, setPage] = useState(0);
  const [keyword, setKeyword] = useState('');
  const [lowStock, setLowStock] = useState(initialLowStock);
  const [threshold, setThreshold] = useState(DEFAULT_THRESHOLD);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [adjusting, setAdjusting] = useState(null);
  const [history, setHistory] = useState(null);

  // Search and the low-stock filter are applied by the server; a new request cancels the previous one.
  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const result = await listInventory({
          keyword: keyword.trim() || undefined,
          lowStock,
          threshold: Number.isInteger(Number(threshold)) && threshold !== '' ? Number(threshold) : undefined,
          page,
          size: PAGE_SIZE,
        }, { signal: controller.signal });
        if (controller.signal.aborted) return;
        setRows(result.items || []);
        setMeta(result.meta || null);
        setError('');
      } catch (e) {
        if (!controller.signal.aborted) setError(e.friendlyMessage || 'Không thể tải tồn kho.');
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }, 300);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [keyword, lowStock, threshold, page, reloadKey]);

  return (
    <div className="admin-panel">
      <div className="admin-panel-toolbar">
        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', alignItems: 'center', flex: 1 }}>
          <input
            type="text"
            placeholder="Tìm theo tên hoặc SKU..."
            value={keyword}
            maxLength={100}
            onChange={(e) => { setKeyword(e.target.value); setPage(0); }}
            className="admin-search-input"
          />
          <label style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <input type="checkbox" checked={lowStock} onChange={(e) => { setLowStock(e.target.checked); setPage(0); }} />
            ⚠️ Chỉ hiện sắp hết hàng (có thể bán ≤
          </label>
          <input
            type="number"
            min="0"
            max="1000000"
            value={threshold}
            disabled={!lowStock}
            onChange={(e) => { setThreshold(e.target.value); setPage(0); }}
            className="admin-filter-select"
            style={{ width: '90px' }}
          />
          <span>)</span>
        </div>
      </div>

      {error && <p className="admin-error-text">{error}</p>}

      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Sản phẩm</th>
              <th>SKU</th>
              <th>Tồn thực</th>
              <th>Đang giữ</th>
              <th>Có thể bán</th>
              <th>Cập nhật</th>
              <th style={{ textAlign: 'center' }}>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.productId}>
                <td className="admin-cell-strong">{r.productName}</td>
                <td className="admin-cell-muted">{r.sku || '—'}</td>
                <td>{r.quantityOnHand}</td>
                <td>{r.reservedQuantity}</td>
                <td>
                  <span className={`admin-badge-stock ${r.available <= 0 ? 'stock-empty' : r.available <= DEFAULT_THRESHOLD ? 'stock-low' : 'stock-ok'}`}>
                    {r.available}
                  </span>
                </td>
                <td className="admin-cell-muted">{formatDate(r.updatedAt)}</td>
                <td style={{ textAlign: 'center' }}>
                  <button type="button" onClick={() => setHistory(r)} className="admin-btn-action-edit">Lịch sử</button>
                  {can(PERM.INVENTORY_ADJUST) && (
                    <button type="button" onClick={() => setAdjusting(r)} className="admin-btn-action-edit">Điều chỉnh</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && (
          <div className="admin-empty">{loading ? 'Đang tải tồn kho...' : 'Không có sản phẩm nào phù hợp với bộ lọc.'}</div>
        )}
        <AdminPagination meta={meta} page={page} onPage={setPage} noun="sản phẩm" />
      </div>

      {adjusting && (
        <AdjustModal
          row={adjusting}
          onClose={() => setAdjusting(null)}
          onSaved={() => { setAdjusting(null); setReloadKey((n) => n + 1); }}
        />
      )}
      {history && <HistoryModal row={history} onClose={() => setHistory(null)} />}
    </div>
  );
};

export default InventoryTab;

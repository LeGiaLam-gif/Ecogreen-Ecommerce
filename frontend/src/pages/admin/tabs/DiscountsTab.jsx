import React, { useEffect, useState } from 'react';
import { listDiscounts, createDiscount, updateDiscount, deleteDiscount } from '../../../services/discountApi';
import { usePermissions } from '../../../hooks/usePermission';
import { PERM } from '../../../utils/permissions';
import AdminPagination from '../AdminPagination';

const PAGE_SIZE = 20;

const emptyForm = {
  id: null, code: '', kind: 'PERCENT', value: '', minOrderAmount: '', validFrom: '', validTo: '', active: true,
};

const formatVnd = (v) => `${Number(v).toLocaleString('vi-VN')} ₫`;
const formatDate = (v) => (v ? new Date(v).toLocaleString('vi-VN') : '—');
// <input type="datetime-local"> wants "YYYY-MM-DDTHH:mm"; the API sends a LocalDateTime without a zone.
const toInputValue = (v) => (v ? String(v).slice(0, 16) : '');

const discountText = (d) => (d.percentOff != null ? `${d.percentOff}%` : formatVnd(d.amountOff));

const fromDiscount = (d) => ({
  id: d.id,
  code: d.code,
  kind: d.percentOff != null ? 'PERCENT' : 'AMOUNT',
  value: d.percentOff != null ? String(d.percentOff) : String(d.amountOff ?? ''),
  minOrderAmount: d.minOrderAmount != null ? String(d.minOrderAmount) : '',
  validFrom: toInputValue(d.validFrom),
  validTo: toInputValue(d.validTo),
  active: d.active,
});

// Exactly one of percentOff / amountOff is sent, as the API requires; empty optional fields become null.
const toPayload = (f) => ({
  code: f.code.trim(),
  percentOff: f.kind === 'PERCENT' ? Number(f.value) : null,
  amountOff: f.kind === 'AMOUNT' ? Number(f.value) : null,
  minOrderAmount: f.minOrderAmount === '' ? null : Number(f.minOrderAmount),
  validFrom: f.validFrom || null,
  validTo: f.validTo || null,
  active: f.active,
});

const DiscountFormModal = ({ form, setForm, isEdit, error, saving, onSubmit, onClose }) => (
  <div className="admin-modal-overlay">
    <div className="admin-modal-card" style={{ maxWidth: '520px' }}>
      <h3 style={{ color: '#1b3a1f', marginTop: 0 }}>{isEdit ? '✏️ Sửa mã giảm giá' : '➕ Thêm mã giảm giá'}</h3>
      <form onSubmit={onSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
        <div>
          <label className="admin-form-label">Mã giảm giá * (chữ, số, gạch ngang hoặc gạch dưới, tối đa 30 ký tự)</label>
          <input
            type="text"
            maxLength={30}
            pattern="[A-Za-z0-9_\-]+"
            value={form.code}
            onChange={(e) => setForm({ ...form, code: e.target.value })}
            required
            className="admin-form-input"
          />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
          <div>
            <label className="admin-form-label">Hình thức giảm *</label>
            <select
              value={form.kind}
              onChange={(e) => setForm({ ...form, kind: e.target.value, value: '' })}
              className="admin-form-select"
            >
              <option value="PERCENT">Giảm theo phần trăm (%)</option>
              <option value="AMOUNT">Giảm số tiền cố định (₫)</option>
            </select>
          </div>
          <div>
            <label className="admin-form-label">{form.kind === 'PERCENT' ? 'Phần trăm giảm * (1–100)' : 'Số tiền giảm * (₫)'}</label>
            <input
              type="number"
              min={form.kind === 'PERCENT' ? 1 : 1}
              max={form.kind === 'PERCENT' ? 100 : undefined}
              step={form.kind === 'PERCENT' ? 1 : 1000}
              value={form.value}
              onChange={(e) => setForm({ ...form, value: e.target.value })}
              required
              className="admin-form-input"
            />
          </div>
        </div>
        <div>
          <label className="admin-form-label">Giá trị đơn tối thiểu (₫, để trống nếu không yêu cầu)</label>
          <input
            type="number"
            min="0"
            step="1000"
            value={form.minOrderAmount}
            onChange={(e) => setForm({ ...form, minOrderAmount: e.target.value })}
            className="admin-form-input"
          />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
          <div>
            <label className="admin-form-label">Có hiệu lực từ</label>
            <input
              type="datetime-local"
              value={form.validFrom}
              onChange={(e) => setForm({ ...form, validFrom: e.target.value })}
              className="admin-form-input"
            />
          </div>
          <div>
            <label className="admin-form-label">Có hiệu lực đến</label>
            <input
              type="datetime-local"
              value={form.validTo}
              onChange={(e) => setForm({ ...form, validTo: e.target.value })}
              className="admin-form-input"
            />
          </div>
        </div>
        <label style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          Đang áp dụng (khách có thể dùng mã này)
        </label>
        {error && <p className="admin-error-text">{error}</p>}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px' }}>
          <button type="button" onClick={onClose} className="admin-btn-secondary">Hủy</button>
          <button type="submit" disabled={saving} className="admin-btn-primary">{saving ? 'Đang lưu...' : 'Lưu mã giảm giá'}</button>
        </div>
      </form>
    </div>
  </div>
);

/** "Mã giảm giá" tab (B04). Reading needs discount:view; create / edit / delete need discount:manage. */
const DiscountsTab = () => {
  const { can } = usePermissions();
  const canManage = can(PERM.DISCOUNT_MANAGE);
  const [items, setItems] = useState([]);
  const [meta, setMeta] = useState(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);
  const [form, setForm] = useState(null); // null = dialog closed
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const result = await listDiscounts({ page, size: PAGE_SIZE });
        if (cancelled) return;
        setItems(result.items || []);
        setMeta(result.meta || null);
        setError('');
      } catch (e) {
        if (!cancelled) setError(e.friendlyMessage || 'Không thể tải danh sách mã giảm giá.');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [page, reloadKey]);

  const reload = () => setReloadKey((n) => n + 1);

  const openForm = (next) => {
    setFormError('');
    setSaving(false);
    setForm(next);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    // UX check only: the server validates the combination again and answers 400 with a Vietnamese message.
    if (form.validFrom && form.validTo && form.validFrom > form.validTo) {
      setFormError('Thời điểm bắt đầu phải trước thời điểm kết thúc.');
      return;
    }
    setSaving(true);
    setFormError('');
    try {
      if (form.id) await updateDiscount(form.id, toPayload(form));
      else await createDiscount(toPayload(form));
      setForm(null);
      reload();
    } catch (err) {
      const fieldMessages = err.fields ? Object.values(err.fields).join(' ') : '';
      setFormError([err.friendlyMessage, fieldMessages].filter(Boolean).join(' ') || 'Không thể lưu mã giảm giá.');
      setSaving(false);
    }
  };

  const handleDelete = async (d) => {
    if (!window.confirm(`Xóa mã giảm giá "${d.code}"? Các đơn hàng đã dùng mã này không bị ảnh hưởng.`)) return;
    try {
      await deleteDiscount(d.id);
      reload();
    } catch (e) {
      alert(e.friendlyMessage || 'Không thể xóa mã giảm giá.');
    }
  };

  return (
    <div className="admin-panel">
      {canManage && (
        <div className="admin-panel-toolbar admin-panel-toolbar-end">
          <button type="button" onClick={() => openForm(emptyForm)} className="admin-btn-primary">+ Thêm mã giảm giá</button>
        </div>
      )}
      {error && <p className="admin-error-text">{error}</p>}
      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Mức giảm</th>
              <th>Đơn tối thiểu</th>
              <th>Hiệu lực</th>
              <th>Trạng thái</th>
              {canManage && <th style={{ textAlign: 'center' }}>Thao tác</th>}
            </tr>
          </thead>
          <tbody>
            {items.map((d) => (
              <tr key={d.id} style={d.active ? undefined : { opacity: 0.6 }}>
                <td className="admin-cell-strong">{d.code}</td>
                <td className="admin-cell-price">{discountText(d)}</td>
                <td>{d.minOrderAmount != null && Number(d.minOrderAmount) > 0 ? formatVnd(d.minOrderAmount) : '—'}</td>
                <td className="admin-cell-muted">
                  {d.validFrom || d.validTo ? `${formatDate(d.validFrom)} → ${formatDate(d.validTo)}` : 'Không giới hạn'}
                </td>
                <td>
                  <span className={`admin-badge-status ${d.active ? 'status-active' : 'status-inactive'}`}>
                    {d.active ? 'Đang áp dụng' : 'Tạm tắt'}
                  </span>
                </td>
                {canManage && (
                  <td style={{ textAlign: 'center' }}>
                    <button type="button" onClick={() => openForm(fromDiscount(d))} className="admin-btn-action-edit">Sửa</button>
                    <button type="button" onClick={() => handleDelete(d)} className="admin-btn-action-delete">Xóa</button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
        {items.length === 0 && (
          <div className="admin-empty">{loading ? 'Đang tải...' : 'Chưa có mã giảm giá nào.'}</div>
        )}
        <AdminPagination meta={meta} page={page} onPage={setPage} noun="mã" />
      </div>

      {form && (
        <DiscountFormModal
          form={form}
          setForm={setForm}
          isEdit={Boolean(form.id)}
          error={formError}
          saving={saving}
          onSubmit={handleSubmit}
          onClose={() => setForm(null)}
        />
      )}
    </div>
  );
};

export default DiscountsTab;

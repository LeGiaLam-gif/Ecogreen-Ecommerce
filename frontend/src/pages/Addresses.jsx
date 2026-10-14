import React, { useCallback, useEffect, useState } from 'react';
import {
  getAddresses, createAddress, updateAddress, setDefaultAddress, deleteAddress,
} from '../services/addressApi';
import ErrorMessage from '../components/ErrorMessage';
import './Addresses.css';

// Must match AddressService.MAX_ADDRESSES on the server (the server is the authority; this only avoids a pointless click).
const MAX_ADDRESSES = 10;
// UX-only mirror of the server's lenient Vietnamese phone pattern.
const PHONE_PATTERN = /^(\+?84|0)[ .-]?[0-9]([ .-]?[0-9]){7,9}$/;

const emptyForm = { recipientName: '', phone: '', line1: '', ward: '', district: '', province: '', isDefault: false };

const FIELDS = [
  { key: 'recipientName', label: 'Tên người nhận', type: 'text', max: 100 },
  { key: 'phone', label: 'Số điện thoại', type: 'tel', max: 20 },
  { key: 'line1', label: 'Số nhà, tên đường', type: 'text', max: 255, full: true },
  { key: 'ward', label: 'Phường/Xã', type: 'text', max: 100 },
  { key: 'district', label: 'Quận/Huyện', type: 'text', max: 100 },
  { key: 'province', label: 'Tỉnh/Thành phố', type: 'text', max: 100, full: true },
];

const validate = (form) => {
  const errors = {};
  FIELDS.forEach(({ key, label }) => {
    if (!String(form[key]).trim()) errors[key] = `${label} là bắt buộc.`;
  });
  if (!errors.phone && !PHONE_PATTERN.test(form.phone.trim())) errors.phone = 'Số điện thoại không hợp lệ.';
  return errors;
};

const Addresses = () => {
  const [addresses, setAddresses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [form, setForm] = useState(null); // null = closed; otherwise the values being edited
  const [editingId, setEditingId] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      setAddresses(await getAddresses());
      setError('');
    } catch (err) {
      setError(err.friendlyMessage || 'Không thể tải sổ địa chỉ.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditingId(null);
    setFieldErrors({});
    setForm({ ...emptyForm });
  };

  const openEdit = (a) => {
    setEditingId(a.id);
    setFieldErrors({});
    setForm({
      recipientName: a.recipientName, phone: a.phone, line1: a.line1, ward: a.ward,
      district: a.district, province: a.province, isDefault: a.isDefault,
    });
  };

  const closeForm = () => {
    setForm(null);
    setEditingId(null);
    setFieldErrors({});
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errors = validate(form);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;
    setSaving(true);
    try {
      if (editingId == null) await createAddress(form);
      else await updateAddress(editingId, form);
      closeForm();
      await load();
    } catch (err) {
      // The server is the authority: show its field messages (VALIDATION_ERROR) or its message (e.g. the 10-address limit).
      if (err.fields) setFieldErrors(err.fields);
      setError(err.friendlyMessage || 'Không thể lưu địa chỉ.');
    } finally {
      setSaving(false);
    }
  };

  const run = async (action, fallback) => {
    try {
      await action();
      setError('');
      await load();
    } catch (err) {
      setError(err.friendlyMessage || fallback);
    }
  };

  const handleDelete = (a) => {
    if (!window.confirm(`Xóa địa chỉ của "${a.recipientName}"?`)) return;
    run(() => deleteAddress(a.id), 'Không thể xóa địa chỉ.');
  };

  if (loading) return <div className="pd-loading"><div className="spinner"></div></div>;

  const atLimit = addresses.length >= MAX_ADDRESSES;

  return (
    <div className="addr-page">
      <div className="addr-header">
        <div>
          <h2 className="addr-title">📍 Sổ địa chỉ</h2>
          <span className="addr-count">{addresses.length}/{MAX_ADDRESSES} địa chỉ</span>
        </div>
        {!form && (
          <button type="button" className="addr-btn" onClick={openCreate} disabled={atLimit}
            title={atLimit ? `Tối đa ${MAX_ADDRESSES} địa chỉ` : undefined}>
            + Thêm địa chỉ
          </button>
        )}
      </div>

      <ErrorMessage message={error} />

      {form && (
        <form className="addr-form" onSubmit={handleSubmit} noValidate>
          {FIELDS.map(({ key, label, type, max, full }) => (
            <div key={key} className={`addr-field${full ? ' full' : ''}`}>
              <label htmlFor={`addr-${key}`}>{label}</label>
              <input
                id={`addr-${key}`}
                type={type}
                maxLength={max}
                value={form[key]}
                onChange={(e) => setForm({ ...form, [key]: e.target.value })}
              />
              {fieldErrors[key] && <div className="err">{fieldErrors[key]}</div>}
            </div>
          ))}
          <div className="addr-field full">
            <label>
              <input
                type="checkbox"
                checked={form.isDefault}
                onChange={(e) => setForm({ ...form, isDefault: e.target.checked })}
              />{' '}
              Đặt làm địa chỉ mặc định
            </label>
          </div>
          <div className="addr-form-actions full">
            <button type="submit" className="addr-btn" disabled={saving}>
              {saving ? 'Đang lưu...' : (editingId == null ? 'Thêm địa chỉ' : 'Lưu thay đổi')}
            </button>
            <button type="button" className="addr-btn secondary" onClick={closeForm} disabled={saving}>Hủy</button>
          </div>
        </form>
      )}

      {addresses.length === 0 && !form ? (
        <div className="addr-empty">
          <div style={{ fontSize: '3rem', marginBottom: '10px' }}>🏠</div>
          <p>Bạn chưa lưu địa chỉ nào. Thêm địa chỉ để đặt hàng nhanh hơn.</p>
        </div>
      ) : (
        <div className="addr-list">
          {addresses.map((a) => (
            <div key={a.id} className={`addr-card${a.isDefault ? ' is-default' : ''}`}>
              <div className="addr-card-top">
                <div>
                  <span className="addr-name">{a.recipientName}</span>
                  <span className="addr-phone">{a.phone}</span>
                  {a.isDefault && <span className="addr-badge">Mặc định</span>}
                </div>
              </div>
              <p className="addr-line">{[a.line1, a.ward, a.district, a.province].join(', ')}</p>
              <div className="addr-actions">
                <button type="button" className="addr-link-btn" onClick={() => openEdit(a)}>Sửa</button>
                <button type="button" className="addr-link-btn" disabled={a.isDefault}
                  onClick={() => run(() => setDefaultAddress(a.id), 'Không thể đặt địa chỉ mặc định.')}>
                  Đặt làm mặc định
                </button>
                <button type="button" className="addr-link-btn danger" onClick={() => handleDelete(a)}>Xóa</button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default Addresses;

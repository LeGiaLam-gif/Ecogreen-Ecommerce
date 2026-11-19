import React, { useEffect, useState } from 'react';
import { listMyNotifications } from '../services/notificationApi';
import ErrorMessage from '../components/ErrorMessage';

const STATUS_LABELS = {
  PENDING: { label: 'Đang chờ gửi', color: '#b45309', bg: '#fef3c7' },
  SENT: { label: 'Đã gửi', color: '#15803d', bg: '#dcfce7' },
  FAILED: { label: 'Chưa gửi được', color: '#b91c1c', bg: '#fee2e2' },
};

const formatDate = (v) => (v ? new Date(v).toLocaleString('vi-VN') : '—');

const MyNotifications = () => {
  const [items, setItems] = useState([]);
  const [meta, setMeta] = useState(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    (async () => {
      setLoading(true);
      try {
        const res = await listMyNotifications({ page, size: 20 }, { signal: controller.signal });
        if (controller.signal.aborted) return;
        setItems(res.items || []);
        setMeta(res.meta || null);
        setError('');
      } catch (err) {
        if (!controller.signal.aborted) setError(err.friendlyMessage || 'Không thể tải danh sách thông báo.');
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    })();
    return () => controller.abort();
  }, [page]);

  return (
    <section style={{ maxWidth: '900px', margin: '32px auto', padding: '0 16px' }}>
      <div style={{ background: '#fff', borderRadius: '14px', padding: '24px', boxShadow: '0 6px 24px rgba(15, 23, 42, 0.06)' }}>
        <h1 style={{ marginTop: 0, color: '#1b3a1f' }}>🔔 Thông báo của tôi</h1>
        <p style={{ color: '#64748b', marginBottom: '20px' }}>
          Lịch sử thông báo về tài khoản, đơn hàng, thanh toán và đổi/trả hàng.
        </p>

        <ErrorMessage message={error} />

        {loading && <div style={{ padding: '24px', textAlign: 'center', color: '#64748b' }}>Đang tải thông báo...</div>}

        {!loading && items.length === 0 && !error && (
          <div style={{ padding: '28px', textAlign: 'center', color: '#64748b', background: '#f8fafc', borderRadius: '10px' }}>
            Bạn chưa có thông báo nào.
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {items.map((n) => {
            const st = STATUS_LABELS[n.status] || { label: n.status, color: '#334155', bg: '#f1f5f9' };
            return (
              <div key={n.id} style={{ border: '1px solid #e2e8f0', borderRadius: '10px', padding: '14px 16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', flexWrap: 'wrap', marginBottom: '6px' }}>
                  <strong style={{ color: '#0f172a' }}>{n.title}</strong>
                  <span style={{ padding: '3px 10px', borderRadius: '999px', fontSize: '0.8rem', fontWeight: 600, color: st.color, background: st.bg }}>
                    {st.label}
                  </span>
                </div>
                {n.body && <p style={{ margin: '6px 0', color: '#334155', whiteSpace: 'pre-line', fontSize: '0.94rem' }}>{n.body}</p>}
                <div style={{ fontSize: '0.8rem', color: '#64748b' }}>
                  Kênh: {n.channel} · Tạo lúc: {formatDate(n.createdAt)}
                  {n.sentAt ? ` · Gửi lúc: ${formatDate(n.sentAt)}` : ''}
                </div>
              </div>
            );
          })}
        </div>

        {meta && meta.totalPages > 1 && (
          <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '12px', marginTop: '20px' }}>
            <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>← Trước</button>
            <span>Trang {page + 1} / {meta.totalPages}</span>
            <button type="button" disabled={page + 1 >= meta.totalPages} onClick={() => setPage((p) => p + 1)}>Sau →</button>
          </div>
        )}
      </div>
    </section>
  );
};

export default MyNotifications;

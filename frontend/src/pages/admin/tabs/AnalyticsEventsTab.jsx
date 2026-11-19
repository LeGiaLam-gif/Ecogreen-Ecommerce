import React, { useEffect, useState } from 'react';
import { listAnalyticsEvents } from '../../../services/analyticsApi';
import AdminPagination from '../AdminPagination';

const PAGE_SIZE = 20;
const EVENT_TYPES = [
  'order_created',
  'payment_success',
  'order_cancelled',
  'return_created',
  'return_status_changed',
  'product_view',
  'product_search',
  'add_to_cart',
  'remove_from_cart',
  'checkout_started',
];

const formatDate = (v) => (v ? new Date(v).toLocaleString('vi-VN') : '—');

/** "Sự kiện Analytics" tab (B09): read-only list of recorded analytics events; needs analytics:view. */
const AnalyticsEventsTab = () => {
  const [items, setItems] = useState([]);
  const [meta, setMeta] = useState(null);
  const [page, setPage] = useState(0);
  const [eventType, setEventType] = useState('ALL');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    (async () => {
      setLoading(true);
      try {
        const result = await listAnalyticsEvents(
          { type: eventType === 'ALL' ? undefined : eventType, page, size: PAGE_SIZE },
          { signal: controller.signal },
        );
        if (controller.signal.aborted) return;
        setItems(result.items || []);
        setMeta(result.meta || null);
        setError('');
      } catch (e) {
        if (!controller.signal.aborted) setError(e.friendlyMessage || 'Không thể tải danh sách sự kiện analytics.');
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    })();
    return () => controller.abort();
  }, [eventType, page]);

  return (
    <div className="admin-panel">
      <div className="admin-panel-toolbar">
        <select
          value={eventType}
          onChange={(e) => { setPage(0); setEventType(e.target.value); }}
          className="admin-filter-select"
        >
          <option value="ALL">Tất cả loại sự kiện</option>
          {EVENT_TYPES.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
      </div>

      {error && <p className="admin-error-text">{error}</p>}

      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Loại sự kiện</th>
              <th>Người dùng</th>
              <th>Payload</th>
              <th>Thời gian</th>
            </tr>
          </thead>
          <tbody>
            {items.map((ev) => (
              <tr key={ev.id}>
                <td className="admin-cell-strong">#{ev.id}</td>
                <td className="admin-cell-strong">{ev.eventType}</td>
                <td>{ev.userId ? `User #${ev.userId}` : 'Khách vãng lai'}</td>
                <td className="admin-cell-muted" style={{ maxWidth: '380px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={JSON.stringify(ev.payload || {})}>
                  {ev.payload && Object.keys(ev.payload).length > 0 ? JSON.stringify(ev.payload) : '—'}
                </td>
                <td className="admin-cell-muted">{formatDate(ev.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {items.length === 0 && (
          <div className="admin-empty">{loading ? 'Đang tải...' : 'Chưa có sự kiện analytics nào trong 30 ngày qua.'}</div>
        )}
        <AdminPagination meta={meta} page={page} onPage={setPage} noun="sự kiện" />
      </div>
    </div>
  );
};

export default AnalyticsEventsTab;

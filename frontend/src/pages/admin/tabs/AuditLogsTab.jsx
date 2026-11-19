import React, { useEffect, useState } from 'react';
import { listAdminAuditLogs } from '../../../services/auditLogApi';
import AdminPagination from '../AdminPagination';

const PAGE_SIZE = 20;
const formatDate = (v) => (v ? new Date(v).toLocaleString('vi-VN') : '—');

/** "Nhật ký thao tác" tab: read-only audit trail for admin mutations and order transitions; needs audit:view. */
const AuditLogsTab = () => {
  const [items, setItems] = useState([]);
  const [meta, setMeta] = useState(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    (async () => {
      setLoading(true);
      try {
        const result = await listAdminAuditLogs({ page, size: PAGE_SIZE }, { signal: controller.signal });
        if (controller.signal.aborted) return;
        setItems(result.items || []);
        setMeta(result.meta || null);
        setError('');
      } catch (e) {
        if (!controller.signal.aborted) setError(e.friendlyMessage || 'Không thể tải nhật ký kiểm toán.');
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    })();
    return () => controller.abort();
  }, [page]);

  return (
    <div className="admin-panel">
      {error && <p className="admin-error-text">{error}</p>}
      <div className="admin-table-wrap">
        <table className="admin-table">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Người thực hiện</th>
              <th>Hành động</th>
              <th>Tài nguyên</th>
              <th>HTTP</th>
              <th>Đường dẫn</th>
              <th>Chi tiết</th>
              <th>Thời gian</th>
            </tr>
          </thead>
          <tbody>
            {items.map((log) => (
              <tr key={log.id}>
                <td className="admin-cell-strong">#{log.id}</td>
                <td>{log.actorUserId ? `User #${log.actorUserId}` : 'Hệ thống'}</td>
                <td className="admin-cell-strong">{log.action}</td>
                <td>
                  {log.resourceType}
                  {log.resourceId ? ` #${log.resourceId}` : ''}
                </td>
                <td>
                  {log.httpMethod ? `${log.httpMethod} (${log.responseStatus ?? '—'})` : '—'}
                </td>
                <td className="admin-cell-muted" style={{ maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={log.requestPath || ''}>
                  {log.requestPath || '—'}
                </td>
                <td className="admin-cell-muted" style={{ maxWidth: '240px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={log.detailsJson || ''}>
                  {log.detailsJson || '—'}
                </td>
                <td className="admin-cell-muted">{formatDate(log.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {items.length === 0 && (
          <div className="admin-empty">{loading ? 'Đang tải...' : 'Chưa có nhật ký thao tác nào.'}</div>
        )}
        <AdminPagination meta={meta} page={page} onPage={setPage} noun="bản ghi" />
      </div>
    </div>
  );
};

export default AuditLogsTab;

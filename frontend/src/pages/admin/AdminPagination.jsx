import React from 'react';

/**
 * Previous / next controls for a server-paged list. `meta` is the `{ page, totalPages, totalElements }` object the API
 * returns, `page` the zero-based page being shown and `noun` the Vietnamese word for the rows ("sản phẩm", "mã"...).
 * Renders nothing when there is a single page.
 */
const AdminPagination = ({ meta, page, onPage, noun = 'mục' }) => {
  if (!meta || meta.totalPages <= 1) return null;
  return (
    <div className="admin-pagination" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '14px', padding: '14px' }}>
      <button type="button" disabled={page <= 0} onClick={() => onPage(page - 1)}>← Trước</button>
      <span>Trang {page + 1} / {meta.totalPages} ({meta.totalElements} {noun})</span>
      <button type="button" disabled={page + 1 >= meta.totalPages} onClick={() => onPage(page + 1)}>Sau →</button>
    </div>
  );
};

export default AdminPagination;

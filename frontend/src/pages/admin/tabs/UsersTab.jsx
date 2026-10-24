import React, { useEffect, useState } from 'react';
import { useAuth } from '../../../context/AuthContext';
import { usePermissions } from '../../../hooks/usePermission';
import { PERM } from '../../../utils/permissions';
import UserDetailModal from './UserDetailModal';
import { listAdminUsers, disableUser, enableUser, deleteUser } from '../../../services/userApi';

const USER_PAGE_SIZE = 20;

/** "Khách hàng" tab (B08). `initialUserId` opens that account's detail straight away (from the dashboard). */
const UsersTab = ({ initialUserId = null }) => {
  const { user } = useAuth();
  const { can } = usePermissions();
  const canDisable = can(PERM.USER_DISABLE); // lock / unlock and delete are both user:disable on the server
  const [detailId, setDetailId] = useState(initialUserId);
  const [errorMsg, setErrorMsg] = useState('');
  const [users, setUsers] = useState([]);
  const [userMeta, setUserMeta] = useState({ page: 0, totalPages: 1, totalElements: 0 });
  const [userPage, setUserPage] = useState(0);
  const [usersLoading, setUsersLoading] = useState(false);
  const [userReload, setUserReload] = useState(0);
  const [searchTerm, setSearchTerm] = useState('');
  const [userRoleFilter, setUserRoleFilter] = useState('ALL');
  const [userActiveFilter, setUserActiveFilter] = useState('ALL');

  // Search / role / active filters and paging are applied by the server; a new request cancels the previous one.
  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      setUsersLoading(true);
      try {
        const result = await listAdminUsers({
          keyword: searchTerm.trim() || undefined,
          role: userRoleFilter === 'ALL' ? undefined : userRoleFilter,
          active: userActiveFilter === 'ALL' ? undefined : userActiveFilter,
          page: userPage,
          size: USER_PAGE_SIZE,
        }, { signal: controller.signal });
        if (controller.signal.aborted) return;
        setUsers(result.items || []);
        setUserMeta(result.meta || { page: 0, totalPages: 1, totalElements: 0 });
      } catch (err) {
        if (!controller.signal.aborted) setErrorMsg(err.friendlyMessage || 'Không thể tải danh sách người dùng.');
      } finally {
        if (!controller.signal.aborted) setUsersLoading(false);
      }
    }, 300);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [searchTerm, userRoleFilter, userActiveFilter, userPage, userReload]);

  const reloadUsers = () => setUserReload((n) => n + 1);

  const handleToggleUser = async (u) => {
    const disabling = u.active;
    const question = disabling
      ? `Khóa tài khoản "${u.username}"? Người dùng sẽ bị đăng xuất khỏi mọi thiết bị.`
      : `Mở khóa tài khoản "${u.username}"?`;
    if (!window.confirm(question)) return;
    try {
      if (disabling) await disableUser(u.id);
      else await enableUser(u.id);
      reloadUsers();
    } catch (e) {
      alert(e.friendlyMessage || 'Thao tác thất bại.');
    }
  };

  // The server refuses (409) for a user with orders / return requests, for yourself and for the last active admin;
  // its message ("hãy khóa tài khoản") is shown as is.
  const handleDeleteUser = async (id, name) => {
    if (!window.confirm(`Bạn có chắc chắn muốn xóa tài khoản "${name}"?`)) return;
    try {
      await deleteUser(id);
      reloadUsers();
    } catch (e) {
      alert(e.friendlyMessage || 'Thao tác thất bại.');
    }
  };

  return (
    <>
      {errorMsg && <p className="admin-error-text">{errorMsg}</p>}
      <div className="admin-panel">
        <div className="admin-panel-toolbar">
          <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
            <input
              type="text"
              placeholder="Tìm theo tên đăng nhập hoặc email..."
              value={searchTerm}
              onChange={(e) => { setUserPage(0); setSearchTerm(e.target.value); }}
              className="admin-search-input"
            />
            <select
              value={userRoleFilter}
              onChange={(e) => { setUserPage(0); setUserRoleFilter(e.target.value); }}
              className="admin-filter-select"
            >
              <option value="ALL">Tất cả vai trò</option>
              <option value="ADMIN">Quản trị viên (ADMIN)</option>
              <option value="MANAGER">Quản lý (MANAGER)</option>
              <option value="CUSTOMER">Khách hàng (CUSTOMER)</option>
            </select>
            <select
              value={userActiveFilter}
              onChange={(e) => { setUserPage(0); setUserActiveFilter(e.target.value); }}
              className="admin-filter-select"
            >
              <option value="ALL">Mọi trạng thái</option>
              <option value="true">Đang hoạt động</option>
              <option value="false">Đã khóa</option>
            </select>
          </div>
        </div>

        <div className="admin-table-wrap">
          <table className="admin-table">
            <thead>
              <tr>
                <th>Mã ND</th>
                <th>Tên tài khoản</th>
                <th>Email</th>
                <th>Vai trò phân quyền</th>
                <th>Trạng thái</th>
                <th style={{ textAlign: 'center' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id} style={u.active ? undefined : { opacity: 0.6 }}>
                  <td className="admin-cell-strong">#{u.id}</td>
                  <td className="admin-cell-strong">{u.username}</td>
                  <td>{u.email}</td>
                  <td>
                    {u.roles.map((r) => (
                      <span
                        key={r}
                        style={{
                          padding: '4px 8px',
                          borderRadius: '4px',
                          fontSize: '0.8rem',
                          backgroundColor: r === 'ADMIN' ? '#c62828' : (r === 'MANAGER' ? '#ef6c00' : '#1976d2'),
                          color: '#fff',
                          marginRight: '4px',
                          fontWeight: 600,
                        }}
                      >
                        {r === 'ADMIN' ? 'Quản trị viên' : (r === 'MANAGER' ? 'Quản lý' : 'Khách hàng')}
                      </span>
                    ))}
                  </td>
                  <td>
                    <span style={{ color: u.active ? '#2e7d32' : '#9ca3af', fontWeight: 600 }}>
                      {u.active ? 'Hoạt động' : 'Đã khóa'}
                    </span>
                  </td>
                  <td style={{ textAlign: 'center' }}>
                    <button onClick={() => setDetailId(u.id)} className="admin-btn-action-edit">Chi tiết</button>
                    {u.id === user?.id ? (
                      <span style={{ color: '#9ca3af', fontSize: '0.85rem' }}>Bạn</span>
                    ) : canDisable && (
                      <>
                        <button
                          onClick={() => handleToggleUser(u)}
                          className="admin-btn-action-edit"
                        >
                          {u.active ? 'Khóa' : 'Mở khóa'}
                        </button>
                        <button
                          onClick={() => handleDeleteUser(u.id, u.username)}
                          className="admin-btn-action-delete"
                        >
                          Xóa
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!usersLoading && users.length === 0 && (
            <div className="admin-empty">Không tìm thấy người dùng nào phù hợp.</div>
          )}
          {userMeta.totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '14px', padding: '14px' }}>
              <button type="button" disabled={userPage === 0} onClick={() => setUserPage((x) => x - 1)}>← Trước</button>
              <span>Trang {userPage + 1} / {userMeta.totalPages} ({userMeta.totalElements} người dùng)</span>
              <button type="button" disabled={userPage + 1 >= userMeta.totalPages} onClick={() => setUserPage((x) => x + 1)}>Sau →</button>
            </div>
          )}
        </div>
      </div>
      {detailId !== null && (
        <UserDetailModal
          userId={detailId}
          onClose={() => setDetailId(null)}
          onToggle={canDisable && detailId !== user?.id
            ? async (u) => { await handleToggleUser(u); setDetailId(null); }
            : null}
        />
      )}
    </>
  );
};

export default UsersTab;

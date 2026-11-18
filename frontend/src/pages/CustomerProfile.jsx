import React, { useEffect, useState } from 'react';
import { getCustomerProfile, updateCustomerProfile } from '../services/customerProfileApi';
import { useAuth } from '../context/AuthContext';
import ErrorMessage from '../components/ErrorMessage';
import './CustomerProfile.css';

const EMPTY_PROFILE = { username: '', email: '', phone: '' };

const CustomerProfile = () => {
  const { updateUserProfile } = useAuth();
  const [profile, setProfile] = useState(EMPTY_PROFILE);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    getCustomerProfile()
      .then(setProfile)
      .catch((err) => setError(err.friendlyMessage || 'Không thể tải hồ sơ cá nhân.'))
      .finally(() => setLoading(false));
  }, []);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setSaving(true);
    setError('');
    setSuccess('');
    try {
      const updated = await updateCustomerProfile(profile);
      setProfile(updated);
      updateUserProfile(updated);
      setSuccess('Hồ sơ của bạn đã được cập nhật.');
    } catch (err) {
      setError(err.friendlyMessage || 'Không thể lưu hồ sơ cá nhân.');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="profile-loading">Đang tải hồ sơ...</div>;

  return (
    <section className="profile-page">
      <div className="profile-card">
        <h1>Hồ sơ cá nhân</h1>
        <p>Cập nhật thông tin tài khoản của bạn.</p>
        <ErrorMessage message={error} />
        {success && <p className="profile-success" role="status">{success}</p>}
        <form onSubmit={handleSubmit}>
          <label htmlFor="profile-username">Tên đăng nhập</label>
          <input id="profile-username" required maxLength={50} value={profile.username}
            onChange={(event) => setProfile({ ...profile, username: event.target.value })} />

          <label htmlFor="profile-email">Email</label>
          <input id="profile-email" type="email" required maxLength={100} value={profile.email}
            onChange={(event) => setProfile({ ...profile, email: event.target.value })} />

          <label htmlFor="profile-phone">Số điện thoại</label>
          <input id="profile-phone" type="tel" maxLength={20} value={profile.phone || ''}
            onChange={(event) => setProfile({ ...profile, phone: event.target.value })} />

          <button type="submit" disabled={saving}>{saving ? 'Đang lưu...' : 'Lưu thay đổi'}</button>
        </form>
      </div>
    </section>
  );
};

export default CustomerProfile;

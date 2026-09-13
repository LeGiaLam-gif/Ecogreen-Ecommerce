import http from './http';

export const getAdminStats = () => http.get('/admin/stats').then((res) => res.data);

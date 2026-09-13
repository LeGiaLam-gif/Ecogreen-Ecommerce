import http from './http';

export const getAllCategories = () => http.get('/categories').then((res) => res.data);

export const createCategory = (data) => http.post('/categories', data).then((res) => res.data);

export const updateCategory = (id, data) => http.put(`/categories/${id}`, data).then((res) => res.data);

export const deleteCategory = (id) => http.delete(`/categories/${id}`);

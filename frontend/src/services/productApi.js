import http from './http';

export const getAllProducts = () => http.get('/products').then((res) => res.data);

export const getAllProductsForAdmin = () => http.get('/products/admin/all').then((res) => res.data);

export const getProductById = (id) => http.get(`/products/${id}`).then((res) => res.data);

export const createProduct = (data) => http.post('/products', data).then((res) => res.data);

export const updateProduct = (id, data) => http.put(`/products/${id}`, data).then((res) => res.data);

export const deleteProduct = (id) => http.delete(`/products/${id}`);

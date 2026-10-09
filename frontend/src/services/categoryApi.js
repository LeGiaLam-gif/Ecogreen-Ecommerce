import http from './http';

// B02: /api/v1 contract (see productApi.js). The list is flat; ask for the tree with getCategoryTree().
export const getAllCategories = () => http.get('/v1/categories').then((res) => res.data);

export const getCategoryTree = () => http.get('/v1/categories', { params: { tree: true } }).then((res) => res.data);

// Only the fields the API accepts are sent (the admin form may hold extra fields from the list response).
const toPayload = (c) => ({ name: c.name, description: c.description ?? null, parentId: c.parentId ?? null });

export const createCategory = (data) => http.post('/v1/admin/categories', toPayload(data)).then((res) => res.data);

// PATCH replaces name, description and parent together.
export const updateCategory = (id, data) =>
  http.patch(`/v1/admin/categories/${id}`, toPayload(data)).then((res) => res.data);

// 409 CONFLICT while the category still has products or child categories.
export const deleteCategory = (id) => http.delete(`/v1/admin/categories/${id}`);

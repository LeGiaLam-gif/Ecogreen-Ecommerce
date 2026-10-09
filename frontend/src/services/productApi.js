import http from './http';

// B02: every call uses the /api/v1 contract. http.js unwraps the envelope, so
//   paged lists resolve to { items, meta: { page, size, totalElements, totalPages } }
//   single resources resolve to the resource itself.
// "config" lets callers pass an AbortController signal ({ signal }) to cancel a superseded request.

// Public catalogue (ACTIVE products only). params: keyword, categoryId, minPrice, maxPrice, inStock, page, size, sort.
export const getProducts = (params = {}, config = {}) =>
  http.get('/v1/products', { ...config, params }).then((res) => res.data);

// idOrSlug: numeric id or slug.
export const getProductByIdOrSlug = (idOrSlug, config = {}) =>
  http.get(`/v1/products/${encodeURIComponent(idOrSlug)}`, config).then((res) => res.data);

// Admin (needs the matching PRODUCT_* permission). params additionally accept status.
export const getAdminProducts = (params = {}, config = {}) =>
  http.get('/v1/admin/products', { ...config, params }).then((res) => res.data);

export const createProduct = (data) => http.post('/v1/admin/products', data).then((res) => res.data);

// PATCH: only the fields present are changed.
export const updateProduct = (id, data) => http.patch(`/v1/admin/products/${id}`, data).then((res) => res.data);

export const publishProduct = (id) => http.post(`/v1/admin/products/${id}/publish`).then((res) => res.data);

// Soft delete: the product becomes INACTIVE.
export const deleteProduct = (id) => http.delete(`/v1/admin/products/${id}`);

export const uploadProductImage = (id, file, altText) => {
  const form = new FormData();
  form.append('file', file);
  if (altText) form.append('altText', altText);
  return http.post(`/v1/admin/products/${id}/images`, form).then((res) => res.data);
};

export const deleteProductImage = (productId, imageId) =>
  http.delete(`/v1/admin/products/${productId}/images/${imageId}`);

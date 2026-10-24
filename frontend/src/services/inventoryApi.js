import http from './http';

// B03 inventory, /api/v1/admin/inventory (see docs/ai/contracts/B03-inventory.md). Reading needs inventory:view,
// adjusting needs inventory:adjust. Lists resolve to { items, meta }.

// Row: { productId, productName, sku, quantityOnHand, reservedQuantity, available, updatedAt }.
// `lowStock=true` keeps available <= threshold (default 10, ignored unless lowStock). The order is fixed by the server.
export const listInventory = ({ keyword, lowStock, threshold, page = 0, size = 20 } = {}, config = {}) =>
  http.get('/v1/admin/inventory', {
    ...config,
    params: { keyword, lowStock: lowStock ? true : undefined, threshold: lowStock ? threshold : undefined, page, size },
  }).then((res) => res.data);

// The ledger of one product, newest first: [{ id, changeType, quantity, onHandAfter, reservedAfter, referenceType,
// referenceId, reason, actorUserId, createdAt }].
export const getInventoryTransactions = (productId, { page = 0, size = 20 } = {}) =>
  http.get(`/v1/admin/inventory/${productId}/transactions`, { params: { page, size } }).then((res) => res.data);

// quantityDelta: a non-zero whole number (negative removes stock); reason is required (max 255). Resolves to the new row.
export const adjustInventory = (productId, { quantityDelta, reason }) =>
  http.patch(`/v1/admin/inventory/${productId}/adjust`, { quantityDelta, reason }).then((res) => res.data);

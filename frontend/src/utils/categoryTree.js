// Helpers for the category tree returned by GET /api/v1/categories?tree=true (B02): [{ id, name, description, slug,
// parentId, children: [...] }]. Display only; the server enforces every rule (cycles, duplicate names, delete conflicts).

/** Depth-first flat list, each item gets `depth` (0 = root). Order follows the tree. */
export const flattenTree = (tree, depth = 0) =>
  (tree || []).flatMap((node) => [{ ...node, depth }, ...flattenTree(node.children, depth + 1)]);

/** Label for a <option>: indented by depth so the hierarchy is visible in a plain dropdown. */
export const categoryOptionLabel = (category) => `${'— '.repeat(category.depth || 0)}${category.name}`;

/** Ids of a node's descendants (a category cannot become the child of one of them; the server rejects it too). */
export const descendantIds = (flat, id) => {
  const byParent = new Map();
  flat.forEach((c) => {
    const list = byParent.get(c.parentId ?? null) || [];
    list.push(c);
    byParent.set(c.parentId ?? null, list);
  });
  const out = new Set();
  const walk = (parentId) => (byParent.get(parentId) || []).forEach((c) => {
    out.add(c.id);
    walk(c.id);
  });
  walk(id);
  return out;
};

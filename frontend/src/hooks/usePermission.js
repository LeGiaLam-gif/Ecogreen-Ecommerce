import { useAuth } from '../context/AuthContext';

/**
 * UX only: true when the signed-in user's token carries `code` (see utils/permissions.js). It decides what is SHOWN;
 * the backend re-checks the permission on every request, so a hidden button is never the only protection.
 */
export const usePermission = (code) => {
  const { hasPermission } = useAuth();
  return hasPermission(code);
};

/** For several checks in one component (hooks cannot be called in a loop): `const { can, canAny } = usePermissions();`. */
export const usePermissions = () => {
  const { hasPermission, hasAnyPermission, isAdmin } = useAuth();
  return { can: hasPermission, canAny: hasAnyPermission, isAdmin };
};

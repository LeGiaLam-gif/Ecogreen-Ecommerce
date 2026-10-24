import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * Route guard for UX only. The frontend hiding a route is NOT a security
 * boundary - every admin API call is independently re-checked against the
 * authenticated user's roles or permissions on the backend (AuthGuard).
 * `adminOnly`: ADMIN. `staffOnly`: ADMIN or MANAGER (the admin area, whose screens then check permissions one by one).
 */
const ProtectedRoute = ({ children, adminOnly = false, staffOnly = false }) => {
  const { user, isAdmin, isStaff } = useAuth();

  if (!user) return <Navigate to="/login" replace />;
  if (adminOnly && !isAdmin) return <Navigate to="/" replace />;
  if (staffOnly && !isStaff) return <Navigate to="/" replace />; // ADMIN or MANAGER; each screen then checks its own permission

  return children;
};

export default ProtectedRoute;

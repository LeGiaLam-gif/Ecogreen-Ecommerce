import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * Route guard for UX only. The frontend hiding a route is NOT a security
 * boundary - every admin API call is independently re-checked against the
 * authenticated user's roles on the backend (see AuthGuard.requireAdmin).
 */
const ProtectedRoute = ({ children, adminOnly = false }) => {
  const { user, isAdmin } = useAuth();

  if (!user) return <Navigate to="/login" replace />;
  if (adminOnly && !isAdmin) return <Navigate to="/" replace />;

  return children;
};

export default ProtectedRoute;

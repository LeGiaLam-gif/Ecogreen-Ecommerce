import React, { createContext, useContext, useState, useEffect } from 'react';
import * as authApi from '../services/authApi';
import {
  clearSession,
  getAccessToken,
  getRefreshToken,
  getStoredUser,
  purgeLegacySession,
  saveSession,
  saveUser
} from '../services/authStorage';

const AuthContext = createContext();

const NO_ITEMS = [];

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    purgeLegacySession(); // old UUID sessions are cleared silently => treated as logged out
    return getAccessToken() || getRefreshToken() ? getStoredUser() : null;
  });
  const [loading, setLoading] = useState(false);

  // On reload, ALWAYS re-validate a stored session against the backend (B01-P3), so stale cached roles/permissions
  // (e.g. a pre-CUSTOMER 'USER' role or changed permissions) are replaced by server truth. http.js refreshes an expired
  // access token once. If the session was cleared (refresh rejected) or there was no cached user, drop it; a transient
  // network/5xx error keeps an already-cached session.
  useEffect(() => {
    if (getAccessToken() || getRefreshToken()) {
      authApi
        .getMe()
        .then((me) => {
          setUser(me);
          saveUser(me);
        })
        .catch(() => {
          if (!user || (!getAccessToken() && !getRefreshToken())) {
            clearSession();
            setUser(null);
          }
        });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const startSession = (data) => {
    saveSession(data);
    setUser(data.user);
    return data.user;
  };

  const login = async (username, password) => {
    setLoading(true);
    try {
      return startSession(await authApi.login(username, password));
    } finally {
      setLoading(false);
    }
  };

  const loginWithGoogle = async (idToken) => {
    setLoading(true);
    try {
      return startSession(await authApi.loginWithGoogle(idToken));
    } finally {
      setLoading(false);
    }
  };

  const register = async (username, email, password) => {
    setLoading(true);
    try {
      return await authApi.register(username, email, password);
    } finally {
      setLoading(false);
    }
  };

  const logout = () => {
    const refreshToken = getRefreshToken();
    if (refreshToken) authApi.logout(refreshToken).catch(() => {});
    clearSession();
    setUser(null);
  };

  // B01-P3. Permissions/roles come from the server (login/me response); the frontend only uses them for UX - the backend
  // re-checks every request.
  const roles = user?.roles ?? NO_ITEMS;
  const permissions = user?.permissions ?? NO_ITEMS;
  const isAdmin = roles.includes('ADMIN');
  const isStaff = isAdmin || roles.includes('MANAGER');
  const hasPermission = (code) => permissions.includes(code);
  const hasAnyPermission = (...codes) => codes.some((code) => permissions.includes(code));

  return (
    <AuthContext.Provider
      value={{
        user,
        roles,
        permissions,
        isAdmin,
        isStaff,
        hasPermission,
        hasAnyPermission,
        loading,
        login,
        loginWithGoogle,
        register,
        logout
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};


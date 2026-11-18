import React, { createContext, useContext, useState, useEffect } from 'react';
import * as authApi from '../services/authApi';
import {
  clearSession,
  hasSessionHint,
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
    purgeLegacySession();
    return hasSessionHint() ? getStoredUser() : null;
  });
  const [loading, setLoading] = useState(false);

  // Re-validate cached profile/permissions against the cookie-backed session on every reload.
  useEffect(() => {
    if (hasSessionHint()) {
      authApi
        .getMe()
        .then((me) => {
          setUser(me);
          saveUser(me);
        })
        .catch((error) => {
          if (error?.response?.status === 401) {
            clearSession();
            setUser(null);
          }
        });
    }
  }, []);

  // `remember`: true -> the session survives closing the browser (localStorage); false -> this tab only (sessionStorage).
  const startSession = (profile, remember = true) => {
    saveSession({ user: profile }, remember);
    setUser(profile);
    return profile;
  };

  const login = async (username, password, remember = true) => {
    setLoading(true);
    try {
      return startSession(await authApi.login(username, password, remember), remember);
    } finally {
      setLoading(false);
    }
  };

  const loginWithGoogle = async (idToken, remember = true) => {
    setLoading(true);
    try {
      return startSession(await authApi.loginWithGoogle(idToken, remember), remember);
    } finally {
      setLoading(false);
    }
  };

  const loginWithFacebook = async (accessToken, remember = true) => {
    setLoading(true);
    try {
      return startSession(await authApi.loginWithFacebook(accessToken, remember), remember);
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
    authApi.logout().catch(() => {});
    clearSession();
    setUser(null);
  };

  const updateUserProfile = (profile) => {
    const updatedUser = { ...user, ...profile };
    saveUser(updatedUser);
    setUser(updatedUser);
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
        loginWithFacebook,
        register,
        updateUserProfile,
        logout
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};


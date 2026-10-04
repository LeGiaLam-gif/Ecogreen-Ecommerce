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

  // On reload, re-validate the stored session against the backend (http.js refreshes an expired access token once).
  useEffect(() => {
    if ((getAccessToken() || getRefreshToken()) && !user) {
      authApi
        .getMe()
        .then((me) => {
          setUser(me);
          saveUser(me);
        })
        .catch(() => clearSession());
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

  const isAdmin = !!user?.roles?.includes('ADMIN');

  return (
    <AuthContext.Provider value={{ user, isAdmin, loading, login, loginWithGoogle, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
};


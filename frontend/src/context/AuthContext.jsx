import React, { createContext, useContext, useState, useEffect } from 'react';
import * as authApi from '../services/authApi';

const AuthContext = createContext();

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('user');
    return stored ? JSON.parse(stored) : null;
  });
  const [loading, setLoading] = useState(false);

  // On refresh, re-validate the stored token against the backend so a
  // revoked/expired token doesn't leave the UI in a stale "logged in" state.
  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token && !user) {
      authApi
        .getMe()
        .then((me) => {
          setUser(me);
          localStorage.setItem('user', JSON.stringify(me));
        })
        .catch(() => {
          localStorage.removeItem('token');
          localStorage.removeItem('user');
        });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = async (username, password) => {
    setLoading(true);
    try {
      const data = await authApi.login(username, password);
      localStorage.setItem('token', data.token);
      localStorage.setItem('user', JSON.stringify(data.user));
      setUser(data.user);
      return data.user;
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
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
  };

  const isAdmin = !!user?.roles?.includes('ADMIN');

  return (
    <AuthContext.Provider value={{ user, isAdmin, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

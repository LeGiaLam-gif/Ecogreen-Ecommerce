/**
 * Browser storage for the auth session (B01-P2).
 * Keys: accessToken (JWT), refreshToken (opaque), user (profile for the UI).
 * "Duy trì đăng nhập" (remember me): ticked -> localStorage (survives closing the browser); unticked -> sessionStorage
 * (gone when the tab/browser is closed). Reads look in both; refreshed tokens are written back to whichever store holds the
 * session, so a refresh never moves a session between stores.
 * NOTE (residual risk): both stores are readable by any script running on the page (XSS). Moving the refresh token to an
 * httpOnly cookie needs CORS credentials + CSRF handling and is an open owner decision (D-5).
 */
const ACCESS = 'accessToken';
const REFRESH = 'refreshToken';
const USER = 'user';
const LEGACY_TOKEN = 'token'; // pre-JWT random UUID session

const JWT_SHAPE = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*$/;

export const isJwtShaped = (value) => typeof value === 'string' && JWT_SHAPE.test(value);

// localStorage first, then sessionStorage; either may throw (privacy mode), so each access is guarded.
const stores = () => {
  const list = [];
  try {
    list.push(localStorage);
  } catch {
    /* unavailable */
  }
  try {
    list.push(sessionStorage);
  } catch {
    /* unavailable */
  }
  return list;
};

const read = (key) => {
  for (const store of stores()) {
    try {
      const value = store.getItem(key);
      if (value !== null) return value;
    } catch {
      /* try the next store */
    }
  }
  return null;
};

// The store that currently holds a session; localStorage when there is none.
const activeStore = () => {
  for (const store of stores()) {
    try {
      if (store.getItem(REFRESH) !== null || store.getItem(ACCESS) !== null) return store;
    } catch {
      /* try the next store */
    }
  }
  return localStorage;
};

export const getAccessToken = () => read(ACCESS);
export const getRefreshToken = () => read(REFRESH);

export const getStoredUser = () => {
  try {
    const raw = read(USER);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
};

export const saveTokens = ({ accessToken, refreshToken }) => {
  const store = activeStore();
  store.setItem(ACCESS, accessToken);
  if (refreshToken) store.setItem(REFRESH, refreshToken);
};

/** Starts a session. `remember` true -> localStorage, false -> sessionStorage. Any previous session is dropped first. */
export const saveSession = ({ accessToken, refreshToken, user }, remember = true) => {
  clearSession();
  const store = remember ? localStorage : sessionStorage;
  store.setItem(ACCESS, accessToken);
  if (refreshToken) store.setItem(REFRESH, refreshToken);
  if (user) store.setItem(USER, JSON.stringify(user));
};

export const saveUser = (user) => activeStore().setItem(USER, JSON.stringify(user));

export const clearSession = () => {
  for (const store of stores()) {
    [ACCESS, REFRESH, USER, LEGACY_TOKEN].forEach((key) => {
      try {
        store.removeItem(key);
      } catch {
        /* ignore */
      }
    });
  }
};

/**
 * Silently drops a legacy (pre-JWT) session: the old UUID `token` key, or any stored access token that is not
 * JWT-shaped. The user is simply treated as logged out and must log in once.
 */
export const purgeLegacySession = () => {
  const access = read(ACCESS);
  if (read(LEGACY_TOKEN) !== null || (access !== null && !isJwtShaped(access))) {
    clearSession();
  }
};

/**
 * Browser storage for the auth session (B01-P2).
 * Keys: accessToken (JWT), refreshToken (opaque), user (profile for the UI).
 * NOTE (residual risk): localStorage is readable by any script running on the page (XSS). Moving the refresh token to an
 * httpOnly cookie needs CORS credentials + CSRF handling and is an open owner decision (D-5).
 */
const ACCESS = 'accessToken';
const REFRESH = 'refreshToken';
const USER = 'user';
const LEGACY_TOKEN = 'token'; // pre-JWT random UUID session

const JWT_SHAPE = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*$/;

export const isJwtShaped = (value) => typeof value === 'string' && JWT_SHAPE.test(value);

const read = (key) => {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
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
  localStorage.setItem(ACCESS, accessToken);
  if (refreshToken) localStorage.setItem(REFRESH, refreshToken);
};

export const saveSession = ({ accessToken, refreshToken, user }) => {
  saveTokens({ accessToken, refreshToken });
  if (user) localStorage.setItem(USER, JSON.stringify(user));
};

export const saveUser = (user) => localStorage.setItem(USER, JSON.stringify(user));

export const clearSession = () => {
  [ACCESS, REFRESH, USER, LEGACY_TOKEN].forEach((key) => {
    try {
      localStorage.removeItem(key);
    } catch {
      /* ignore */
    }
  });
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

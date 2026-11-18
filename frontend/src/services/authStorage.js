const USER = 'user';
const SESSION_HINT = 'authSessionPresent';
const REMEMBER = 'authSessionRemember';
const LEGACY_KEYS = ['accessToken', 'refreshToken', 'token'];
let sessionPresentInMemory = false;
let rememberedInMemory = true;

const stores = () => {
  const available = [];
  try {
    available.push(localStorage);
  } catch {
    /* storage unavailable */
  }
  try {
    available.push(sessionStorage);
  } catch {
    /* storage unavailable */
  }
  return available;
};

const read = (key) => {
  for (const store of stores()) {
    try {
      const value = store.getItem(key);
      if (value !== null) return value;
    } catch {
      /* try next store */
    }
  }
  return null;
};

export const getStoredUser = () => {
  try {
    const raw = read(USER);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
};

export const hasSessionHint = () => sessionPresentInMemory || read(SESSION_HINT) === 'true';
export const isRememberedSession = () => rememberedInMemory && read(REMEMBER) !== 'false';

export const saveSession = ({ user }, remember = true) => {
  clearSession();
  sessionPresentInMemory = true;
  rememberedInMemory = remember;
  try {
    const store = remember ? localStorage : sessionStorage;
    if (user) store.setItem(USER, JSON.stringify(user));
  } catch {
    /* browser storage may be disabled or full */
  }
  try {
    localStorage.setItem(SESSION_HINT, 'true');
    localStorage.setItem(REMEMBER, String(remember));
  } catch {
    /* the in-memory hint still supports this page lifetime */
  }
};

export const saveUser = (user) => {
  try {
    const target = isRememberedSession() ? localStorage : sessionStorage;
    target.setItem(USER, JSON.stringify(user));
  } catch {
    /* profile cache is optional */
  }
};

export const clearSession = () => {
  sessionPresentInMemory = false;
  rememberedInMemory = true;
  for (const store of stores()) {
    [USER, ...LEGACY_KEYS].forEach((key) => {
      try {
        store.removeItem(key);
      } catch {
        /* ignore */
      }
    });
  }
  try {
    localStorage.removeItem(SESSION_HINT);
    localStorage.removeItem(REMEMBER);
  } catch {
    /* ignore */
  }
};

// Old releases stored bearer credentials in web storage; remove them during the cookie-auth migration.
export const purgeLegacySession = () => {
  const hadLegacyCredentials = stores().some((store) =>
    LEGACY_KEYS.some((key) => {
      try {
        return store.getItem(key) !== null;
      } catch {
        return false;
      }
    })
  );
  if (hadLegacyCredentials) clearSession();
};

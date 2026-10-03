/**
 * Google Sign-In (Google Identity Services, ID-token flow).
 *
 * The browser only obtains a signed Google ID token (`response.credential`) and forwards it to the backend,
 * which verifies it. No profile/email/id is read or sent from the client side.
 * The client ID comes ONLY from the build-time env var VITE_GOOGLE_CLIENT_ID (no localStorage override).
 */

const GIS_SRC = 'https://accounts.google.com/gsi/client';
let gisPromise = null;

export const getGoogleClientId = () => (import.meta.env.VITE_GOOGLE_CLIENT_ID || '').trim();

/** Loads the GIS script on demand (idempotent). Resolves with `window.google.accounts.id`. */
export const loadGoogleIdentity = () => {
  if (window.google?.accounts?.id) return Promise.resolve(window.google.accounts.id);
  if (gisPromise) return gisPromise;

  gisPromise = new Promise((resolve, reject) => {
    const done = () => (window.google?.accounts?.id
      ? resolve(window.google.accounts.id)
      : reject(new Error('GOOGLE_SDK_NOT_LOADED')));
    const fail = () => {
      gisPromise = null;
      reject(new Error('GOOGLE_SDK_NOT_LOADED'));
    };

    let script = document.querySelector(`script[src="${GIS_SRC}"]`);
    if (!script) {
      script = document.createElement('script');
      script.src = GIS_SRC;
      script.async = true;
      script.defer = true;
      document.head.appendChild(script);
    }
    script.addEventListener('load', done, { once: true });
    script.addEventListener('error', fail, { once: true });
  });
  return gisPromise;
};

/**
 * Renders Google's official sign-in button into `container`.
 * `onIdToken(idToken)` is called with the raw ID token (JWT string) - nothing else.
 * Returns a Promise; rejects with MISSING_CLIENT_ID / GOOGLE_SDK_NOT_LOADED.
 */
export const renderGoogleSignInButton = async (container, { onIdToken, onError, text = 'signin_with' }) => {
  const clientId = getGoogleClientId();
  if (!clientId) throw new Error('MISSING_CLIENT_ID');

  const gis = await loadGoogleIdentity();
  gis.initialize({
    client_id: clientId,
    callback: (response) => {
      if (response?.credential) {
        onIdToken(response.credential);
      } else if (onError) {
        onError(new Error('GOOGLE_NO_CREDENTIAL'));
      }
    },
    ux_mode: 'popup',
    cancel_on_tap_outside: true
  });
  container.innerHTML = '';
  gis.renderButton(container, {
    type: 'standard',
    theme: 'outline',
    size: 'large',
    text,
    shape: 'rectangular',
    width: Math.max(200, Math.min(400, container.offsetWidth || 240))
  });
};

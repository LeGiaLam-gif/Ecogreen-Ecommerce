/**
 * Facebook Login (Facebook JS SDK).
 *
 * The browser only obtains a user ACCESS TOKEN and forwards it to the backend, which verifies it with Facebook's Graph API
 * (debug_token + /me). No profile/email/id is read or sent from the client side.
 * The app id comes ONLY from the build-time env var VITE_FACEBOOK_APP_ID (public value; the app secret never reaches the browser).
 * Facebook only allows the JS SDK login on https:// origins (http://localhost is allowed for development).
 */

const SDK_SRC = 'https://connect.facebook.net/vi_VN/sdk.js';
const GRAPH_VERSION = 'v21.0';
let sdkPromise = null;

export const getFacebookAppId = () => (import.meta.env.VITE_FACEBOOK_APP_ID || '').trim();

/** Loads and initialises the SDK once. Resolves with `window.FB`. Call it early (page mount) so the later click is instant. */
export const loadFacebookSdk = () => {
  if (window.FB) return Promise.resolve(window.FB);
  if (sdkPromise) return sdkPromise;
  const appId = getFacebookAppId();
  if (!appId) return Promise.reject(new Error('MISSING_APP_ID'));

  sdkPromise = new Promise((resolve, reject) => {
    const fail = () => {
      sdkPromise = null;
      reject(new Error('FACEBOOK_SDK_NOT_LOADED'));
    };
    window.fbAsyncInit = () => {
      window.FB.init({ appId, cookie: false, xfbml: false, version: GRAPH_VERSION });
      resolve(window.FB);
    };
    const script = document.createElement('script');
    script.src = SDK_SRC;
    script.async = true;
    script.defer = true;
    script.crossOrigin = 'anonymous';
    script.addEventListener('error', fail, { once: true });
    document.head.appendChild(script);
  });
  return sdkPromise;
};

/**
 * Opens the Facebook login popup and resolves with the access token.
 * Must be called straight from the click handler (no `await` before it) or the browser blocks the popup,
 * so the SDK has to be loaded already (see loadFacebookSdk). Rejects with FACEBOOK_SDK_NOT_READY / FACEBOOK_CANCELLED.
 */
export const facebookLogin = () =>
  new Promise((resolve, reject) => {
    if (!window.FB) {
      reject(new Error('FACEBOOK_SDK_NOT_READY'));
      return;
    }
    window.FB.login(
      (response) => {
        const token = response?.authResponse?.accessToken;
        if (token) resolve(token);
        else reject(new Error('FACEBOOK_CANCELLED'));
      },
      { scope: 'public_profile,email' }
    );
  });

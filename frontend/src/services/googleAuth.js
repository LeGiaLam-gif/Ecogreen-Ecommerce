/**
 * Google OAuth 2.0 Client Service
 * Uses official Google Identity Services (GIS) library
 */

const STORAGE_KEY = 'ecogreen_google_client_id';

export const getGoogleClientId = () => {
  const envId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
  if (envId && envId.trim()) return envId.trim();
  return localStorage.getItem(STORAGE_KEY) || '';
};

export const saveGoogleClientId = (clientId) => {
  if (clientId && clientId.trim()) {
    localStorage.setItem(STORAGE_KEY, clientId.trim());
  } else {
    localStorage.removeItem(STORAGE_KEY);
  }
};

/**
 * Triggers the REAL Google OAuth 2.0 popup using Google's official GIS SDK.
 */
export const triggerRealGoogleLogin = ({ onSuccess, onError }) => {
  const clientId = getGoogleClientId();

  if (!clientId) {
    if (onError) onError(new Error('MISSING_CLIENT_ID'));
    return;
  }

  if (!window.google || !window.google.accounts || !window.google.accounts.oauth2) {
    if (onError) onError(new Error('GOOGLE_SDK_NOT_LOADED'));
    return;
  }

  try {
    const tokenClient = window.google.accounts.oauth2.initTokenClient({
      client_id: clientId,
      scope: 'openid email profile',
      callback: async (tokenResponse) => {
        if (tokenResponse.error) {
          if (onError) onError(new Error(tokenResponse.error_description || tokenResponse.error));
          return;
        }

        try {
          // Fetch verified user profile directly from Google servers
          const res = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
            headers: {
              Authorization: `Bearer ${tokenResponse.access_token}`
            }
          });

          if (!res.ok) {
            throw new Error('Không thể tải thông tin tài khoản từ Google.');
          }

          const googleProfile = await res.json();
          // googleProfile: { sub, name, email, picture, email_verified }
          if (onSuccess) {
            onSuccess({
              email: googleProfile.email,
              name: googleProfile.name,
              googleId: googleProfile.sub,
              avatar: googleProfile.picture,
              credential: tokenResponse.access_token
            });
          }
        } catch (fetchErr) {
          if (onError) onError(fetchErr);
        }
      }
    });

    // Opens the authentic Google popup window
    tokenClient.requestAccessToken({ prompt: 'select_account' });
  } catch (err) {
    if (onError) onError(err);
  }
};

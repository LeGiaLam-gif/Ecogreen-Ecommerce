import React, { useEffect, useRef, useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { getGoogleClientId, renderGoogleSignInButton } from '../services/googleAuth';
import { facebookLogin, getFacebookAppId, loadFacebookSdk } from '../services/facebookAuth';
import { authErrorMessage } from '../utils/authErrors';

const FacebookIcon = () => (
  <svg className="social-icon" viewBox="0 0 24 24" width="20" height="20" fill="#1877F2" aria-hidden="true">
    <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z" />
  </svg>
);

/**
 * Facebook + Google sign-in buttons shared by the login and register pages.
 * `onSuccess(user)` runs after the session started; `onError(message)` with a ready-to-show message.
 * `remember` is the "Duy trì đăng nhập" choice (read at the moment the provider answers).
 */
const SocialAuthButtons = ({ googleText = 'signin_with', remember = true, onSuccess, onError }) => {
  const { loginWithGoogle, loginWithFacebook } = useAuth();
  const [googleUnavailable, setGoogleUnavailable] = useState(!getGoogleClientId());
  const [facebookReady, setFacebookReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const googleBtnRef = useRef(null);
  const live = useRef({ remember, onSuccess, onError });

  useEffect(() => {
    live.current = { remember, onSuccess, onError };
  });

  // Google: Google renders its own button; the effect runs once, so it reads the latest props through `live`.
  useEffect(() => {
    if (!googleBtnRef.current || !getGoogleClientId()) return;
    renderGoogleSignInButton(googleBtnRef.current, {
      onIdToken: async (idToken) => {
        setBusy(true);
        try {
          const user = await loginWithGoogle(idToken, live.current.remember);
          live.current.onSuccess?.(user);
        } catch (err) {
          live.current.onError?.(authErrorMessage(err, 'Đăng nhập bằng Google không thành công. Vui lòng thử lại.'));
        } finally {
          setBusy(false);
        }
      },
      onError: () => live.current.onError?.('Đăng nhập bằng Google không thành công. Vui lòng thử lại.'),
      text: googleText
    }).catch(() => setGoogleUnavailable(true));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Facebook: load the SDK as soon as the page opens so the click can open the popup immediately.
  useEffect(() => {
    if (!getFacebookAppId()) return;
    loadFacebookSdk()
      .then(() => setFacebookReady(true))
      .catch(() => setFacebookReady(false));
  }, []);

  const handleFacebookClick = () => {
    facebookLogin() // called synchronously from the click: the SDK is already loaded
      .then(async (accessToken) => {
        setBusy(true);
        try {
          const user = await loginWithFacebook(accessToken, live.current.remember);
          live.current.onSuccess?.(user);
        } finally {
          setBusy(false);
        }
      })
      .catch((err) => {
        if (err?.message === 'FACEBOOK_CANCELLED') return; // the person closed the popup
        live.current.onError?.(authErrorMessage(err, 'Đăng nhập bằng Facebook không thành công. Vui lòng thử lại.'));
      });
  };

  const facebookTitle = !getFacebookAppId()
    ? 'Đăng nhập Facebook chưa được cấu hình'
    : facebookReady
      ? 'Tiếp tục với Facebook'
      : 'Đang tải Facebook...';

  return (
    <div className="shopee-social-row">
      <button
        type="button"
        className="shopee-social-btn btn-facebook"
        onClick={handleFacebookClick}
        disabled={!facebookReady || busy}
        title={facebookTitle}
      >
        <FacebookIcon />
        <span>Facebook</span>
      </button>

      <div className="google-signin-slot">
        {googleUnavailable ? (
          <button type="button" className="shopee-social-btn btn-google" disabled title="Đăng nhập Google chưa được cấu hình">
            <span>Google</span>
          </button>
        ) : (
          <div ref={googleBtnRef} aria-busy={busy} />
        )}
      </div>
    </div>
  );
};

export default SocialAuthButtons;

import React, { createContext, useCallback, useContext, useRef, useState } from 'react';
import './Toast.css';

const ToastContext = createContext();

export const useToast = () => {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within a ToastProvider');
  return ctx;
};

const DEFAULT_DURATION = 4000;

/**
 * App-wide toast notifications. Replaces window.alert() for action errors
 * (add-to-cart failed, admin save failed, etc.) with a small, dismissible,
 * non-blocking notification - alert() freezes the whole page and looks like
 * a browser crash, which is a bad experience on both desktop and mobile.
 */
export const ToastProvider = ({ children }) => {
  const [toasts, setToasts] = useState([]);
  const nextId = useRef(0);

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const showToast = useCallback((message, tone = 'error', duration = DEFAULT_DURATION) => {
    const id = nextId.current++;
    setToasts((prev) => [...prev, { id, message, tone }]);
    if (duration > 0) {
      setTimeout(() => dismiss(id), duration);
    }
    return id;
  }, [dismiss]);

  const showError = useCallback((message) => showToast(message, 'error'), [showToast]);
  const showSuccess = useCallback((message) => showToast(message, 'success'), [showToast]);

  return (
    <ToastContext.Provider value={{ showToast, showError, showSuccess }}>
      {children}
      <div className="toast-stack" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.tone}`}>
            <span className="toast-icon">{t.tone === 'success' ? '✅' : '⚠️'}</span>
            <span className="toast-message">{t.message}</span>
            <button className="toast-close" onClick={() => dismiss(t.id)} aria-label="Dismiss">
              &times;
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};

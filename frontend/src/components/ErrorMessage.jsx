import React from 'react';
import './ErrorMessage.css';

/**
 * Shared page/section-level error banner. Replaces the ad-hoc
 * <p style={{ color: 'red' }}>{error}</p> that used to be copy-pasted on
 * every page.
 *
 * `tone`:
 *   - "error" (default): red banner, for failures.
 *   - "info": neutral banner, for non-error notices (e.g. "payment pending").
 */
const ErrorMessage = ({ message, tone = 'error', onRetry }) => {
  if (!message) return null;

  return (
    <div className={`error-banner error-banner-${tone}`} role="alert">
      <span className="error-banner-icon">{tone === 'error' ? '⚠️' : 'ℹ️'}</span>
      <span className="error-banner-text">{message}</span>
      {onRetry && (
        <button type="button" className="error-banner-retry" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
};

export default ErrorMessage;

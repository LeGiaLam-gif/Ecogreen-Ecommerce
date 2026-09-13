import React from 'react';
import './LoadingSpinner.css';

/**
 * Shared loading indicator so every page uses the same spinner instead of
 * each page defining its own ad-hoc markup.
 *
 * variant="page"   -> centered, fills the viewport height (page-level loads)
 * variant="inline" -> small spinner that sits inline with other content
 */
const LoadingSpinner = ({ variant = 'page', label }) => {
  if (variant === 'inline') {
    return (
      <span className="loading-inline">
        <span className="spinner spinner-sm" />
        {label && <span className="loading-inline-label">{label}</span>}
      </span>
    );
  }

  return (
    <div className="loading-page">
      <div className="spinner" />
      {label && <p className="loading-page-label">{label}</p>}
    </div>
  );
};

export default LoadingSpinner;

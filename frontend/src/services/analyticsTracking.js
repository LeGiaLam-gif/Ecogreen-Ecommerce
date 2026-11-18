import http from './http';

const SESSION_KEY = 'ecogreenAnalyticsSession';

const getSessionId = () => {
  try {
    let id = window.sessionStorage.getItem(SESSION_KEY);
    if (!id) {
      id = window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
      window.sessionStorage.setItem(SESSION_KEY, id);
    }
    return id;
  } catch {
    return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }
};

export const trackEvent = async (eventType, payload = {}) => {
  try {
    await http.post('/v1/analytics/events', { eventType, sessionId: getSessionId(), payload });
  } catch (error) {
    console.debug('Analytics event was not recorded.', error?.friendlyMessage || error?.message || 'Request failed');
  }
};

// Picks the most useful message from an error produced by services/http.js.
// A /api/v1 validation error carries per-field messages (err.fields); use the first real one. The backend masks messages of
// password/token fields as "Giá trị không hợp lệ.", so that placeholder is skipped.
const MASKED = 'Giá trị không hợp lệ.';

export const authErrorMessage = (err, fallback) => {
  const fields = err?.fields;
  if (fields && typeof fields === 'object') {
    const first = Object.values(fields).find((message) => typeof message === 'string' && message && message !== MASKED);
    if (first) return first;
  }
  return err?.friendlyMessage || fallback;
};

// Same rules as the backend (PasswordPolicy): the server re-checks, this only gives instant feedback.
export const PASSWORD_MIN_LENGTH = 8;

export const passwordProblem = (password) => {
  if (!password) return 'Vui lòng nhập mật khẩu.';
  if (password.length < PASSWORD_MIN_LENGTH) return `Mật khẩu phải có ít nhất ${PASSWORD_MIN_LENGTH} ký tự.`;
  if (new TextEncoder().encode(password).length > 72) return 'Mật khẩu quá dài (tối đa 72 byte).';
  return '';
};

#!/usr/bin/env node
/**
 * Dependency-free verification of the access-token refresh logic in src/services/http.js (B01-P2).
 * Run from the frontend folder after `npm install`:   node scripts/verify-auth-refresh.mjs
 * It copies http.js + authStorage.js to a temp dir (so Node can resolve the extensionless import), stubs
 * localStorage/window/navigator, and drives axios through a fake adapter. No network, no test framework.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'verify-refresh-'));
fs.writeFileSync(path.join(tmp, 'package.json'), '{"type":"module"}');
fs.symlinkSync(path.join(frontend, 'node_modules'), path.join(tmp, 'node_modules'), 'dir');
fs.copyFileSync(path.join(frontend, 'src/services/authStorage.js'), path.join(tmp, 'authStorage.js'));
fs.writeFileSync(
  path.join(tmp, 'http.js'),
  fs.readFileSync(path.join(frontend, 'src/services/http.js'), 'utf8').replace("'./authStorage'", "'./authStorage.js'")
);

// ── browser stubs ──
const store = {};
globalThis.localStorage = {
  getItem: (k) => (k in store ? store[k] : null),
  setItem: (k, v) => { store[k] = String(v); },
  removeItem: (k) => { delete store[k]; }
};
let redirectedTo = null;
globalThis.window = { location: { pathname: '/orders', assign: (u) => { redirectedTo = u; } } };

const axios = (await import(pathToFileURL(path.join(tmp, 'node_modules/axios/index.js')).href)).default;
const storage = await import(pathToFileURL(path.join(tmp, 'authStorage.js')).href);

// ── fake backend ──
const jwt = (n) => `h${n}.p${n}.s${n}`;
let refreshCalls = 0;
let refreshMode = 'ok'; // ok | 401 | 403 | 500 | network
let log = [];

const respond = (config, status, data) => {
  const res = { data, status, statusText: '', headers: {}, config, request: {} };
  if (status >= 400) {
    const e = new Error(`http ${status}`);
    e.config = config; e.response = res; e.isAxiosError = true;
    throw e;
  }
  return res;
};

axios.defaults.adapter = async (config) => {
  const url = (config.baseURL || '') + config.url;
  const auth = config.headers.Authorization || config.headers.get?.('Authorization');
  log.push(`${config.method} ${url} ${auth || '-'}`);

  if (url === '/api/v1/auth/refresh') {
    refreshCalls++;
    await new Promise((r) => setTimeout(r, 20)); // keep it in flight so concurrent 401s overlap
    if (refreshMode === 'ok') {
      return respond(config, 200, { data: { accessToken: jwt(2), refreshToken: 'refresh-2', expiresIn: 900 }, meta: null });
    }
    if (refreshMode === 'network') { const e = new Error('Network Error'); e.code = 'ERR_NETWORK'; e.config = config; throw e; }
    return respond(config, Number(refreshMode), { error: { code: 'X', message: 'refresh failed' } });
  }
  if (url === '/api/v1/auth/login') {
    return respond(config, 401, { error: { code: 'UNAUTHENTICATED', message: 'bad credentials' } });
  }
  if (auth === `Bearer ${jwt(2)}`) return respond(config, 200, url.startsWith('/api/v1') ? { data: { ok: url }, meta: null } : [{ id: 1 }]);
  return respond(config, 401, url.startsWith('/api/v1')
    ? { error: { code: 'UNAUTHENTICATED', message: 'expired' } }
    : { message: 'Vui lòng đăng nhập để tiếp tục.' });
};

// http.js (and its internal refresh client) must be imported AFTER the adapter is installed: axios.create() copies defaults.
const http = (await import(pathToFileURL(path.join(tmp, 'http.js')).href)).default;

// ── helpers ──
let passed = 0; let total = 0;
const check = (name, ok, detail = '') => {
  total++; if (ok) passed++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : `\n      ${detail}`}`);
};
const reset = (mode = 'ok') => {
  storage.clearSession(); log = []; refreshCalls = 0; refreshMode = mode; redirectedTo = null;
  storage.saveSession({ accessToken: jwt(1), refreshToken: 'refresh-1', user: { id: 1, username: 'alice' } });
};
const sessionCleared = () =>
  storage.getAccessToken() === null && storage.getRefreshToken() === null && storage.getStoredUser() === null;
const attempt = async (fn) => { try { return { value: await fn() }; } catch (error) { return { error }; } };

// 1-4. a failed refresh (any kind) ends the session
for (const [label, mode] of [['401', '401'], ['403', '403'], ['500', '500'], ['network error', 'network']]) {
  reset(mode);
  const r = await attempt(() => http.get('/orders'));
  check(`refresh ${label} -> session cleared (access, refresh, user) and redirected to /login`,
    !!r.error && sessionCleared() && redirectedTo === '/login' && refreshCalls === 1,
    JSON.stringify({ cleared: sessionCleared(), redirectedTo, refreshCalls }));
  check(`refresh ${label} -> original error still surfaced with friendlyMessage`,
    !!r.error?.friendlyMessage, JSON.stringify(r.error?.friendlyMessage));
}

// 5. concurrent 401s -> exactly one refresh attempt (success, then failure)
reset('ok');
const ok3 = await Promise.all([http.get('/orders'), http.get('/v1/products'), http.get('/v1/auth/me')]);
check('3 concurrent 401s (refresh succeeds) -> ONE refresh, all retried',
  refreshCalls === 1 && ok3.length === 3 && ok3[1].data.ok === '/api/v1/products', JSON.stringify({ refreshCalls, log }));

reset('500');
const bad3 = await Promise.all([attempt(() => http.get('/orders')), attempt(() => http.get('/v1/products')), attempt(() => http.get('/v1/auth/me'))]);
check('3 concurrent 401s (refresh fails with 500) -> ONE refresh attempt, all rejected, session cleared',
  refreshCalls === 1 && bad3.every((x) => x.error) && sessionCleared() && redirectedTo === '/login', JSON.stringify({ refreshCalls }));

// 6. no loops
reset('ok');
const normalAdapter = http.defaults.adapter;
http.defaults.adapter = async (config) => { // main client only: refresh works, but every other call keeps answering 401
  if ((config.baseURL + config.url) === '/api/v1/auth/refresh') return normalAdapter(config);
  log.push(`${config.method} ${config.baseURL + config.url} ${config.headers.Authorization || '-'}`);
  return respond(config, 401, { message: 'still 401' });
};
const loop = await attempt(() => http.get('/orders'));
http.defaults.adapter = normalAdapter;
check('server keeps answering 401 after a successful refresh -> one refresh, one retry, then stop',
  refreshCalls === 1 && !!loop.error && log.filter((l) => l.includes('/api/orders')).length === 2, JSON.stringify({ refreshCalls, log }));

reset('401');
await attempt(() => http.get('/orders'));
const callsAfterFailure = log.length;
const later = await attempt(() => http.get('/orders')); // session already cleared by the failed refresh
check('after a failed refresh, later 401s do not trigger another refresh',
  refreshCalls === 1 && !!later.error && log.length === callsAfterFailure + 1, JSON.stringify({ refreshCalls, log }));

reset('401'); // calling the refresh endpoint directly and getting 401 must not recurse into another refresh
const direct = await attempt(() => http.post('/v1/auth/refresh', { refreshToken: 'x' }));
check('401 from /auth/refresh called directly never triggers a nested refresh',
  !!direct.error && refreshCalls === 1, JSON.stringify({ refreshCalls, log }));

// login endpoint
reset('ok');
const login = await attempt(() => http.post('/v1/auth/login', { username: 'a', password: 'b' }));
check('401 from /auth/login never triggers refresh or redirect',
  refreshCalls === 0 && redirectedTo === null && login.error?.code === 'UNAUTHENTICATED' && login.error?.friendlyMessage === 'bad credentials',
  JSON.stringify({ refreshCalls, redirectedTo, code: login.error?.code }));

// legacy storage purge
localStorage.setItem('token', '3f2b8c1e-5a4d-4e0b-9a52-0c3d6f7e8a91');
storage.purgeLegacySession();
check('legacy UUID session is cleared silently', localStorage.getItem('token') === null);

fs.rmSync(tmp, { recursive: true, force: true });
console.log(`\n${passed}/${total} checks passed`);
process.exit(passed === total ? 0 : 1);

const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const ts = require(process.env.TYPESCRIPT_PATH || '/Applications/DevEco-Studio.app/Contents/sdk/default/openharmony/ets/build-tools/ets-loader/node_modules/typescript');
const source = fs.readFileSync(path.join(__dirname, '../ohos/jverification-native/src/main/ets/GycJVerificationService.ets'), 'utf8');
const exportsObject = {}, timers = new Map(), checks = [], logins = [];
let initCallback, initialized = false, initCalls = 0, preCalls = 0, dismissed = 0, cleared = 0, timerID = 0;
const sdk = {
  init(_, key, callback) { initCalls++; initCallback = callback; },
  isInitSuccess() { return initialized; },
  checkVerifyEnable() { return new Promise(resolve => checks.push(resolve)); },
  prelogin(callback) { preCalls++; callback(7000); },
  setCustomUIWithConfig() {},
  loginAuth(_, callback) { logins.push(callback); },
  dismissLoginAuthActivity() { dismissed++; },
  clearPreLoginCache() { cleared++; },
};
vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText, {
  exports: exportsObject,
  require(name) { if (name === '@jg/verify') return { JVerificationInterface: sdk, JVerificationLoginSettings: class {} }; throw Error(name); },
  setTimeout(fn) { timers.set(++timerID, fn); return timerID; },
  clearTimeout(id) { timers.delete(id); }, Promise, Error,
});
const settle = async () => { for (let i = 0; i < 20; i++) await Promise.resolve(); };
const Service = exportsObject.GycJVerificationService;
const make = () => new Service({}, 'host-key', () => ({}), {}, () => ({}));
(async () => {
  const first = make(), other = make();
  assert.equal((await first.prepare(false)).status, 'CONSENT_REQUIRED');
  assert.equal((await first.preLogin()).status, 'CONSENT_REQUIRED');
  assert.equal(initCalls, 0);
  const initializing = first.prepare(true);
  assert.equal((await other.initialize(true)).status, 'BUSY');
  other.close();
  first.revokeConsent();
  assert.equal((await initializing).status, 'CANCELED');
  initialized = true; initCallback(8000); await settle();
  assert.equal(preCalls, 0);
  first.close();
  const service = make();
  const preload = service.prepare(true); await settle();
  service.revokeConsent(); checks.shift()(true); await settle();
  assert.equal((await preload).status, 'CANCELED');
  assert.equal(preCalls, 0);
  const canceledGap = service.authenticate(true);
  queueMicrotask(() => service.cancel());
  assert.equal((await canceledGap).status, 'CANCELED');
  assert.equal(checks.length, 0);
  const revokedGap = service.authenticate(true);
  queueMicrotask(() => service.revokeConsent());
  assert.equal((await revokedGap).status, 'CANCELED');
  assert.equal(checks.length, 0);
  const ready = service.prepare(true); await settle(); checks.shift()(true);
  assert.equal((await ready).status, 'READY');
  assert.equal(logins.length, 0);
  const auth = service.authenticate(true); await settle(); checks.shift()(true); await settle();
  const stale = logins.shift();
  service.revokeConsent();
  assert.equal((await auth).status, 'CANCELED');
  stale(6000, 'secret-token', 'carrier'); await settle();
  assert.equal(dismissed, 1);
  const timeout = service.authenticate(true); await settle(); checks.shift()(true); await settle();
  [...timers.values()][0]();
  assert.equal((await timeout).status, 'TIMEOUT');
  service.close();
  assert.equal((await service.prepare(true)).status, 'CLOSED');
  assert.equal(timers.size, 0);
  assert.ok(cleared > 0);
  console.log('OHOS consent, unique owner, preload-only, revoke, late callbacks, timeout and close passed');
})().catch(error => { console.error(error); process.exitCode = 1; });

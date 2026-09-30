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
  loginAuth(settings, callback) { logins.push({ callback, event: settings.authPageEventListener }); },
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
  let lateOpened = 0;
  const auth = service.authenticate(true, () => lateOpened++); await settle(); checks.shift()(true); await settle();
  const stale = logins.shift();
  service.revokeConsent();
  assert.equal((await auth).status, 'CANCELED');
  stale.event(2); stale.callback(6000, 'secret-token', 'carrier'); await settle();
  assert.equal(dismissed, 1);
  assert.equal(lateOpened, 0);
  for (const code of [6000, 6002, 6001]) {
    const events = [];
    const result = service.authenticate(true, () => events.push('opened'));
    await settle(); checks.shift()(true); await settle();
    const login = logins.shift();
    assert.deepEqual(events, []);
    login.event(3); login.event(2); login.event(2);
    assert.deepEqual(events, ['opened']);
    let completed = false; result.then(() => completed = true); await settle();
    assert.equal(completed, false);
    login.callback(code, 'secret-token', 'carrier');
    events.push((await result).status); login.event(2);
    assert.deepEqual(events, ['opened', code === 6000 ? 'TOKEN' : code === 6002 ? 'CANCELED' : 'FAILED']);
  }
  const failedEvents = [];
  const failure = service.authenticate(true, () => failedEvents.push('opened'));
  await settle(); checks.shift()(true); await settle();
  const failedLogin = logins.shift();
  failedLogin.callback(6001, '', 'carrier');
  assert.equal((await failure).status, 'FAILED'); failedLogin.event(2);
  assert.deepEqual(failedEvents, []);
  const closedPage = service.authenticate(true, () => failedEvents.push('opened'));
  await settle(); checks.shift()(true); await settle();
  const closedLogin = logins.shift();
  closedLogin.event(1); closedLogin.event(2); closedLogin.callback(6002, '', '');
  assert.equal((await closedPage).status, 'CANCELED');
  assert.deepEqual(failedEvents, []);
  const timeout = service.authenticate(true, () => failedEvents.push('opened')); await settle(); checks.shift()(true); await settle();
  [...timers.values()][0]();
  assert.equal((await timeout).status, 'TIMEOUT');
  logins.shift().event(2);
  assert.deepEqual(failedEvents, []);
  const closing = service.authenticate(true, () => failedEvents.push('opened'));
  await settle(); checks.shift()(true); await settle();
  const closeLogin = logins.shift();
  service.close();
  assert.equal((await closing).status, 'CANCELED'); closeLogin.event(2);
  assert.deepEqual(failedEvents, []);
  assert.equal((await service.prepare(true)).status, 'CLOSED');
  assert.equal(timers.size, 0);
  assert.ok(cleared > 0);
  const moduleSource = fs.readFileSync(path.join(__dirname, '../ohos/jverification-native/src/main/ets/GycJVerificationModule.ets'), 'utf8');
  const moduleExports = {};
  vm.runInNewContext(ts.transpileModule(moduleSource, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText, {
    exports: moduleExports,
    require(name) {
      if (name === '@kuikly-open/render') return { KuiklyRenderBaseModule: class { onDestroy() {} } };
      if (name === './GycJVerificationService') return exportsObject;
      throw Error(name);
    }, Promise, JSON,
  });
  const bridge = new moduleExports.GycJVerificationModule(make());
  const messages = [];
  bridge.call('authenticate', '{"consentGranted":true}', reply => messages.push(reply.event || reply.status));
  await settle(); checks.shift()(true); await settle();
  const bridgeLogin = logins.shift();
  bridgeLogin.event(2); bridgeLogin.event(2);
  assert.deepEqual(messages, ['opened']);
  bridgeLogin.callback(6000, 'secret-token', 'carrier'); await settle();
  assert.deepEqual(messages, ['opened', 'TOKEN']);
  bridgeLogin.event(2); assert.deepEqual(messages, ['opened', 'TOKEN']);
  bridge.call('authenticate', '{"consentGranted":true}', reply => messages.push(reply.event || reply.status));
  await settle(); checks.shift()(true); await settle();
  const detached = logins.shift();
  bridge.call('cancel', '{}', null); detached.event(2); detached.callback(6000, 'secret-token', 'carrier');
  await settle(); assert.deepEqual(messages, ['opened', 'TOKEN']);
  bridge.call('authenticate', '{"consentGranted":true}', reply => messages.push(reply.event || reply.status));
  await settle(); checks.shift()(true); await settle();
  const destroyed = logins.shift(); bridge.onDestroy(); destroyed.event(2);
  destroyed.callback(6000, 'secret-token', 'carrier'); await settle();
  assert.deepEqual(messages, ['opened', 'TOKEN']);
  assert.equal(timers.size, 0);
  console.log('OHOS consent, owner, opened once, final-result order, failed launch, late events, timeout and close passed');
})().catch(error => { console.error(error); process.exitCode = 1; });

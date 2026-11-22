import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createSimulator, isLoopback } from '../src/server.js';

const { server, state } = createSimulator({ demoMode: true });
let baseUrl;

before(async () => {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});
after(async () => new Promise((resolve) => server.close(resolve)));

test('authorizes idempotently and rejects a key reused for another request', async () => {
  const body = { value: 25.98, code: 'ORDER-44', operationKey: 'payment-44' };
  const first = await fetch(`${baseUrl}/api/transactions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const retry = await fetch(`${baseUrl}/api/transactions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  assert.equal(first.status, 200);
  assert.deepEqual(await retry.json(), await first.json());
  assert.equal(state.operations.size, 1);

  const mismatch = await fetch(`${baseUrl}/api/transactions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...body, value: 30 }) });
  assert.equal(mismatch.status, 409);
});

test('demo controls inject failures and keep the simulator local', async () => {
  const controlled = await fetch(`${baseUrl}/api/control/failures`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ count: 1 }) });
  assert.equal(controlled.status, 200);
  const failed = await fetch(`${baseUrl}/api/transactions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ value: 7, code: 'ORDER-8', operationKey: 'retry-8' }) });
  assert.equal(failed.status, 503);
  assert.equal(state.operations.has('retry-8'), false);
  const retry = await fetch(`${baseUrl}/api/transactions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ value: 7, code: 'ORDER-8', operationKey: 'retry-8' }) });
  assert.equal(retry.status, 200);

  assert.equal(isLoopback('127.0.0.1'), true);
  assert.equal(isLoopback('::1'), true);
  assert.equal(isLoopback('192.168.1.50'), false);
});

test('controls are disabled outside demo mode', async () => {
  const ordinary = createSimulator({ demoMode: false });
  await new Promise((resolve) => ordinary.server.listen(0, '127.0.0.1', resolve));
  try {
    const response = await fetch(`http://127.0.0.1:${ordinary.server.address().port}/api/control/state`);
    assert.equal(response.status, 404);
  } finally {
    await new Promise((resolve) => ordinary.server.close(resolve));
  }
});

test('malformed JSON returns one client error without terminating the server', async () => {
  const invalid = await fetch(`${baseUrl}/api/transactions`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{broken' });
  assert.equal(invalid.status, 400);
  assert.match((await invalid.json()).message, /JSON/);
  const health = await fetch(`${baseUrl}/api/health`);
  assert.equal(health.status, 200);
});

import http from 'node:http';
import { createHash } from 'node:crypto';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HOST = '127.0.0.1';
const PORT = Number(process.env.PAYMENT_SIMULATOR_PORT ?? 8090);
const DEMO_MODE = process.env.PAYMENT_SIMULATOR_DEMO === 'true';

export function createSimulator({ demoMode = false } = {}) {
  const state = { available: true, failuresRemaining: 0, callCount: 0, operations: new Map() };
  const server = http.createServer(async (request, response) => {
    response.setHeader('Content-Type', 'application/json; charset=utf-8');
    response.setHeader('Cache-Control', 'no-store');
    const url = new URL(request.url, `http://${HOST}`);

    if (request.method === 'GET' && url.pathname === '/api/health') {
      return send(response, 200, { status: 'UP', service: 'payment-simulator' });
    }
    if (url.pathname.startsWith('/api/control/')) {
      if (!demoMode || !isLoopback(request.socket.remoteAddress)) return send(response, 404, { message: 'No encontrado.' });
      if (request.method === 'GET' && url.pathname === '/api/control/state') {
        return send(response, 200, { available: state.available, failuresRemaining: state.failuresRemaining, callCount: state.callCount, idempotentOperations: state.operations.size });
      }
      if (request.method === 'POST' && url.pathname === '/api/control/failures') {
        const parsed = await readJson(request);
        if (!parsed.ok) return send(response, parsed.status, { message: parsed.message });
        const body = parsed.value;
        if (!isRecord(body) || !Number.isInteger(body.count) || body.count < 0 || body.count > 100) return send(response, 400, { message: 'count debe estar entre 0 y 100.' });
        state.failuresRemaining = body.count;
        return send(response, 200, { failuresRemaining: state.failuresRemaining });
      }
      if (request.method === 'PUT' && url.pathname === '/api/control/availability') {
        const parsed = await readJson(request);
        if (!parsed.ok) return send(response, parsed.status, { message: parsed.message });
        const body = parsed.value;
        if (!isRecord(body) || typeof body.available !== 'boolean') return send(response, 400, { message: 'available debe ser booleano.' });
        state.available = body.available;
        return send(response, 200, { available: state.available });
      }
      return send(response, 404, { message: 'No encontrado.' });
    }
    if (request.method === 'POST' && url.pathname === '/api/transactions') {
      state.callCount += 1;
      if (!state.available) return send(response, 503, { result: 'UNAVAILABLE', message: 'Simulador temporalmente no disponible.' });
      if (state.failuresRemaining > 0) {
        state.failuresRemaining -= 1;
        return send(response, 503, { result: 'UNAVAILABLE', message: 'Fallo temporal simulado.' });
      }
      const parsed = await readJson(request);
      if (!parsed.ok) return send(response, parsed.status, { result: 'DECLINED', message: parsed.message });
      const body = parsed.value;
      if (!isRecord(body) || typeof body.operationKey !== 'string' || !body.operationKey.trim() || body.operationKey.length > 120 ||
          typeof body.code !== 'string' || !body.code.trim() || body.code.length > 80 ||
          !Number.isFinite(Number(body.value)) || Number(body.value) <= 0 || Number(body.value) > 9999.99) {
        return send(response, 400, { result: 'DECLINED', message: 'La solicitud de autorización no es válida.' });
      }
      const fingerprint = `${Number(body.value).toFixed(2)}|${body.code}`;
      const existing = state.operations.get(body.operationKey);
      if (existing) {
        if (existing.fingerprint !== fingerprint) return send(response, 409, { result: 'DECLINED', message: 'La clave ya pertenece a otra operación.' });
        return send(response, 200, existing.response);
      }
      const reference = `SIM-${createHash('sha256').update(body.operationKey).digest('hex').slice(0, 12).toUpperCase()}`;
      const result = { result: 'APPROVED', reference };
      state.operations.set(body.operationKey, { fingerprint, response: result });
      return send(response, 200, result);
    }
    return send(response, 404, { message: 'No encontrado.' });
  });
  return { server, state };
}

function send(response, status, body) {
  response.writeHead(status);
  response.end(JSON.stringify(body));
}

async function readJson(request) {
  try {
    let raw = '';
    for await (const chunk of request) {
      raw += chunk;
      if (raw.length > 8192) {
        return { ok: false, status: 413, message: 'La solicitud es demasiado grande.' };
      }
    }
    return { ok: true, value: JSON.parse(raw || '{}') };
  } catch {
    return { ok: false, status: 400, message: 'El JSON de la solicitud no es válido.' };
  }
}

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

export function isLoopback(address = '') {
  return address === '127.0.0.1' || address === '::1' || address.startsWith('::ffff:127.');
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  const { server } = createSimulator({ demoMode: DEMO_MODE });
  server.listen(PORT, HOST, () => process.stdout.write(`Payment simulator listening on http://${HOST}:${PORT}\nDemo controls: ${DEMO_MODE ? 'enabled' : 'disabled'}\n`));
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close(() => process.exit(0)));
}

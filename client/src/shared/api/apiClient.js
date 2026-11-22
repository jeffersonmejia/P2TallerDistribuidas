const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiRequestError extends Error {
  constructor({ status, code, message, correlationId }) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
    this.correlationId = correlationId;
  }
}

export async function requestJson(path, options = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers: { Accept: 'application/json', ...options.headers },
  });

  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    throw new ApiRequestError({
      status: response.status,
      code: payload.code ?? 'REQUEST_FAILED',
      message: payload.message ?? 'No se pudo completar la solicitud.',
      correlationId: payload.correlationId ?? response.headers.get('X-Correlation-ID'),
    });
  }

  if (response.status === 204) return null;
  return response.json();
}


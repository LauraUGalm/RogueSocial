// Talks to the server. The browser only ever says what the player wants to do;
// the server decides where they end up and what they find.

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.error ?? `Server said ${status}`);
    this.status = status;
    this.body = body;
  }
}

async function call(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(res.status, json);
  return json;
}

export const startRun = () => call('POST', '/api/runs');
export const resumeRun = (id) => call('GET', `/api/runs/${encodeURIComponent(id)}`);
export const takeTurn = (id, turn, action) =>
  call('POST', `/api/runs/${encodeURIComponent(id)}/turns`, { turn, action });

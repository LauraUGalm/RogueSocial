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

// The Tavern: posts, replies, likes, follows. Gold for posting comes out of the bank.
const post = (id) => `/api/posts/${encodeURIComponent(id)}`;

export const getMe = () => call('GET', '/api/me');
export const getFeed = (before) =>
  call('GET', before ? `/api/feed?before=${encodeURIComponent(before)}` : '/api/feed');
export const createPost = (text, replyTo = null) => call('POST', '/api/posts', { text, replyTo });
export const editPost = (id, text) => call('PATCH', post(id), { text });
// The post as a tombstone if it had replies, or null when it is gone (204, no body).
export const deletePost = (id) => call('DELETE', post(id));
export const getReplies = (id) => call('GET', `${post(id)}/replies`);
export const likePost = (id) => call('PUT', `${post(id)}/like`);
export const unlikePost = (id) => call('DELETE', `${post(id)}/like`);
export const follow = (username) => call('PUT', `/api/follows/${encodeURIComponent(username)}`);
export const unfollow = (username) => call('DELETE', `/api/follows/${encodeURIComponent(username)}`);

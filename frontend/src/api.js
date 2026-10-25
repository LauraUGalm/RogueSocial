// Talks to the server. The browser only ever says what the player wants to do;
// the server decides where they end up and what they find.

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.error ?? `Server said ${status}`);
    this.status = status;
    this.body = body;
  }
}

// Called when the server says nobody is signed in (it restarted, or the session ran out), so the
// app can show the sign-in screen. The /api/session calls answer 401 as a normal reply, not this.
let onSignedOut = () => {};
export function whenSignedOut(fn) {
  onSignedOut = fn;
}

async function call(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => null);
  if (res.status === 401 && !path.startsWith('/api/session')) onSignedOut();
  if (!res.ok) throw new ApiError(res.status, json);
  return json;
}

// Signing up signs you in too. The session lives in a cookie the browser sends by itself.
export const signUp = (username, email, password) => call('POST', '/api/users', { username, email, password });
export const signIn = (username, password) => call('POST', '/api/session', { username, password });
export const signOut = () => call('DELETE', '/api/session');
// Who is signed in, or null.
export const getSession = () =>
  call('GET', '/api/session').catch((e) => {
    if (e instanceof ApiError && e.status === 401) return null;
    throw e;
  });

export const startRun = () => call('POST', '/api/runs');
export const resumeRun = (id) => call('GET', `/api/runs/${encodeURIComponent(id)}`);
export const takeTurn = (id, turn, action) =>
  call('POST', `/api/runs/${encodeURIComponent(id)}/turns`, { turn, action });

// The Tavern: posts, replies, likes, follows. Gold for posting comes out of the bank.
const post = (id) => `/api/posts/${encodeURIComponent(id)}`;

export const getMe = () => call('GET', '/api/me');
export const getFeed = (before) =>
  call('GET', before ? `/api/feed?before=${encodeURIComponent(before)}` : '/api/feed');
// `audience` is FOLLOWERS or FRIENDS. A reply goes to its post's audience, so it leaves it out.
export const createPost = (text, replyTo = null, audience = null) =>
  call('POST', '/api/posts', { text, replyTo, audience });
export const editPost = (id, text) => call('PATCH', post(id), { text });
// The post as a tombstone if it had replies, or null when it is gone (204, no body).
export const deletePost = (id) => call('DELETE', post(id));
export const getReplies = (id) => call('GET', `${post(id)}/replies`);
export const likePost = (id) => call('PUT', `${post(id)}/like`);
export const unlikePost = (id) => call('DELETE', `${post(id)}/like`);
export const follow = (username) => call('PUT', `/api/follows/${encodeURIComponent(username)}`);
export const unfollow = (username) => call('DELETE', `/api/follows/${encodeURIComponent(username)}`);
// Each of these answers with your lists as they are now: {friends, incoming, outgoing}.
export const getFriends = () => call('GET', '/api/friends');
// Sends a request, or accepts theirs.
export const befriend = (username) => call('PUT', `/api/friends/${encodeURIComponent(username)}`);
// Unfriends, declines their request, or takes back yours.
export const unfriend = (username) => call('DELETE', `/api/friends/${encodeURIComponent(username)}`);
// Who you've blocked, as a list of usernames. Blocking and unblocking answer with the new list.
export const getBlocked = () => call('GET', '/api/blocks');
export const block = (username) => call('PUT', `/api/blocks/${encodeURIComponent(username)}`);
export const unblock = (username) => call('DELETE', `/api/blocks/${encodeURIComponent(username)}`);

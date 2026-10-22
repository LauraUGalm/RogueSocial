import { useRef, useState } from 'react';
import { ApiError, signIn, signUp } from './api.js';

// The door: sign in, or sign up. `onSignedIn` gets the player the server sends back.
export default function SignIn({ onSignedIn }) {
  const [mode, setMode] = useState('in');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState(null); // {text, list?}
  const [sending, setSending] = useState(false);
  // Brad Bradford gets the first weak password. After that, the real rules.
  const bradHasSpoken = useRef(false);

  function switchTo(next) {
    setMode(next);
    setMessage(null);
  }

  async function submit(e) {
    e.preventDefault();
    setSending(true);
    setMessage(null);
    try {
      onSignedIn(mode === 'in' ? await signIn(username, password) : await signUp(username, email, password));
    } catch (err) {
      setMessage(explain(err));
    } finally {
      setSending(false);
    }
  }

  function explain(err) {
    const problems = err instanceof ApiError ? err.body?.problems : null;
    if (!problems) return { text: err.message };
    if (!bradHasSpoken.current) {
      bradHasSpoken.current = true;
      return { text: 'Sorry, that password is already in use by Brad Bradford. Please choose another.' };
    }
    return { text: "Good news: Brad Bradford doesn't use that one. It still needs:", list: problems };
  }

  const signingUp = mode === 'up';
  return (
    <form className="sign-in" onSubmit={submit}>
      <nav className="tabs">
        <button type="button" className={signingUp ? 'tab' : 'tab current'} onClick={() => switchTo('in')}>
          Sign in
        </button>
        <button type="button" className={signingUp ? 'tab current' : 'tab'} onClick={() => switchTo('up')}>
          Sign up
        </button>
      </nav>

      <label>
        Username
        <input value={username} autoComplete="username" required onChange={(e) => setUsername(e.target.value)} />
      </label>
      {signingUp && (
        <label>
          Email
          <input type="email" value={email} autoComplete="email" required onChange={(e) => setEmail(e.target.value)} />
        </label>
      )}
      <label>
        Password
        <input
          type="password"
          value={password}
          autoComplete={signingUp ? 'new-password' : 'current-password'}
          required
          onChange={(e) => setPassword(e.target.value)}
        />
      </label>

      {message && (
        <div className="error" role="alert">
          {message.text}
          {message.list && (
            <ul>
              {message.list.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <button type="submit" disabled={sending}>
        {signingUp ? 'Sign up' : 'Sign in'}
      </button>
    </form>
  );
}

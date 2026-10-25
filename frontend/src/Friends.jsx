import { useEffect, useState } from 'react';
import { befriend, block, follow, getBlocked, getFriends, unblock, unfollow, unfriend } from './api.js';

// What the two buttons say for each radio choice: doing it, and undoing it.
const LABELS = {
  follow: ['Follow', 'Unfollow'],
  friend: ['Add friend', 'Unfriend'],
  block: ['Block', 'Unblock'],
};

// Friend requests waiting on you, one box to follow, friend or block someone by name, then your
// friends, the requests you sent, and who you've blocked. There are no notifications yet, so
// requests waiting on you are shown first, where you'll see them. `onChange` tells the Tavern who
// you see posts from has changed.
export default function Friends({ onChange, onOpenProfile }) {
  const [lists, setLists] = useState(null);
  const [blocked, setBlocked] = useState([]);
  const [name, setName] = useState('');
  const [mode, setMode] = useState('follow');
  const [confirming, setConfirming] = useState(null); // who is about to be blocked
  const [note, setNote] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([getFriends(), getBlocked()]).then(([f, b]) => {
      setLists(f);
      setBlocked(b);
    }, (err) => setError(err.message));
  }, []);

  async function act(doIt, who) {
    setError(null);
    try {
      const before = lists.friends.length;
      const now = await doIt(who);
      setLists(now);
      if (now.friends.length !== before) onChange();
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    }
  }

  // A block also ends friendships, requests and follows, so the friends lists and feed reload.
  async function changeBlock(doIt, who, done) {
    setConfirming(null);
    setError(null);
    try {
      setBlocked(await doIt(who));
      setLists(await getFriends());
      setNote(done);
      setName('');
      onChange();
    } catch (err) {
      setError(err.message);
    }
  }

  // Follow, friend or block, or undo one, for the name typed in the box.
  async function byName(e, undo) {
    e.preventDefault();
    const who = name.trim();
    if (!who) return;
    setNote(null);
    if (mode === 'block') {
      if (undo) changeBlock(unblock, who, `Unblocked ${who}.`);
      else setConfirming(who);
      return;
    }
    if (mode === 'friend') {
      if (await act(undo ? unfriend : befriend, who)) {
        setNote(undo ? `No longer friends with ${who}.` : `Asked ${who} to be friends.`);
        setName('');
      }
      return;
    }
    setError(null);
    try {
      await (undo ? unfollow : follow)(who);
      setNote(undo ? `Stopped following ${who}.` : `Following ${who}.`);
      setName('');
      onChange();
    } catch (err) {
      setError(err.message);
    }
  }

  if (!lists) return error ? <p className="error">{error}</p> : null;

  return (
    <section className="friends">
      {lists.incoming.map((who) => (
        <div key={who} className="friend-row request">
          <span>
            <button className="name" onClick={() => onOpenProfile(who)}>
              {who}
            </button>{' '}
            wants to be friends
          </span>
          <button className="quiet" onClick={() => act(befriend, who)}>
            Accept
          </button>
          <button className="quiet" onClick={() => act(unfriend, who)}>
            Decline
          </button>
          <button className="quiet" onClick={() => setConfirming(who)}>
            Block
          </button>
        </div>
      ))}

      <form className="follow-box" onSubmit={(e) => byName(e, false)}>
        <input value={name} placeholder="username" onChange={(e) => setName(e.target.value)} />
        <span className="radios">
          {Object.keys(LABELS).map((m) => (
            <label key={m}>
              <input type="radio" name="mode" checked={mode === m} onChange={() => setMode(m)} />
              {m[0].toUpperCase() + m.slice(1)}
            </label>
          ))}
        </span>
        <button type="submit" className="quiet">
          {LABELS[mode][0]}
        </button>
        <button type="button" className="quiet" onClick={(e) => byName(e, true)}>
          {LABELS[mode][1]}
        </button>
      </form>
      {confirming && (
        <p className="confirm">
          Block {confirming}? This ends any friendship and follows between you, and unblocking won't bring
          them back.
          <button className="icon danger" onClick={() => changeBlock(block, confirming, `Blocked ${confirming}.`)}>
            Block
          </button>
          <button className="icon" onClick={() => setConfirming(null)}>
            Cancel
          </button>
        </p>
      )}
      {note && <p className="hint">{note}</p>}

      <details>
        <summary>Friends ({lists.friends.length})</summary>
        {lists.friends.length === 0 && <p className="hint">No friends yet.</p>}
        {lists.friends.map((who) => (
          <div key={who} className="friend-row">
            <span>
              <button className="name" onClick={() => onOpenProfile(who)}>
                {who}
              </button>
            </span>
            <button className="icon" onClick={() => act(unfriend, who)}>
              Unfriend
            </button>
            <button className="icon" onClick={() => setConfirming(who)}>
              Block
            </button>
          </div>
        ))}
        {lists.outgoing.map((who) => (
          <div key={who} className="friend-row">
            <span className="hint">{who} (asked)</span>
            <button className="icon" onClick={() => act(unfriend, who)}>
              Take back
            </button>
          </div>
        ))}
      </details>

      {blocked.length > 0 && (
        <details>
          <summary>Blocked ({blocked.length})</summary>
          <p className="hint">Only you can see this list.</p>
          {blocked.map((who) => (
            <div key={who} className="friend-row">
              <span>{who}</span>
              <button className="icon" onClick={() => changeBlock(unblock, who, `Unblocked ${who}.`)}>
                Unblock
              </button>
            </div>
          ))}
        </details>
      )}
      {error && <p className="error">{error}</p>}
    </section>
  );
}

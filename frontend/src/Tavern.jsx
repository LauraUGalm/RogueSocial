import { useCallback, useEffect, useState } from 'react';
import Composer from './Composer.jsx';
import PostCard from './PostCard.jsx';
import { createPost, follow, getFeed, getMe, unfollow } from './api.js';

// The server sends 20 posts a page; fewer means there are no older ones.
const PAGE = 20;

// The social tab: your banked gold, a box to post, the feed, and following people.
export default function Tavern({ active }) {
  const [me, setMe] = useState(null);
  const [posts, setPosts] = useState(null);
  const [more, setMore] = useState(false);
  const [error, setError] = useState(null);

  const refresh = useCallback(async () => {
    setError(null);
    try {
      const [who, page] = await Promise.all([getMe(), getFeed()]);
      setMe(who);
      setPosts(page);
      setMore(page.length === PAGE);
    } catch (err) {
      setError(`Couldn't reach the tavern: ${err.message}`);
    }
  }, []);

  // Each time the tab opens: gold may have been banked, and others may have posted.
  useEffect(() => {
    if (active) refresh();
  }, [active, refresh]);

  async function older() {
    try {
      const page = await getFeed(posts[posts.length - 1].id);
      setPosts([...posts, ...page]);
      setMore(page.length === PAGE);
    } catch (err) {
      setError(err.message);
    }
  }

  const spent = useCallback((gold) => setMe((m) => ({ ...m, bankGold: m.bankGold - gold })), []);

  async function publish(text) {
    const { post, goldSpent } = await createPost(text);
    setPosts([post, ...posts]);
    spent(goldSpent);
  }

  if (error) {
    return (
      <div className="tavern">
        <p className="error">{error}</p>
        <button onClick={refresh}>Try again</button>
      </div>
    );
  }
  if (!me || !posts) return <div className="tavern">Pushing open the tavern door…</div>;

  return (
    <div className="tavern">
      <div className="status gold">
        <span>Banked gold: {me.bankGold}</span>
        <span className="hint">Posting as {me.username}</span>
      </div>

      <Composer gold={me.bankGold} placeholder="What did you find down there?" submitLabel="Post" onSubmit={publish} />

      <FollowBox onChange={refresh} />

      {posts.length === 0 && (
        <p className="hint">Nothing here yet. Post something, or follow someone to see their posts.</p>
      )}
      {posts.map((p) => (
        <PostCard key={p.id} post={p} me={me} onSpent={spent} />
      ))}
      {more && (
        <button className="quiet" onClick={older}>
          Older posts
        </button>
      )}
    </div>
  );
}

// There's no list of players yet, so you follow someone by typing their name.
function FollowBox({ onChange }) {
  const [name, setName] = useState('');
  const [note, setNote] = useState(null);

  async function run(e, doIt, done) {
    e.preventDefault();
    const who = name.trim();
    if (!who) return;
    try {
      await doIt(who);
      setNote({ ok: true, text: `${done} ${who}.` });
      setName('');
      onChange();
    } catch (err) {
      setNote({ ok: false, text: err.message });
    }
  }

  return (
    <form className="follow-box" onSubmit={(e) => run(e, follow, 'Following')}>
      <input value={name} placeholder="username" onChange={(e) => setName(e.target.value)} />
      <button type="submit" className="quiet">
        Follow
      </button>
      <button type="button" className="quiet" onClick={(e) => run(e, unfollow, 'Stopped following')}>
        Unfollow
      </button>
      {note && <span className={note.ok ? 'hint' : 'error'}>{note.text}</span>}
    </form>
  );
}

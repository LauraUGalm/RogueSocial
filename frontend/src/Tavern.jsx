import { useCallback, useEffect, useState } from 'react';
import Composer from './Composer.jsx';
import Friends from './Friends.jsx';
import PostCard from './PostCard.jsx';
import { createPost, getFeed, getMe } from './api.js';

// The server sends 20 posts a page; fewer means there are no older ones.
const PAGE = 20;

// The social tab: your banked gold, a box to post, friends, following people, and the feed.
export default function Tavern({ active, onOpenProfile }) {
  const [me, setMe] = useState(null);
  const [posts, setPosts] = useState(null);
  const [more, setMore] = useState(false);
  const [error, setError] = useState(null);
  // Bumped after blocking from a post, so the friends lists load again.
  const [blocks, setBlocks] = useState(0);

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

  async function publish(text, audience) {
    const { post, goldSpent } = await createPost(text, null, audience);
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

      <Composer gold={me.bankGold} placeholder="What did you find down there?" submitLabel="Post" chooseAudience onSubmit={publish} />

      <Friends key={blocks} onChange={refresh} onOpenProfile={onOpenProfile} />

      {posts.length === 0 && (
        <p className="hint">Nothing here yet. Post something, or follow someone to see their posts.</p>
      )}
      {posts.map((p) => (
        <PostCard
          key={p.id}
          post={p}
          me={me}
          onSpent={spent}
          onOpenProfile={onOpenProfile}
          onGone={() => setPosts((ps) => ps.filter((x) => x.id !== p.id))}
          onBlocked={() => {
            setBlocks((n) => n + 1);
            refresh();
          }}
        />
      ))}
      {more && (
        <button className="quiet" onClick={older}>
          Older posts
        </button>
      )}
    </div>
  );
}

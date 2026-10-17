import { useState } from 'react';
import Composer from './Composer.jsx';
import { createPost, deletePost, editPost, getReplies, likePost, unlikePost } from './api.js';

function ago(iso) {
  const seconds = Math.max(0, (Date.now() - new Date(iso)) / 1000);
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h`;
  return new Date(iso).toLocaleDateString();
}

// One post, or one reply when `isReply` is set (replies are one level deep, so they can't be
// replied to). `me` is {username, bankGold}; `onSpent` tells the Tavern gold left the bank, and
// `onGone` tells whoever lists this card that the post no longer exists.
export default function PostCard({ post: initial, me, onSpent, onGone, isReply = false }) {
  const [post, setPost] = useState(initial);
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [replies, setReplies] = useState(null); // null until opened
  const [error, setError] = useState(null);

  const mine = me && post.authorName.toLowerCase() === me.username.toLowerCase();
  const deleted = post.deletedAt != null;

  async function toggleLike() {
    setError(null);
    try {
      setPost(post.liked ? await unlikePost(post.id) : await likePost(post.id));
    } catch (err) {
      setError(err.message);
    }
  }

  async function toggleReplies() {
    if (replies) {
      setReplies(null);
      return;
    }
    setError(null);
    try {
      setReplies(await getReplies(post.id));
    } catch (err) {
      setError(err.message);
    }
  }

  async function saveEdit(text) {
    const { post: saved, goldSpent } = await editPost(post.id, text);
    setPost(saved);
    setEditing(false);
    onSpent(goldSpent);
  }

  // Gives no gold back. A post with replies comes back as a tombstone and stays.
  async function remove() {
    setConfirming(false);
    setError(null);
    try {
      const tombstone = await deletePost(post.id);
      if (tombstone) {
        setPost(tombstone);
        setEditing(false);
      } else {
        onGone();
      }
    } catch (err) {
      setError(err.message);
    }
  }

  // The server removes a tombstone along with its last reply, so this card goes too.
  function replyGone(id) {
    setReplies(replies.filter((r) => r.id !== id));
    if (deleted && post.replyCount === 1) {
      onGone();
    } else {
      setPost({ ...post, replyCount: post.replyCount - 1 });
    }
  }

  async function reply(text) {
    const { post: saved, goldSpent } = await createPost(text, post.id);
    setReplies([...(replies ?? []), saved]);
    setPost({ ...post, replyCount: post.replyCount + 1 });
    onSpent(goldSpent);
  }

  return (
    <article className={isReply ? 'post reply' : 'post'}>
      <header>
        <strong>{post.authorName}</strong>
        <span className="hint">
          {' · '}
          {ago(post.createdAt)}
          {post.editedAt && ' · edited'}
        </span>
      </header>

      {editing ? (
        <Composer
          initial={post.text}
          paid={post.paid}
          gold={me?.bankGold}
          submitLabel="Save"
          onSubmit={saveEdit}
          onCancel={() => setEditing(false)}
        />
      ) : deleted ? (
        <p className="post-text deleted">[deleted]</p>
      ) : (
        <p className="post-text">{post.text}</p>
      )}

      <footer>
        {!deleted && (
          <button className={post.liked ? 'icon liked' : 'icon'} onClick={toggleLike} aria-pressed={post.liked}>
            {post.liked ? '♥' : '♡'} {post.likeCount}
          </button>
        )}
        {!isReply && (
          <button className="icon" onClick={toggleReplies} aria-expanded={replies != null}>
            💬 {post.replyCount}
          </button>
        )}
        {mine && !deleted && !editing && !confirming && (
          <>
            <button className="icon" onClick={() => setEditing(true)}>
              Edit
            </button>
            <button className="icon" onClick={() => setConfirming(true)}>
              Delete
            </button>
          </>
        )}
        {confirming && (
          <span className="confirm">
            Delete for good? The gold stays spent.
            <button className="icon danger" onClick={remove}>
              Delete
            </button>
            <button className="icon" onClick={() => setConfirming(false)}>
              Keep
            </button>
          </span>
        )}
      </footer>
      {error && <p className="error">{error}</p>}

      {replies && (
        <div className="replies">
          {replies.map((r) => (
            <PostCard key={r.id} post={r} me={me} onSpent={onSpent} onGone={() => replyGone(r.id)} isReply />
          ))}
          {!deleted && <Composer gold={me?.bankGold} placeholder="Reply…" submitLabel="Reply" onSubmit={reply} />}
        </div>
      )}
    </article>
  );
}

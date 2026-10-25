import { useCallback, useEffect, useState } from 'react';
import {
  addToGallery,
  befriend,
  block,
  follow,
  galleryUrl,
  getProfile,
  pictureUrl,
  removeFromGallery,
  removePicture,
  saveProfile,
  setPicture,
  unfollow,
  unfriend,
} from './api.js';

const MAX_UPLOAD = 10 * 1024 * 1024;
const MAX_GALLERY = 30;
const PICTURE_TYPES = 'image/jpeg,image/png,image/webp';

function since(iso) {
  const seconds = Math.max(0, (Date.now() - new Date(iso)) / 1000);
  if (seconds < 60) return 'just now';
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return new Date(iso).toLocaleDateString();
}

// The browser can tell the size before sending, so a file that's too big fails here at once
// rather than after uploading 10 MB.
function tooBig(file) {
  return file.size > MAX_UPLOAD ? 'Pictures can be at most 10 MB' : null;
}

// Anyone's profile: picture, status, where they are, stats, about, and gallery. On your own you
// can change all of it. `onBlocked` tells the app you blocked them, since they're now gone.
export default function Profile({ username, active, onOpenProfile, onBlocked }) {
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setProfile(await getProfile(username));
    } catch (err) {
      setProfile(null);
      setError(err.message);
    }
  }, [username]);

  useEffect(() => {
    if (active) load();
  }, [active, load]);

  // Runs a change, then shows the profile as it is now.
  async function change(doIt) {
    setError(null);
    try {
      const updated = await doIt();
      if (updated && updated.username) setProfile(updated);
      else await load();
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    }
  }

  if (!profile) {
    return (
      <div className="profile">
        {error ? <p className="error">{error}</p> : <p className="hint">Looking them up…</p>}
      </div>
    );
  }

  return (
    <div className="profile">
      {!profile.mine && (
        <button className="quiet back" onClick={() => onOpenProfile(null)}>
          ← My profile
        </button>
      )}

      <header className="profile-head">
        <Avatar profile={profile} />
        <div>
          <h2>{profile.username}</h2>
          {profile.status && (
            <p className="status-line">
              {profile.status}
              <span className="hint"> · {since(profile.statusAt)}</span>
            </p>
          )}
          <p className="hint">
            {profile.location && <>📍 {profile.location} · </>}
            Joined {new Date(profile.joined).toLocaleDateString()}
          </p>
          <p className="stats">
            Level {profile.level} · ⚔️ {profile.power} · 🛡️ {profile.defense} · ❤️ {profile.maxHealth}
          </p>
        </div>
      </header>

      {profile.mine ? (
        <MyControls profile={profile} change={change} />
      ) : (
        <TheirControls profile={profile} change={change} onBlocked={onBlocked} />
      )}

      <section>
        <h3>About me</h3>
        {profile.about ? (
          <p className="about">{profile.about}</p>
        ) : (
          <p className="hint">{profile.mine ? 'Nothing yet. Edit your profile to add something.' : 'Nothing yet.'}</p>
        )}
      </section>

      <Gallery profile={profile} change={change} />
      {error && <p className="error">{error}</p>}
    </div>
  );
}

// Their picture, or the first letter of their name when they have none.
function Avatar({ profile }) {
  if (profile.pictureId) {
    return <img className="avatar" src={pictureUrl(profile.username, profile.pictureId)} alt="" />;
  }
  return <div className="avatar letter">{profile.username[0].toUpperCase()}</div>;
}

function MyControls({ profile, change }) {
  const [editing, setEditing] = useState(false);
  const [about, setAbout] = useState('');
  const [location, setLocation] = useState('');
  const [status, setStatus] = useState('');

  function startEditing() {
    setAbout(profile.about ?? '');
    setLocation(profile.location ?? '');
    setStatus(profile.status ?? '');
    setEditing(true);
  }

  async function save(e) {
    e.preventDefault();
    if (await change(() => saveProfile({ about, location, status }))) setEditing(false);
  }

  async function pickPicture(e) {
    const file = e.target.files[0];
    e.target.value = '';
    if (!file) return;
    await change(() => {
      const problem = tooBig(file);
      if (problem) throw new Error(problem);
      return setPicture(file);
    });
  }

  if (editing) {
    return (
      <form className="profile-edit" onSubmit={save}>
        <label>
          Status <span className="hint">({status.length}/100)</span>
          <input value={status} maxLength={100} placeholder="What are you up to?" onChange={(e) => setStatus(e.target.value)} />
        </label>
        <label>
          Location <span className="hint">({location.length}/50)</span>
          <input value={location} maxLength={50} placeholder="Where are you?" onChange={(e) => setLocation(e.target.value)} />
        </label>
        <label>
          About me <span className="hint">({about.length}/500)</span>
          <textarea value={about} maxLength={500} rows={4} onChange={(e) => setAbout(e.target.value)} />
        </label>
        <div className="row">
          <button type="submit">Save</button>
          <button type="button" className="quiet" onClick={() => setEditing(false)}>
            Cancel
          </button>
        </div>
      </form>
    );
  }

  return (
    <div className="row">
      <button className="quiet" onClick={startEditing}>
        Edit profile
      </button>
      <label className="quiet file-button">
        {profile.pictureId ? 'Change picture' : 'Add picture'}
        <input type="file" accept={PICTURE_TYPES} onChange={pickPicture} hidden />
      </label>
      {profile.pictureId && (
        <button className="quiet" onClick={() => change(removePicture)}>
          Remove picture
        </button>
      )}
      <span className="hint">Everyone but people you've blocked sees your picture.</span>
    </div>
  );
}

const FRIEND_BUTTONS = {
  NONE: ['Add friend', befriend],
  ASKED: ['Take back request', unfriend],
  ASKED_YOU: ['Accept friend request', befriend],
  FRIENDS: ['Unfriend', unfriend],
};

function TheirControls({ profile, change, onBlocked }) {
  const [confirming, setConfirming] = useState(false);
  const [label, friendAction] = FRIEND_BUTTONS[profile.relation];
  const name = profile.username;

  // Not through change(): once blocked, their profile is gone, so there's nothing to reload.
  async function blockThem() {
    setConfirming(false);
    try {
      await block(name);
      onBlocked();
    } catch (err) {
      change(() => Promise.reject(err));
    }
  }

  return (
    <div className="row">
      {profile.relation === 'FRIENDS' && <span className="tag">Friends</span>}
      {profile.followsYou && <span className="tag">Follows you</span>}
      <button className="quiet" onClick={() => change(() => (profile.following ? unfollow : follow)(name))}>
        {profile.following ? 'Unfollow' : 'Follow'}
      </button>
      <button className="quiet" onClick={() => change(() => friendAction(name))}>
        {label}
      </button>
      {confirming ? (
        <span className="confirm">
          Block {name}? This ends any friendship and follows between you.
          <button className="icon danger" onClick={blockThem}>
            Block
          </button>
          <button className="icon" onClick={() => setConfirming(false)}>
            Cancel
          </button>
        </span>
      ) : (
        <button className="quiet" onClick={() => setConfirming(true)}>
          Block
        </button>
      )}
    </div>
  );
}

function Gallery({ profile, change }) {
  const [audience, setAudience] = useState('FOLLOWERS');
  const pictures = profile.gallery;
  const full = pictures.length >= MAX_GALLERY;

  async function pick(e) {
    const file = e.target.files[0];
    e.target.value = '';
    if (!file) return;
    await change(() => {
      const problem = tooBig(file);
      if (problem) throw new Error(problem);
      return addToGallery(file, audience);
    });
  }

  return (
    <section>
      <h3>
        Gallery {profile.mine && <span className="hint">({pictures.length}/{MAX_GALLERY})</span>}
      </h3>
      {profile.mine && (
        <div className="row">
          <span className="radios">
            <label>
              <input type="radio" checked={audience === 'FOLLOWERS'} onChange={() => setAudience('FOLLOWERS')} />
              Followers
            </label>
            <label>
              <input type="radio" checked={audience === 'FRIENDS'} onChange={() => setAudience('FRIENDS')} />
              Friends
            </label>
          </span>
          <label className={full ? 'quiet file-button disabled' : 'quiet file-button'}>
            {full ? 'Gallery full' : 'Add to gallery'}
            <input type="file" accept={PICTURE_TYPES} onChange={pick} disabled={full} hidden />
          </label>
        </div>
      )}
      {pictures.length === 0 && <p className="hint">No pictures{profile.mine ? ' yet' : ' you can see'}.</p>}
      <div className="gallery">
        {pictures.map((p) => (
          <figure key={p.id}>
            <a href={galleryUrl(p.id)} target="_blank" rel="noreferrer">
              <img src={galleryUrl(p.id)} alt="" loading="lazy" />
            </a>
            {(p.audience === 'FRIENDS' || profile.mine) && (
              <figcaption>
                {p.audience === 'FRIENDS' ? 'friends only' : 'followers'}
                {profile.mine && (
                  <button className="icon" onClick={() => change(() => removeFromGallery(p.id))}>
                    Remove
                  </button>
                )}
              </figcaption>
            )}
          </figure>
        ))}
      </div>
    </section>
  );
}

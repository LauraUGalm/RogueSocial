import { useEffect, useState } from 'react';
import Dungeon from './Dungeon.jsx';
import Profile from './Profile.jsx';
import SignIn from './SignIn.jsx';
import Tavern from './Tavern.jsx';
import { getSession, signOut, whenSignedOut } from './api.js';

const TAB_KEY = 'rogueSocial.tab';

function savedTab() {
  try {
    const tab = localStorage.getItem(TAB_KEY);
    return tab === 'tavern' || tab === 'profile' ? tab : 'dungeon';
  } catch {
    return 'dungeon';
  }
}

export default function App() {
  const [tab, setTab] = useState(savedTab);
  // undefined while asking the server, null when nobody is signed in.
  const [player, setPlayer] = useState(undefined);
  const [error, setError] = useState(null);
  // Whose profile the Profile tab shows: null for your own.
  const [profileOf, setProfileOf] = useState(null);
  // Bumped after a block, so the Tavern starts over with fresh lists and feed.
  const [blocks, setBlocks] = useState(0);

  useEffect(() => {
    whenSignedOut(() => setPlayer(null));
    getSession().then(setPlayer, (err) => setError(`Couldn't reach the server: ${err.message}`));
  }, []);

  function show(next) {
    setTab(next);
    try {
      localStorage.setItem(TAB_KEY, next);
    } catch {
      // Without storage the page just opens on the dungeon next time.
    }
  }

  function openProfile(username) {
    setProfileOf(username && username.toLowerCase() !== player.username.toLowerCase() ? username : null);
    show('profile');
  }

  function blocked() {
    setBlocks((n) => n + 1);
    setProfileOf(null);
    show('tavern');
  }

  async function leave() {
    await signOut().catch(() => {});
    setPlayer(null);
  }

  if (error) {
    return (
      <main className="app">
        <h1>Rogue Social</h1>
        <p className="error">{error}</p>
      </main>
    );
  }
  if (player === undefined) return <main className="app" />;
  if (player === null) {
    return (
      <main className="app">
        <h1>Rogue Social</h1>
        <SignIn onSignedIn={setPlayer} />
      </main>
    );
  }

  return (
    <main className="app">
      <h1>Rogue Social</h1>
      <div className="signed-in hint">
        {player.username}
        <button className="quiet" onClick={leave}>
          Sign out
        </button>
      </div>
      <nav className="tabs">
        <button className={tab === 'dungeon' ? 'tab current' : 'tab'} onClick={() => show('dungeon')}>
          Dungeon
        </button>
        <button className={tab === 'tavern' ? 'tab current' : 'tab'} onClick={() => show('tavern')}>
          Tavern
        </button>
        <button className={tab === 'profile' ? 'tab current' : 'tab'} onClick={() => openProfile(null)}>
          Profile
        </button>
      </nav>
      {/* All stay mounted so none loses its place; `hidden` just stops drawing one. */}
      <div hidden={tab !== 'dungeon'}>
        <Dungeon active={tab === 'dungeon'} />
      </div>
      <div hidden={tab !== 'tavern'} className="tavern-wrap">
        <Tavern key={blocks} active={tab === 'tavern'} onOpenProfile={openProfile} />
      </div>
      <div hidden={tab !== 'profile'}>
        <Profile
          username={profileOf ?? player.username}
          active={tab === 'profile'}
          onOpenProfile={openProfile}
          onBlocked={blocked}
        />
      </div>
    </main>
  );
}

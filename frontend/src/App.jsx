import { useEffect, useState } from 'react';
import Dungeon from './Dungeon.jsx';
import SignIn from './SignIn.jsx';
import Tavern from './Tavern.jsx';
import { getSession, signOut, whenSignedOut } from './api.js';

const TAB_KEY = 'rogueSocial.tab';

function savedTab() {
  try {
    return localStorage.getItem(TAB_KEY) === 'tavern' ? 'tavern' : 'dungeon';
  } catch {
    return 'dungeon';
  }
}

export default function App() {
  const [tab, setTab] = useState(savedTab);
  // undefined while asking the server, null when nobody is signed in.
  const [player, setPlayer] = useState(undefined);
  const [error, setError] = useState(null);

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
      </nav>
      {/* Both stay mounted so neither loses its place; `hidden` just stops drawing one. */}
      <div hidden={tab !== 'dungeon'}>
        <Dungeon active={tab === 'dungeon'} />
      </div>
      <div hidden={tab !== 'tavern'} className="tavern-wrap">
        <Tavern active={tab === 'tavern'} />
      </div>
    </main>
  );
}

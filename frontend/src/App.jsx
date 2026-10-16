import { useState } from 'react';
import Dungeon from './Dungeon.jsx';
import Tavern from './Tavern.jsx';

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

  function show(next) {
    setTab(next);
    try {
      localStorage.setItem(TAB_KEY, next);
    } catch {
      // Without storage the page just opens on the dungeon next time.
    }
  }

  return (
    <main className="app">
      <h1>Rogue Social</h1>
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

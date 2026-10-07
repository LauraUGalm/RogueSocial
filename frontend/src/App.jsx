import { useCallback, useEffect, useRef, useState } from 'react';
import PlayArea from './PlayArea.jsx';
import { ApiError, resumeRun, startRun, takeTurn } from './api.js';
import { applyView } from './run.js';
import { loadSprites } from './sprites.js';

const KEYS = {
  ArrowUp: 'NORTH',
  ArrowDown: 'SOUTH',
  ArrowLeft: 'WEST',
  ArrowRight: 'EAST',
  l: 'LEAVE',
  L: 'LEAVE',
};

// Until there are accounts, the browser remembers which run is its own.
const RUN_KEY = 'rogueSocial.runId';

function savedRunId() {
  try {
    return localStorage.getItem(RUN_KEY);
  } catch {
    return null;
  }
}

function saveRunId(id) {
  try {
    localStorage.setItem(RUN_KEY, id);
  } catch {
    // Private windows can refuse storage; the run just won't resume after a reload.
  }
}

export default function App() {
  const [state, setState] = useState(null);
  const [sprites, setSprites] = useState(null);
  const [error, setError] = useState(null);
  // One turn at a time: keys pressed while a turn is in flight are dropped.
  const busy = useRef(false);
  // The key handler reads the latest state from here, so a key pressed just after a turn
  // lands never uses the previous turn number.
  const latest = useRef(null);
  latest.current = state;

  const begin = useCallback(async () => {
    setError(null);
    try {
      const view = await startRun();
      saveRunId(view.runId);
      setState(applyView(null, view, true));
    } catch (e) {
      setError(`Couldn't start a run: ${e.message}`);
    }
  }, []);

  useEffect(() => {
    loadSprites().then(setSprites, () => setSprites(null));

    const id = savedRunId();
    if (!id) {
      begin();
      return;
    }
    resumeRun(id)
      .then((view) => setState(applyView(null, view, true)))
      .catch((e) => {
        // The server forgot the run (it restarted): start a new one.
        if (e instanceof ApiError && e.status === 404) begin();
        else setError(`Couldn't load your run: ${e.message}`);
      });
  }, [begin]);

  // Sends one action to the server. Ignored while another is in flight or the run is over.
  const act = useCallback(async (action) => {
    const run = latest.current;
    if (busy.current || !run || run.status !== 'ACTIVE') return;

    busy.current = true;
    try {
      const view = await takeTurn(run.runId, run.turn, action);
      latest.current = applyView(run, view, false);
      setState(latest.current);
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        // Out of step with the server (another tab, or a lost reply): take its word for it.
        const view = await resumeRun(run.runId).catch(() => null);
        if (view) {
          latest.current = applyView(null, view, true);
          setState(latest.current);
        }
      } else {
        setError(`Lost touch with the dungeon: ${err.message}`);
      }
    } finally {
      busy.current = false;
    }
  }, []);

  useEffect(() => {
    function onKeyDown(e) {
      const action = KEYS[e.key];
      if (!action) return;
      e.preventDefault(); // keep the arrows from scrolling the page
      act(action);
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [act]);

  if (error) {
    return (
      <main className="app">
        <p className="error">{error}</p>
        <button onClick={() => window.location.reload()}>Try again</button>
      </main>
    );
  }
  if (!state) return <main className="app">Opening the dungeon…</main>;

  const left = state.status === 'LEFT';

  return (
    <main className="app">
      <h1>Rogue Social</h1>
      <div className="status">
        <span>Gold carried: {state.gold}</span>
        <span>Turn {state.turn}</span>
      </div>

      <PlayArea state={state} sprites={sprites} />

      <ul className="log">
        {state.log.length === 0 && (
          <li className="hint">
            Arrow keys to move. Find gold, then find the exit (&gt;) and leave to bank it.
          </li>
        )}
        {state.log.map((m, i) => (
          <li key={i}>{m}</li>
        ))}
      </ul>

      {state.onExit && !left && (
        <button onClick={() => act('LEAVE')}>Leave the dungeon (L)</button>
      )}
      {left && <button onClick={begin}>Enter a new floor</button>}
    </main>
  );
}

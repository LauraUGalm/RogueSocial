import { useCallback, useEffect, useRef, useState } from 'react';
import PlayArea from './PlayArea.jsx';
import { ApiError, resumeRun, startRun, takeTurn } from './api.js';
import { copyText, createRecorder, debugReport, logText, record, snapshot } from './debug.js';
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
  // Everything that happens, for the copy buttons.
  const recorder = useRef(createRecorder());
  const [copied, setCopied] = useState(null);

  const copy = useCallback(async (what) => {
    const text = what === 'log' ? logText(recorder.current) : debugReport(recorder.current, latest.current);
    const ok = await copyText(text);
    setCopied(ok ? what : 'failed');
    setTimeout(() => setCopied(null), 2000);
  }, []);

  const begin = useCallback(async () => {
    setError(null);
    try {
      const view = await startRun();
      saveRunId(view.runId);
      const run = applyView(null, view, true);
      record(recorder.current, { type: 'start', runId: view.runId, after: snapshot(run), monsters: view.monsters });
      setState(run);
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
      .then((view) => {
        const run = applyView(null, view, true);
        record(recorder.current, { type: 'resume', runId: view.runId, after: snapshot(run), monsters: view.monsters });
        setState(run);
      })
      .catch((e) => {
        // The server forgot the run (it restarted): start a new one.
        if (e instanceof ApiError && e.status === 404) begin();
        else setError(`Couldn't load your run: ${e.message}`);
      });
  }, [begin]);

  // Sends one action to the server. Ignored while another is in flight or the run is over.
  const act = useCallback(async (action, source) => {
    const run = latest.current;
    if (busy.current || !run || run.status !== 'ACTIVE') {
      record(recorder.current, {
        type: 'ignored', source, action,
        why: busy.current ? 'a turn was still in flight' : 'no active run',
      });
      return;
    }

    busy.current = true;
    const before = snapshot(run);
    try {
      const view = await takeTurn(run.runId, run.turn, action);
      latest.current = applyView(run, view, false);
      record(recorder.current, {
        type: 'turn', source, action, turnSent: run.turn,
        before, after: snapshot(latest.current),
        monstersWentFirst: view.monstersWentFirst,
        monsters: view.monsters,
        messages: view.messages,
      });
      setState(latest.current);
    } catch (err) {
      record(recorder.current, { type: 'error', source, action, turnSent: run.turn, before, status: err.status, error: err.message });
      if (err instanceof ApiError && err.status === 409) {
        // Out of step with the server (another tab, or a lost reply): take its word for it.
        const view = await resumeRun(run.runId).catch(() => null);
        if (view) {
          latest.current = applyView(null, view, true);
          record(recorder.current, { type: 'resync', after: snapshot(latest.current), monsters: view.monsters });
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
      act(action, `key ${e.key}${e.repeat ? ' (held)' : ''}`);
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

  const over = state.status !== 'ACTIVE';
  const { level, power, defense, maxHealth } = state.stats;

  return (
    <main className="app">
      <h1>Rogue Social</h1>
      <div className="status">
        <span className="health">
          Health {state.health}/{maxHealth}
          <span className="health-bar">
            <span style={{ width: `${(100 * state.health) / maxHealth}%` }} />
          </span>
        </span>
        <span>Level {level}</span>
        <span>Power {power}</span>
        <span>Defense {defense}</span>
      </div>
      <div className="status gold">
        <span>Gold carried: {state.gold}</span>
        <span>Turn {state.turn}</span>
      </div>

      <PlayArea state={state} sprites={sprites} />

      <ul className="log">
        {state.log.length === 0 && (
          <li className="hint">
            Arrow keys to move; walk into a monster to attack it. Find gold, then find the exit
            (&gt;) and leave to bank it.
          </li>
        )}
        {state.log.map((m, i) => (
          <li key={i}>{m}</li>
        ))}
      </ul>

      {state.onExit && !over && (
        <button onClick={() => act('LEAVE', 'button')}>Leave the dungeon (L)</button>
      )}
      {over && <button onClick={begin}>Enter a new floor</button>}

      <button className="quiet" onClick={() => copy('log')}>
        {copied === 'log' ? 'Copied!' : copied === 'failed' ? 'Copy failed' : 'Copy log'}
      </button>
      {copied === 'debug' && <p className="hint">Debug report copied.</p>}

      {/* Invisible: the bottom-right corner of the page copies the full debug report. */}
      <button
        className="invisible-copy"
        aria-label="Copy debug report"
        onClick={() => copy('debug')}
      />
    </main>
  );
}

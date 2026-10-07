// Records what happens in a run so it can be copied out and replayed by hand: every key pressed,
// what the server said back, and a text map of what the player has seen.

const MAX_EVENTS = 1000;

export function createRecorder() {
  return { pageLoaded: new Date().toISOString(), events: [] };
}

export function record(rec, entry) {
  rec.events.push({ at: new Date().toISOString(), ...entry });
  if (rec.events.length > MAX_EVENTS) rec.events.shift();
}

/** The parts of a run worth keeping in an event. Positions are relative to the entrance. */
export function snapshot(state) {
  return {
    turn: state.turn,
    status: state.status,
    player: state.player,
    health: state.health,
    gold: state.gold,
  };
}

/**
 * Everything the player has seen, as text. '@' player, 'b'/'s' a monster in sight (first letter
 * of its kind), '$' gold, '>' exit, '#' wall, '.' floor, ' ' never seen. Row and column numbers
 * are relative to the entrance, like the positions in the events.
 */
export function mapText(state) {
  let minR = Infinity, maxR = -Infinity, minC = Infinity, maxC = -Infinity;
  for (const tile of state.known.values()) {
    minR = Math.min(minR, tile.row); maxR = Math.max(maxR, tile.row);
    minC = Math.min(minC, tile.col); maxC = Math.max(maxC, tile.col);
  }
  const monsters = new Map(state.monsters.map((m) => [`${m.row},${m.col}`, m.kind[0]]));
  const lines = [`rows ${minR}..${maxR}, cols ${minC}..${maxC}`];
  for (let r = minR; r <= maxR; r++) {
    let line = '';
    for (let c = minC; c <= maxC; c++) {
      const k = `${r},${c}`;
      const tile = state.known.get(k);
      if (r === state.player.row && c === state.player.col) line += '@';
      else if (monsters.has(k)) line += monsters.get(k);
      else if (!tile) line += ' ';
      else if (tile.gold) line += '$';
      else line += tile.terrain;
    }
    lines.push(`${String(r).padStart(4)} ${line}`);
  }
  return lines.join('\n');
}

/** Every message this run, one per line, with the turn it came on. */
export function logText(rec) {
  const lines = [];
  for (const e of rec.events) {
    for (const m of e.messages ?? []) lines.push(`Turn ${e.after?.turn ?? '?'}: ${m}`);
  }
  return lines.length ? lines.join('\n') : 'Nothing has happened yet.';
}

/** The full report for debugging: the map now, the state now, and every event. */
export function debugReport(rec, state) {
  return [
    'Rogue Social debug report',
    `Copied: ${new Date().toISOString()}  Page loaded: ${rec.pageLoaded}`,
    `Run: ${state?.runId ?? 'none'}`,
    '',
    state ? mapText(state) : '(no run)',
    '',
    'Now: ' + JSON.stringify(state ? { ...snapshot(state), stats: state.stats, monsters: state.monsters } : null),
    '',
    'Events (oldest first):',
    ...rec.events.map((e) => JSON.stringify(e)),
  ].join('\n');
}

export async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    // Older browsers, or no clipboard permission: fall back to a hidden text box.
    const box = document.createElement('textarea');
    box.value = text;
    box.style.position = 'fixed';
    box.style.opacity = '0';
    document.body.appendChild(box);
    box.select();
    const ok = document.execCommand('copy');
    box.remove();
    return ok;
  }
}

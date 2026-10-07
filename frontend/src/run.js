// The player's knowledge of the floor, built up from what the server reveals.
// Positions are relative to the floor's entrance, as the server sends them.

const key = (row, col) => `${row},${col}`;

export function tileAt(state, row, col) {
  return state.known.get(key(row, col));
}

export function isVisible(state, row, col) {
  return state.visible.has(key(row, col));
}

/** Folds a server response into what the player knows. {@code fresh} starts the map over. */
export function applyView(state, view, fresh) {
  const known = fresh || !state ? new Map() : new Map(state.known);
  for (const tile of view.remembered ?? []) known.set(key(tile.row, tile.col), tile);
  for (const tile of view.visible) known.set(key(tile.row, tile.col), tile);

  const log = fresh || !state ? [] : state.log;
  return {
    runId: view.runId,
    turn: view.turn,
    status: view.status,
    gold: view.gold,
    player: view.player,
    known,
    visible: new Set(view.visible.map((t) => key(t.row, t.col))),
    log: view.messages.length ? [...log, ...view.messages].slice(-4) : log,
  };
}

import { useEffect, useRef } from 'react';
import { drawSprite } from './sprites.js';
import { isVisible, tileAt } from './run.js';

// The play area: a window onto the floor, centred on the player.
const VIEW_COLS = 17;
const VIEW_ROWS = 13;
const BLOCK = 40;

const COLORS = {
  unknown: '#0b0b10',
  wall: '#4a4458',
  floor: '#2a2633',
  exit: '#e8c547',
  // Laid over blocks the player remembers but cannot see right now.
  memory: 'rgba(11, 11, 16, 0.6)',
};

export default function PlayArea({ state, sprites }) {
  const canvasRef = useRef(null);

  useEffect(() => {
    const ctx = canvasRef.current.getContext('2d');
    const top = state.player.row - Math.floor(VIEW_ROWS / 2);
    const left = state.player.col - Math.floor(VIEW_COLS / 2);

    for (let vr = 0; vr < VIEW_ROWS; vr++) {
      for (let vc = 0; vc < VIEW_COLS; vc++) {
        const row = top + vr;
        const col = left + vc;
        const x = vc * BLOCK;
        const y = vr * BLOCK;
        const tile = tileAt(state, row, col);

        if (!tile) {
          ctx.fillStyle = COLORS.unknown;
          ctx.fillRect(x, y, BLOCK, BLOCK);
          continue;
        }
        ctx.fillStyle = tile.terrain === '#' ? COLORS.wall : COLORS.floor;
        ctx.fillRect(x, y, BLOCK, BLOCK);

        if (tile.terrain === '>') {
          ctx.fillStyle = COLORS.exit;
          ctx.font = `bold ${BLOCK * 0.7}px monospace`;
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          ctx.fillText('>', x + BLOCK / 2, y + BLOCK / 2 + 1);
        }
        if (tile.gold && sprites) {
          drawSprite(ctx, sprites, 'gold', x + 4, y + 4, BLOCK - 8);
        }
        if (!isVisible(state, row, col)) {
          ctx.fillStyle = COLORS.memory;
          ctx.fillRect(x, y, BLOCK, BLOCK);
        }
      }
    }

    const px = Math.floor(VIEW_COLS / 2) * BLOCK;
    const py = Math.floor(VIEW_ROWS / 2) * BLOCK;
    if (sprites) {
      drawSprite(ctx, sprites, 'player', px + 2, py + 2, BLOCK - 4);
    } else {
      ctx.fillStyle = '#c58cff';
      ctx.fillRect(px + 6, py + 6, BLOCK - 12, BLOCK - 12);
    }
  }, [state, sprites]);

  return (
    <canvas
      ref={canvasRef}
      className="play-area"
      width={VIEW_COLS * BLOCK}
      height={VIEW_ROWS * BLOCK}
    />
  );
}

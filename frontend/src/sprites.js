// Loads the Mark 1 sprite sheet and its index, and draws sprites by name.

const BASE = `${import.meta.env.BASE_URL}sprites/`;

export async function loadSprites() {
  const [index, image] = await Promise.all([
    fetch(`${BASE}sprites.json`).then((r) => r.json()),
    new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = () => reject(new Error('Could not load the sprite sheet'));
      img.src = `${BASE}spritesheet.png`;
    }),
  ]);
  return { index, image };
}

export function drawSprite(ctx, sprites, name, x, y, size) {
  const s = sprites.index.sprites[name];
  if (!s) return;
  const t = sprites.index.tileSize;
  ctx.drawImage(sprites.image, s.col * t, s.row * t, t, t, x, y, size, size);
}

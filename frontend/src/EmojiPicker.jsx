import { useEffect, useRef, useState } from 'react';

// A short, hand-picked set rather than every emoji there is. Each costs one gold, like any symbol.
const GROUPS = [
  ['Dungeon', '⚔️ 🗡️ 🛡️ 🏹 🪓 💰 🪙 💎 🗝️ 🧪 🔥 🕯️ 💀 🦇 🦂 🐉 🕷️ 🐀 🏰 🚪'],
  ['Faces', '😀 😂 😅 😎 🤔 😱 😭 😡 🥳 😴 🤯 😈'],
  ['Hands', '👍 👎 👏 🙌 💪 🙏 ✌️ 👋'],
  ['Other', '❤️ 💔 ✨ ⭐ 🎉 ⚡ ☠️ 🍀'],
].map(([name, list]) => [name, list.split(' ')]);

// A button that opens a grid of emoji; picking one calls onPick with it. Stays open so you can
// pick several; Escape or a click elsewhere closes it.
export default function EmojiPicker({ onPick }) {
  const [open, setOpen] = useState(false);
  const box = useRef(null);

  useEffect(() => {
    if (!open) return;
    function onKey(e) {
      if (e.key === 'Escape') setOpen(false);
    }
    function onClick(e) {
      if (!box.current?.contains(e.target)) setOpen(false);
    }
    window.addEventListener('keydown', onKey);
    window.addEventListener('mousedown', onClick);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('mousedown', onClick);
    };
  }, [open]);

  return (
    <span className="emoji-picker" ref={box}>
      <button
        type="button"
        className="icon"
        aria-label="Add an emoji"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        😀
      </button>
      {open && (
        <div className="emoji-panel" role="dialog" aria-label="Emoji">
          {GROUPS.map(([name, emoji]) => (
            <div key={name}>
              <div className="hint emoji-group">{name}</div>
              <div className="emoji-grid">
                {emoji.map((e) => (
                  // mousedown + preventDefault keeps the text box focused, so the caret stays put.
                  <button
                    key={e}
                    type="button"
                    className="emoji"
                    onMouseDown={(ev) => ev.preventDefault()}
                    onClick={() => onPick(e)}
                  >
                    {e}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </span>
  );
}

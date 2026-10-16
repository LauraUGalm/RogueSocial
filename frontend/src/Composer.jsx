import { useEffect, useRef, useState } from 'react';
import EmojiPicker from './EmojiPicker.jsx';
import { postCost } from './cost.js';

// A text box that shows what posting it will cost. Used for new posts, replies and edits.
// `paid` is what the post has already paid for, so an edit only shows the extra.
export default function Composer({ initial = '', paid = 0, gold, placeholder, submitLabel, onSubmit, onCancel }) {
  const [text, setText] = useState(initial);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);
  const box = useRef(null);

  // Editing starts with the caret at the end of the text, ready to add to it.
  useEffect(() => {
    if (initial === '') return;
    box.current.focus();
    box.current.setSelectionRange(initial.length, initial.length);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps -- only when the box first opens

  const symbols = postCost(text);
  const cost = Math.max(0, symbols - paid);
  const tooDear = gold != null && cost > gold;

  // Puts the emoji where the caret is (or over the selection), then moves the caret past it.
  function insert(emoji) {
    const el = box.current;
    const start = el ? el.selectionStart : text.length;
    const end = el ? el.selectionEnd : text.length;
    setText(text.slice(0, start) + emoji + text.slice(end));
    requestAnimationFrame(() => {
      el?.focus();
      el?.setSelectionRange(start + emoji.length, start + emoji.length);
    });
  }

  async function submit(e) {
    e.preventDefault();
    setSending(true);
    setError(null);
    try {
      await onSubmit(text);
      setText('');
    } catch (err) {
      setError(err.message);
    } finally {
      setSending(false);
    }
  }

  return (
    <form className="composer" onSubmit={submit}>
      <textarea
        ref={box}
        value={text}
        placeholder={placeholder}
        rows={3}
        onChange={(e) => setText(e.target.value)}
      />
      <div className="composer-bar">
        <EmojiPicker onPick={insert} />
        <span className={tooDear ? 'cost error' : 'cost'}>
          {symbols === 0
            ? 'Write something first'
            : paid > 0 && cost === 0
              ? 'Free: already paid for'
              : `Costs ${cost} gold${tooDear ? `, you have ${gold}` : ''}`}
        </span>
        {onCancel && (
          <button type="button" className="quiet" onClick={onCancel}>
            Cancel
          </button>
        )}
        <button type="submit" disabled={sending || symbols === 0 || tooDear}>
          {sending ? '…' : submitLabel}
        </button>
      </div>
      {error && <p className="error">{error}</p>}
    </form>
  );
}

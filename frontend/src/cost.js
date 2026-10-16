// What a post costs, worked out in the browser so the price shows while you type.
// The server counts again when you post (PostCost.java) and its count is the one you pay.
//
// One gold per symbol a reader sees, not counting spaces. JavaScript strings count UTF-16
// units, so "👍🏽".length is 4; Intl.Segmenter splits text into graphemes, what a reader sees
// as one character, the same way Java's BreakIterator does on the server.
const graphemes = new Intl.Segmenter(undefined, { granularity: 'grapheme' });

export function postCost(text) {
  let cost = 0;
  for (const { segment } of graphemes.segment(text)) {
    if (!/^\s+$/u.test(segment)) cost++;
  }
  return cost;
}

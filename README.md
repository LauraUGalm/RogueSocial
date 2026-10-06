# Rogue Social: Design Notes

A social media site where you earn the right to post by playing a turn-based dungeon crawler.
Gold found in the dungeon pays for the words you write.

These notes were captured from a design conversation on 2026-10-05. Each item is marked:

- **Decided**: Laura chose this.
- **Proposed**: suggested during the conversation, not yet confirmed.
- **Open**: needs a decision.

---

## 1. The core idea

- **Decided**: Players explore a randomly generated dungeon to find gold, and spend that gold to write posts.
- **Decided**: Every character in a post that is not whitespace costs one gold.
- **Decided**: There are two currencies. Gold is for posting. Silver (name is tentative) is for buying equipment.

A 280-character post with normal spacing costs roughly 230 gold, so the gold a run yields
directly sets how much a player can say.

## 2. Currencies and loot

- **Decided**: Chests hold a mix of gold and silver. Enemies drop a mix of gold and silver.
- **Decided**: Chests lean toward gold and enemies lean toward silver. Fighting makes you want
  better gear and pays in the currency that buys it; exploring and finding treasure earns talking.
- **Decided**: Players start with some silver so they can buy starting gear and never enter the
  dungeon with nothing.
- **Proposed**: The starting silver is a safety net, given only when a player has no silver and no
  gear anywhere, bank included. Handed out on every death it could be farmed.
- **Open**: The starting silver amount.
- **Open**: How characters are counted in edge cases: emoji (count one per visible symbol), links,
  replies, and edits.

## 3. The bank

- **Decided**: Outside the dungeon, the bank keeps gold, silver, and any items the player does not
  have equipped.
- **Decided**: Items left in the bank cannot be used while in the dungeon.
- **Proposed**: Only banked gold can pay for posts, so treasure has to make it out of the dungeon
  before it becomes words.
- **Open**: When a player can bank. Either only by leaving the dungeon alive (high tension), or
  at checkpoints such as a safe room every few floors (more forgiving).

## 4. Death and recovery

- **Decided**: Dying does not permanently destroy what the player was carrying. It stays on the
  floor where they died, and they may go back to that same floor and fight to reclaim it.
- **Decided**: If they choose not to go back, it is gone forever, because floors are randomly
  generated.
- **Decided**: Only the last floor a player died on is kept. It is discarded when they decline it.
- **Decided**: The saved floor is a snapshot, not a fresh copy. Monsters the player already killed
  stay dead; only the ones they had not reached are still there.
- **Proposed**: The snapshot also keeps opened chests as opened and the tiles the player had
  uncovered, so their map of that floor comes back.
- **Proposed**: Surviving monsters return at full health.
- **Proposed**: Dying during a recovery run replaces the old dropped pile with whatever the player
  was carrying this time.
- **Proposed**: A death floor is kept for one week, or until the player starts a new dungeon.
- **Open**: Where the player re-enters a death floor. If they can start anywhere, they could start
  beside the tombstone and skip the fight. Starting at the floor's original entrance avoids that.

## 5. Pause

- **Decided**: A player can leave mid-run and come back a day or two later.
- **Decided**: Pausing is automatic. The game is turn-based and the server saves after every turn,
  so closing the tab at any moment resumes exactly where it left off. There is no pause button.
- **Proposed**: A player has at most one saved floor at a time: either a paused run or a death floor.
- **Proposed**: Pausing is not banking. What the player carries stays at risk until they get out.
- **Proposed**: Paused runs do not expire.

## 6. The dungeon

- **Decided**: The game is turn-based, like the original Rogue. Monsters act after the player does.
- **Decided**: Monsters that are off screen keep moving even though the player cannot see them.
- **Decided**: Floors get bigger as the player levels up. A level 1 player gets the size of the
  current Letter Maze (15 x 11 cells); a level 20 player gets a much larger floor, with stronger
  monsters and more of them.
- **Decided**: The player picks the difficulty tier of the floor they enter, up to their level.
  Someone short on time can choose a smaller floor.
- **Decided**: The player can start a floor at any position that is not inside a wall.
- **Proposed**: Start positions keep a minimum distance from monsters and from the exit, and the
  start must always be able to reach the exit.
- **Proposed**: A death floor keeps the tier it was generated at.
- **Open**: The floor generator. The current maze code makes a pure maze with one path between any
  two points. Rogue-style floors need rooms joined by corridors.
- **Open**: How levelling works: what earns experience and how fast levels come.
- **Open**: How unseen monsters behave: wander, sleep until the player is near, or hunt.

## 7. What the player can see

- **Decided**: The server keeps the whole floor and sends the player only the parts they can see,
  adding more as they reach the edge of what they know.
- **Decided**: A map shows everywhere the player has been. It starts empty and fills in as they
  explore.
- **Decided**: The screen is laid out like a Nintendo DS or 3DS: the map and the play area are both
  always visible, one above the other, and the map has a marker showing where the player is.
- **Decided**: The play area has a limited view and is the only place monsters appear. A monster
  that moves into somewhere the player has already been does not show on the map, so they cannot
  tell it is there.
- **Decided**: A tombstone marks where the player's gear lies after they die. It is shown on the
  map during a recovery run.
- **Decided**: Spells can reveal more of the map, or give a sense of where treasure or monsters
  are without revealing the maze around them.
- **Proposed**: Sensed treasure and monsters appear as markers in unexplored darkness. Monster
  markers fade after a few turns, since monsters keep moving.
- **Proposed**: The map also marks any stairs the player has found.
- **Proposed**: On a phone the map goes on top and the play area on the bottom, nearer the thumbs.
- **Proposed**: A compass arrow at the edge of the play area points toward the tombstone when it is
  out of view.
- **Open**: How the map scales on large floors: shrink to fit everything explored, or keep a fixed
  scale and scroll with the player.
- **Open**: What counts as seeing. Options: a fixed radius around the player, true line of sight
  (blocked by walls), or Rogue-style (a whole room lights up on entry, corridors one step at a
  time). Suggested: line of sight on the current maze, Rogue-style once rooms exist.
- **Open**: What spells cost: a magic pool that refills, scrolls bought with silver, or a cooldown
  counted in turns.

## 8. Keeping it fair

- **Decided**: The browser never sends grid coordinates. It sends only what the player wants to do
  (move north, attack, cast a spell, use an item, take the stairs). The server works out the result
  from the position it already holds, so a forged request cannot claim to be somewhere else.
- **Proposed**: Gold is never claimed by the browser, only granted by the server when the player
  steps onto it.
- **Proposed**: Each request carries a turn number, so replayed or out-of-order requests are rejected.
- **Proposed**: Positions sent to the browser are relative to where the player started, so the
  browser never learns the floor's true size or where its edges are.
- **Proposed**: A rate limit on turns, to slow down scripts that play the game automatically.

## 9. Storage

- **Decided**: Everything lives in DynamoDB: the game and the social side. There is no relational
  database. Part of the purpose of this project is hands-on NoSQL experience beyond storing
  sessions and settings, and follows, likes, and feeds are where that experience comes from.
- **Decided**: Saved floors go in the database so they survive a player walking away for a day or
  two.
- **Decided**: Store the whole floor, not just the random seed that generated it.
- **Proposed**: One item per player for the saved floor, keyed by player ID. Dying overwrites it
  and starting a new dungeon deletes it.
- **Proposed**: Use DynamoDB's automatic expiry for abandoned death floors, and also check the
  date when loading, because automatic deletion can lag.
- **Proposed**: Gold and silver balances use conditional writes, so "spend 230 gold only if they
  have 230" is safe when two requests arrive at once.
- **Proposed**: Posting is one transaction: deduct the gold and create the post, or do neither.
- **Proposed**: Banking is one transaction: credit the bank and delete the floor, or do neither.
- **Proposed**: Posts are keyed by author with a timestamp sort key, so "this person's posts,
  newest first" is a single query.
- **Proposed**: Follows and likes are stored so they can be read in both directions ("who do I
  follow" and "who follows me"), using a secondary index.
- **Proposed**: The feed starts as a read-time merge: fetch recent posts from each followed person
  and combine them. If that gets slow, switch to copying each new post into followers' feeds.
- **Open**: The table design. DynamoDB tables are built around the queries they must answer, so
  the feed and profile features in section 11 need sketching first.

A floor is small. The current maze is about 700 characters as a grid; a floor four times wider and
taller is about 11 KB, far below DynamoDB's 400 KB limit per item.

## 10. Mark 1 art

- **Decided**: The first version has no custom art. Emoji stand in for monsters, treasure, and the
  player.
- **Decided**: The emoji are drawn once into a sprite sheet (one image holding every tile), and the
  game draws from that image. Players' devices never render the emoji themselves, so everyone sees
  the same pictures.
- **Decided**: The cast includes a troll and a tombstone.
- **Proposed**: The pictures come from Google's Noto Emoji, which is free to use this way.
- **Proposed**: Tiles are 64 pixels. A small index file maps each sprite name to its place in the
  sheet, and the game looks sprites up by name.
- **Proposed**: Saved floors store a monster's type (`bat`), never its picture. Real art later
  means repainting the sheet in the same layout, with no change to the server or to saved floors.
- **Proposed**: The Mark 1 cast, weakest to strongest: rat, bat, spider, scorpion, snake, wolf,
  skeleton, ghost, ogre, troll, dragon. Objects: chest (a money bag), gold coin, tombstone. The
  player is a mage.
- **Open**: How a tougher "elite" monster is shown. A red tint was tried and barely shows on the
  ogre, which is already red. A coloured ring or a small crown would work for every monster.
- **Open**: The spider is hard to see on a dark floor. Options are a lighter floor or a pale
  outline around sprites.

A first sheet with this cast has been generated (`spritesheet.png`, `sprites.json`, and the script
`make_sheet.py` with its list `cast.json`). It is not in a repository yet.

## 11. Not designed yet

- The social side itself: the feed, following, replies, likes, and what a profile shows. This is
  next, because the table design depends on it.
- Accounts and sign-in.
- Combat rules, monster types, gear, and item stats.
- Whether it is intended that experienced players, who earn more gold per run, become the loudest
  voices on the site.

---

## Starting point: the Letter Maze code

The MazeGame repository holds Letter Maze, a small game with a Java 21 / Spring Boot backend and
a React frontend.

**Carries over**

- The grid format (`#` for wall, `.` for floor).
- The canvas renderer.
- Arrow-key movement.
- The "walk onto a tile to pick it up" rule.

**Needs to change**

- The server sends the whole maze at once and then forgets it. It has to keep the floor and reveal
  it turn by turn.
- The browser decides what was collected. The server has to decide.
- There are no accounts, no database, and no monsters.
- The generator makes a pure maze, not rooms and corridors.

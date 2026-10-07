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
- **Decided**: A player can bank only by leaving the dungeon alive. There are no checkpoints;
  everything carried stays at risk until they get out.
- **Decided**: Reaching the exit does not end the run by itself. Standing on the exit, the player
  chooses to leave, which banks the gold they carry; or they keep exploring.

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
- **Decided**: The player re-enters a death floor at its original entrance, so they have to fight
  their way back to the tombstone. This is an exception to choosing a start position (section 6).

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
- **Decided**: The player can start a floor at any position that is not inside a wall. Death
  floors are the exception: they always start at the original entrance (section 4).
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
- **Decided**: What counts as seeing: true line of sight (blocked by walls) on the current maze,
  then Rogue-style (a whole room lights up on entry, corridors one step at a time) once the
  generator makes rooms.
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

- **Decided** (changed 2026-10-06): Data lives in PostgreSQL, laid out like the Stonks project:
  `db/schema.sql` holds the current shape and `db/migrations/` holds numbered change scripts.
  This replaces the earlier decision to keep everything in DynamoDB. The items below that were
  written with DynamoDB in mind (automatic expiry, one item per player, secondary indexes, the
  400 KB limit) need revisiting for Postgres.
- **Decided**: A `users` table holds one row per player: ID, username, email, and when they
  joined. Usernames and emails are unique ignoring case.
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

A first sheet with this cast is in the RogueSocial repository: `spritesheet.png`, its index
`sprites.json`, and the script `make_sheet.py` with its list `cast.json`.

Sprites are derived from [Google's Noto Emoji](https://github.com/googlefonts/noto-emoji), licensed
under Apache 2.0.

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

- ~~The server sends the whole maze at once and then forgets it. It has to keep the floor and reveal
  it turn by turn.~~ Done: see "The code so far" below.
- ~~The browser decides what was collected. The server has to decide.~~ Done.
- There are no accounts, no database, and no monsters.
- The generator makes a pure maze, not rooms and corridors.

Letter Maze itself is left untouched. The pieces that carried over were copied into this repository.

---

## The code so far

A Java 21 / Spring Boot backend and a React frontend, started from Letter Maze.

**What works**

- The server keeps the whole floor and runs every turn. The browser sends only a turn number and
  an action (`NORTH`, `SOUTH`, `EAST`, `WEST`) and never a position.
- Line of sight: the player sees every block within 6 that a straight line reaches without
  crossing a wall. What they have seen stays on screen, dimmed.
- Gold is granted by the server when the player steps onto it.
- Each turn must carry the current turn number. A replayed or out-of-order turn is rejected.
  Walking into a wall does not use a turn.
- Positions sent to the browser are relative to the entrance.
- Standing on the exit (`>`), the player can leave (`L` or the button). That adds the gold they
  carry to their bank in Postgres and ends the run. The bank is credited before the run ends,
  so if the database is down the run simply carries on.
- There is no sign-in, so every run belongs to one hard-coded player, `rogue.player=laura` in
  `application.properties`, created by `db/seed.sql`.
- Closing the tab and coming back resumes the run, with the map so far.

**Not built yet**

- Runs are kept in memory (behind `RunStore`) and lost when the server restarts. They are not
  in Postgres yet.
- There is a `users` table and an API to create and look up users, but no sign-in. The browser
  remembers its run ID in local storage.
- Silver, and spending banked gold on posts, are not built.
- No monsters, combat, death, banking, levels, the overview map, or the rate limit.
- The player always starts at the entrance; choosing a start position is not built.
- The gold amounts (8 piles of 5 to 25) are placeholders until the economy is designed.

### Running it

You need **Java 21**, **Maven 3.6+**, **Node 18+** and **PostgreSQL**.

Create the database once, then point the server at it:

```bash
createdb -h localhost -U postgres rogue_social
psql -h localhost -U postgres -d rogue_social -f db/schema.sql
psql -h localhost -U postgres -d rogue_social -f db/seed.sql     # the player, laura

export ROGUE_DB_PASSWORD='...'   # required; nothing is stored in the repo
# Optional: ROGUE_DB_URL (default jdbc:postgresql://localhost:5432/rogue_social)
#           ROGUE_DB_USER (default postgres)
```

Run these from the repository folder. On a database made before a change, apply the scripts in
`db/migrations/` in order instead of `schema.sql`.

The game runs without the database until you leave the dungeon; banking and `/api/users` need it.

```bash
cd frontend
npm install
npm run build        # builds into the backend's static folder

cd ../backend
mvn spring-boot:run
```

Then open **http://localhost:8081/play**. The port is 8081 because the Stonks app uses 8080.

For frontend work, leave the server running and run `npm run watch` in `frontend/`, then refresh
the browser after saving. Backend tests: `mvn test` in `backend/`. The user tests run against
the real database and are skipped unless `ROGUE_DB_PASSWORD` is set; they only create users named
`zztest_...` and delete them afterwards.

### API

| Request | Does |
|---|---|
| `POST /api/runs` | Starts a run on a new floor. |
| `GET /api/runs/{id}` | The run as it stands, including `remembered`: every block seen so far. |
| `POST /api/runs/{id}/turns` with `{"turn": 3, "action": "NORTH"}` | Plays one turn. Actions: `NORTH`, `SOUTH`, `EAST`, `WEST`, and `LEAVE` (only on the exit). |

Every response has `runId`, `turn`, `status` (`ACTIVE` or `LEFT`), `gold` (carried), `player`,
`onExit`, `visible` (tiles in sight now) and `messages`. A tile is `{row, col, terrain}` with `terrain`
`#` wall, `.` floor or `>` exit, plus `gold` when there is some. A wrong turn number gets
`409` with `currentTurn`; an unknown run gets `404`.

| Request | Does |
|---|---|
| `POST /api/users` with `{"username": "laura", "email": "..."}` | Creates a user. `400` for a bad username, `409` if the username or email is taken. |
| `GET /api/users/{username}` | `{userId, username, joined}`, never the email. `404` if there is none. |

There is no sign-in yet, so anyone can create a user. This is for development.

### Layout

```
backend/src/main/java/com/roguesocial/
├── maze/      Wilson's algorithm, copied from Letter Maze
├── dungeon/   Floor, FloorGenerator, LineOfSight, Run, RunService, RunStore, Bank
├── user/      User, UserRepository, UserBank (SQL via JdbcClient), UserController
└── web/       REST API, and serves the React app at /play
db/
├── schema.sql     The whole current database, for a new empty database
├── seed.sql       The one player until there is sign-in
└── migrations/    Numbered change scripts for an existing database
frontend/src/
├── App.jsx       Status line, arrow keys, message log
├── PlayArea.jsx  Draws the view around the player from the sprite sheet
├── run.js        Builds up the player's map from what the server reveals
├── api.js        Calls to the server
└── sprites.js    Loads spritesheet.png and sprites.json
```

`frontend/public/sprites/` holds copies of `spritesheet.png` and `sprites.json`. Copy them again
after re-running `make_sheet.py`.

# DuelCore — Batch 1 + 2: Foundation + Tiers/Placeholders/Profile

## How the plugin actually works, end to end

1. **A player joins** → `PlayerConnectionListener` loads their row from MySQL
   (creating one if it's their first time) into an in-memory `PlayerProfile`
   cache, then `HotbarManager` gives them the 6 hotbar items from `config.yml`.
2. **Right-clicking a hotbar item** (`HotbarListener`) just runs the matching
   command (`/queue`, `/duel`, `/party`, `/leaderboard`, `/duelsettings`,
   `/kiteditor`) — so the hotbar and the commands are always in sync; the
   actual GUI menus for those are the next batch.
3. **Tiers are not earned automatically.** A staff member runs
   `/result <player> <gamemode> <tier>` after judging a tier-test match.
   That single command:
   - saves the tier for that kit in `dc_kit_stats`
   - unlocks the armor trim tied to that tier (`dc_trim_unlocks`)
   - recalculates the player's **overall points** — the sum of every kit's
     tier `points-value` (the table you gave me, e.g. HT1 = 60, LT1 = 45) —
     and saves it to `dc_players.overall_points`
   - posts the Discord tier-up announcement, `@mention`-ing the player's
     Discord account if they've linked it
   - works even if the player is offline, since it looks them up by their
     last-known username in the database rather than requiring them online
4. **`/verify`** (in-game) generates a one-time 6-character code. The player
   runs `/verify <code>` **on Discord** (a slash command, not a DM) within
   `discord.verify-code-expiry-minutes`. On success their Discord ID is
   saved 1:1 against their Minecraft UUID in `dc_discord_links`, and the
   `verified-role-id` role is applied if you set one.
5. **`/profile [player]`** or **`/stats [player]`** opens a GUI (works for
   offline players too) showing their overall rank, overall points, Discord
   link status, and every kit's tier + points contribution.
6. **PlaceholderAPI** placeholders (below) read straight from the database,
   so they work for offline players in things like join messages or
   scoreboard plugins, not just for the person currently online.

## Placeholders (needs PlaceholderAPI installed)

| Placeholder | Returns |
|---|---|
| `%duelcore_tier%` | The player's single highest tier across all kits (e.g. `HT1`), or `Untested` |
| `%duelcore_tier_<kit>%` | Tier in one specific kit — e.g. `%duelcore_tier_crystal%`, `%duelcore_tier_sword%` |
| `%duelcore_overall%` | Overall leaderboard **rank position** (e.g. `3`), or `Unranked` |
| `%duelcore_points%` | Total points — sum of every kit's tier points-value |
| `%duelcore_point_<kit>%` | Points contributed by one specific kit's tier — e.g. `%duelcore_point_uhc%` |

`<kit>` must match a key under `kits:` in `config.yml` (e.g. `sword`, `mace`,
`crystal`, `end-game`, `spear`, `diamond-smp`, `axe-shield`, `early-game`,
`late-game`, `bow`, `pot`). Add more kits there and the placeholders pick
them up automatically — nothing else needs to change.

## Tier points table (currently in config.yml, fully editable)

| Tier | Points value |
|---|---|
| HT1 | 60 |
| LT1 | 45 |
| HT2 | 30 |
| LT2 | 20 |
| HT3 | 10 |
| LT3 | 6 |
| HT4 | 4 |
| LT4 | 3 |
| HT5 | 2 |
| LT5 | 1 |

This lives under `tiers.definitions.<TIER>.points-value` in `config.yml` —
change the numbers, add tiers, or rename them and `/duelcoreadmin reload`
picks it up immediately (no restart needed).

## New commands in this batch

| Command | Who | Does |
|---|---|---|
| `/result <player> <gamemode> <tier>` | `duelcore.admin` | Sets a player's tier for a kit. Tab-completes online players, kit ids, and tier ids. |
| `/profile [player]` | anyone | Opens the stats GUI for yourself or another player |
| `/stats [player]` | anyone | Alias of `/profile` |

## About the exact 3D profile screen from your screenshot

The `/profile` GUI I built is a **standard inventory screen** with the same
information (career overview, per-kit tier + points, Discord link status).
Your screenshot's version is different under the hood — it's a full 3D scene
(an armor stand + floating item/text display entities arranged like a museum,
with arrows to page between kit categories) rather than a chest GUI. That's
a legitimately separate, heavier system to build (positioning Display
entities in world space per-viewer, camera framing, page-turning). I can
build that as a dedicated next batch if you want pixel parity — just say so
and I'll start on it once the GUI batch (queue/duel/party/leaderboard menus)
is done, since those are more central to actually running duels.

---

# DuelCore — Batch 1: Foundation

This is the **foundation layer** of the plugin. It's a real, working Paper
plugin skeleton — it compiles, connects to MySQL, gives out the hotbar,
and has a fully working Discord `/verify` linking flow with tier-up
announcements. The GUI menus (queue, duel, party, kit editor, trims,
leaderboard, settings) are the next batch, built on top of this.

## What's in this batch

- **Gradle project** (`build.gradle.kts`) targeting Paper 26.2 (api-version `1.21`),
  shading JDA + HikariCP + MySQL connector into the jar with relocation, so it
  won't clash with other plugins on your server.
- **config.yml** — every piece of text uses MiniMessage, so hex colors work
  everywhere: `<#ff5555>`, `<color:#ff5555>`, and gradients `<gradient:#ff0000:#ffff00>`.
  This includes the hotbar item names/lore, kit names, tier display names, GUI
  titles, and Discord messages.
- **MySQL schema** (`schema.sql`, auto-run on first start) — players, per-kit
  tier/points, kit loadouts, trim unlocks, parties, and Discord links.
- **Hotbar system** matching your screenshot exactly (Diamond Sword = Queue,
  Stone Sword = Duel, Sign = Leaderboard, Cake = Party, Book = Settings,
  Crafting Table = Kit Editor), fully re-orderable/re-themable from config.
- **Tier calculation** (`TierService`) — resolves a player's tier from their
  kit points against the thresholds in config, and on rank-up:
  - persists the new tier
  - fires the Discord tier-up announcement (with `@mention` if linked)
- **Discord bot** (JDA, runs inside the plugin process):
  - `/verify <code>` on Discord, paired with `/verify` in-game which
    generates a 6-character single-use code (expires after
    `discord.verify-code-expiry-minutes`)
  - Links are 1-to-1 (one MC account ↔ one Discord account) and persisted
    in `dc_discord_links`
  - Successful link applies a "verified" role if you set `verified-role-id`
  - Tier-up announcements post to `tier-announce-channel-id`, and mention
    the player's Discord account if they're linked, otherwise it posts
    without a mention
  - `/profile` slash command is registered but replies "coming soon" until
    the leaderboard batch lands (so it's already usable, just not filled in)

## Setup

1. Install **Java 25** (required by Paper 26.1+) and have a MySQL 8+ server
   reachable from every backend server in your network.
2. Build: `./gradlew shadowJar` → the output jar is in `build/libs/`.
3. Drop the jar in `plugins/` on **every backend server** in your proxy
   network. Set the **same** `database.host/name/username/password`
   on every server so they share data — but give each server a unique
   `database.server-id` in `config.yml`.
4. Only enable `discord.enabled: true` **and** fill in the bot token /
   guild id / channel id / role id on **one** server (running two bots on
   the same token will conflict). If you'd rather run the bot as its own
   process, tell me and I'll split it out — right now it's built into the
   plugin per your choice.
5. Create a Discord bot at https://discord.com/developers/applications,
   invite it with the `applications.commands` and `bot` scopes
   (permissions: Send Messages, Manage Roles if using verified-role),
   and enable the **Server Members Intent** in the bot's settings tab
   (required for role-assignment).
6. Start the server once — it'll create all the tables automatically.

## Commands so far

| Command | Does |
|---|---|
| `/verify` | Generates your Discord-linking code |
| `/duelcoreadmin reload` | Reloads config.yml and rebuilds the hotbar |

`/queue`, `/duel`, `/party`, `/leaderboard`, `/kiteditor`, `/duelsettings`
are already declared in `plugin.yml` and already open from the hotbar via
`HotbarListener` — they just need their GUI executors, which is the next
batch, built straight off your screenshots (kit categories, tier trim
unlock lists, party creation, the settings tabs, and the filterable
Top-10 leaderboard with country/client filters).

## Notes / things to confirm before I build the next batch

- **Currency source**: right now `currency` just sits on the player row
  (starts at `currency.starting-balance`, no way to earn it yet). Do you
  want it earned from duel wins, kills, tier-ups, all of the above?
- **Points source**: `TierService.applyPoints(...)` is ready to be called,
  but nothing calls it yet — that happens once the actual duel/tier-test
  match logic exists. Is a "tier test" a 1v1 against another player queued
  for the same kit, or against a bot/algorithm, or best-of-N series?
- I couldn't compile this in my sandbox (no access to Maven Central/PaperMC's
  repo from here), so please run `./gradlew build` on your end and send me
  any errors — I'll fix them immediately.

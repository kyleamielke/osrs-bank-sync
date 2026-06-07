# Design: osrs-bank-sync

A RuneLite plugin that captures the player's bank contents and POSTs them to a self-hosted endpoint — structurally modelled on `weirdgloop/WikiSync`, wire-compatible with the future `osrs-tracker` v0.2 bank ingestion route.

## Problem

kyleamielke runs (or will run) a self-hosted TempleOSRS clone called `osrs-tracker` (FastAPI + Postgres + React, separate Copilot session, lives as orphan branch in `kyleamielke/tools`, eventual surface at `osrs.kyil.io`). The hiscores API can only ever provide skill XP and activity KC — it cannot expose bank contents. To get bank wealth/composition data into the tracker, the only viable source is the RuneLite client itself, which can read the player's `BANK` `ItemContainer` at runtime.

This project is the RuneLite-side half of that integration: a small Plugin Hub plugin that captures bank snapshots and POSTs them to a user-configurable URL. The server side (a new `bank_snapshots` table + ingestion route in `osrs-tracker`) is **not** in scope here, but the HTTP contract defined below is intended to become the spec that `osrs-tracker` v0.2 implements verbatim. To unblock development today, the repo ships a small FastAPI stub server (mirroring WikiSync's `stub_server.py`).

## Success criteria

- `./gradlew build` from `~/osrs-bank-sync/` passes on JDK 11+ (Debian 13's `default-jdk` = OpenJDK 21 compiling to `--release 11`).
- Sideloading the built JAR into RuneLite shows "OSRS Bank Sync" in the plugin list with the icon and description from `runelite-plugin.properties`.
- With the bundled `stub_server/` running on `127.0.0.1:8484` and the plugin pointed at it, opening and closing the bank in-game causes exactly one `POST /api/v1/sync/bank` request to land at the stub, with a JSON body that validates against the schema in §Interfaces & contracts.
- The "Sync bank" widget button added to the bank interface manually triggers a submission (independent of the auto-trigger), printing a chat confirmation.
- Submissions are idempotent: the plugin emits a fresh UUID `snapshot_id` per submission and the server dedupes by `(account_hash, snapshot_id)`. The plugin itself suppresses an `AUTO_ON_CLOSE` submission whose serialized payload (minus the per-submission `snapshot_id` and `captured_at` fields) is identical to the previous successful one.
- CI (`ci.yml`) is green on every push/PR to the `osrs-bank-sync` branch: Java build+test, Python ruff+mypy on `stub_server/`, shellcheck on any shell scripts, all action SHAs pinned.
- Plugin Hub manifest PR (against `runelite/plugin-hub`) is submittable from a public mirror repo and CI-passable.

## Assumptions

- The RuneLite `client` Maven artifact at `latest.release` continues to expose `ItemContainer`, `net.runelite.api.InventoryID.BANK`, `WidgetClosed.isUnload()`, `ItemContainerChanged`, `client.getAccountHash()`, `client.getAccountType()`, and `net.runelite.api.widgets.InterfaceID.BANK` (all present and load-bearing in the current API; see Architecture for citations).
- Debian 13 host has `default-jdk` (OpenJDK 21). Source/target is Java 11 per Plugin Hub convention (matching WikiSync's `options.release.set(11)`).
- `osrs-tracker` v0.2 will add bank ingestion *after* this plugin lands; until then, the stub server is the only consumer.
- Player consents to bank data leaving their client (same trust model as WikiSync, documented bluntly as a `warning=` line on the Plugin Hub manifest).
- The user runs at most a handful of accounts (own + alts), so we don't need rate limiting on the client; a single submission is small (≤ ~16 KB JSON for a full 800-item bank without names/prices).
- `kyleamielke/tools` is private; Plugin Hub CI cannot clone it. A separate public mirror repo is acceptable to kyleamielke (called out as an irreversible decision below).

## Constraints

**Technical:**
- Plugin Hub schema: `repository=<url>` + `commit=<sha>` only — no branch selector. Source repo must be public.
- Plugin Hub builds plugins with `--release 11`; cannot use Java 17+ language features.
- Only "free" runtime dependencies allowed: anything transitive to `runelite-client` (Gson, OkHttp, Guice, Lombok, SLF4J) is free; any additional dep requires Gradle dependency-verification SHAs in the plugin-hub PR. **We add zero new runtime deps.**
- Bank `ItemContainer` reads and all other `client.*` calls MUST happen on the RuneLite client thread (via `clientThread.invokeLater` or inside an `@Subscribe` handler). HTTP submission MUST happen off the client thread (OkHttp `enqueue`). OkHttp callbacks MUST NOT touch `client.*` directly — they marshal back via `clientThread.invokeLater`. See §Threading rules.
- `runelite-plugin.properties` lives at repo root (not under `src/main/resources/`).
- `icon.png` lives at repo root, ≤ 48×72 px.

**Non-technical:**
- Matches `kyleamielke/tools` orphan-branch / worktree pattern.
- BSD 2-Clause license. Rationale: the Plugin Hub README does **not** mandate any specific license; it only requires that the source be publicly available and that the license permit redistribution. BSD 2-Clause matches what WikiSync (`weirdgloop/WikiSync`) ships and is comfortably within the permissive range that Plugin Hub reviewers expect — no policy claim about it being "recommended" is made.
- CI conventions follow `~/spotify-dj-bot/.github/workflows/ci.yml` (action SHA pinning, ruff `E F I UP B`, mypy `--strict`).
- Commit/PR conventions: kyleamielke noreply author + Copilot co-author trailer; PR-gated via `~/copilot-config/scripts/sync.sh`.

---

## Naming & irreversible decisions (committed)

| Item | Value | Reversibility |
|---|---|---|
| Project / branch / worktree slug | **`osrs-bank-sync`** | Irreversible once Plugin Hub manifest merges |
| Worktree path | **`~/osrs-bank-sync/`** | Reversible (local) |
| Plugin Hub manifest filename | **`plugins/osrs-bank-sync`** | Irreversible once merged |
| Display name (`displayName=`) | **`OSRS Bank Sync`** | Reversible via Plugin Hub PR |
| Java package | **`io.kyil.osrsbanksync`** | Irreversible (matches `kyil.io` domain) |
| `pluginMainClass` / `PluginDescriptor.name` | `io.kyil.osrsbanksync.OsrsBankSyncPlugin` / `"OSRS Bank Sync"` | Irreversible |
| Gradle `group` | **`io.kyil.osrsbanksync`** | Irreversible |
| License | **BSD 2-Clause "Simplified"** | Effectively irreversible after first external contribution |
| Config group key | **`osrsbanksync`** | Irreversible (RuneLite config namespace; rename = lost user settings) |
| Public mirror repo | **`github.com/kyleamielke/osrs-bank-sync`** (public, default branch `main`) | Reversible only by archive + rename |
| **HTTP path on receiver** | **`POST {baseUrl}/api/v1/sync/bank`** | Reversible only by versioned config + server-side support |
| **Wire format casing** | **`snake_case`** (Gson `FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES`) | Irreversible once external consumers exist |
| **Auth scheme** | **`Authorization: Bearer <opaque-token>`** (RFC 6750) | Reversible |
| **Idempotency key** | **`(account_hash, snapshot_id)`** where `snapshot_id` is a client-generated UUIDv4 | Irreversible at the DDL level |

The `/api/v1/` prefix:
- `/api/` matches the universal prefix used by `osrs-tracker` (see DESIGN-v2.md lines 478–650 — all v0.1 routes are registered under `APIRouter(prefix="/api")`).
- `/v1/` reserves room for breaking schema changes without forcing all existing plugin installations to a new path simultaneously.

---

## Options considered

### Option A — Capture and submit on **every** `ItemContainerChanged(BANK)`

- Shape: One `@Subscribe` handler; serialize and POST on every container update.
- Pros: Always up-to-date; trivial to implement.
- Cons: Bank reorganization triggers dozens of events per second; would hammer the receiver with near-duplicate snapshots. Easy to get IP-throttled if/when the receiver is on a tailnet or VPS.
- Cost: Low to build, high to operate.

### Option B — Capture-fresh on bank close + manual button + fallback on disconnect (chosen)

- Shape: Use `ItemContainerChanged(BANK)` only for **dirty-tracking** (was-it-changed-since-last-submit). When the bank widget closes (`WidgetClosed.groupId == InterfaceID.BANK && isUnload()`), re-read the container fresh on the client thread, build a snapshot, and submit it. Additionally mount a "Sync" button on the bank widget (WikiSync `SyncButtonManager` pattern) that forces an immediate submission. As a fallback for "user logged out without closing the bank", a `GameStateChanged` handler submits any dirty cached snapshot on `HOPPING` / `CONNECTION_LOST` / `LOGIN_SCREEN`. Skip an automatic submission if the serialized payload (minus the per-submission `snapshot_id` and `captured_at` fields) is identical to the previous successful submission.
- Pros: One submission per natural user action. Source-of-truth for the wire payload is always a fresh `client.getItemContainer(InventoryID.BANK)` read, not a possibly-stale cache. Manual button covers "I left the bank open all session." Disconnect fallback covers crash/hop/AFK-logout cases. Minimal traffic; meaningful state.
- Cons: Three trigger paths (close / button / game-state) instead of one — mitigated by all three funneling through a single `submit()` method.
- Cost: Moderate.

### Option C — Manual button only (WikiSync-style)

- Shape: Drop auto-trigger entirely; require button click.
- Pros: Simplest; user is always in control.
- Cons: User forgets. Tracker data goes stale silently. The whole point is automation.
- Cost: Low to build, defeats the user's stated intent.

### Option D — Debounced periodic submit (every N seconds while bank is open)

- Shape: Timer fires every N seconds; if BANK container has changed since last submit, POST.
- Pros: Bounded request rate; eventually-consistent.
- Cons: Half-states (mid-reorganize) get captured. More config knobs.
- Cost: Moderate.

## Decision

**Chosen: Option B** — capture-fresh on bank close, plus manual sync button, plus disconnect fallback, plus client-side payload dedupe (computed against the payload-with-volatile-fields-stripped). It produces one submission per intentional user action, mirrors the trust model and UX pattern of WikiSync, and degrades gracefully via the manual button and the game-state fallback. The `submitMode` config exposes `AUTO_ON_CLOSE` / `MANUAL_ONLY` / `OFF` so a paranoid user can disable auto-trigger without uninstalling.

---

## Architecture

### Repo layout (mirrors `weirdgloop/WikiSync` structurally, adapted to kyleamielke conventions)

```
~/osrs-bank-sync/                              (worktree on orphan branch osrs-bank-sync)
├── .github/workflows/ci.yml                   Java build + Python lint + shellcheck
├── .gitignore                                 RuneLite/Gradle/Python/IDE
├── CHANGELOG.md                               Keep-a-changelog
├── DESIGN.md                                  This document
├── LICENSE                                    BSD 2-Clause
├── README.md                                  Quickstart + screenshots + stub-server docs
├── build.gradle                               Java 11, RuneLite latest.release, zero extra runtime deps
├── settings.gradle                            rootProject.name = 'osrs-bank-sync'
├── gradle/wrapper/                            Pinned Gradle 8.x wrapper (jar checksum-verified)
├── gradlew, gradlew.bat                       Wrapper scripts
├── icon.png                                   ≤ 48×72 px, Plugin Hub icon
├── runelite-plugin.properties                 Manifest metadata at repo root (NOT under resources)
├── src/main/java/io/kyil/osrsbanksync/
│   ├── OsrsBankSyncPlugin.java                @PluginDescriptor, event subscribers, orchestration
│   ├── OsrsBankSyncConfig.java                @ConfigGroup("osrsbanksync") config interface
│   ├── BankSnapshot.java                      POJO: accountHash, displayName, accountType,
│   │                                          capturedAt, pluginVersion, snapshotId, items[]
│   ├── BankItem.java                          POJO: slot, itemId, quantity
│   ├── BankCaptureService.java                Reads ItemContainer on client thread → BankSnapshot
│   ├── BankSubmitter.java                     OkHttp wrapper: buildRequest(), enqueue(), dedupe-by-hash
│   ├── BankSyncButton.java                    Mounts/unmounts the "Sync" button on the bank widget
│   └── AccountTypeMapper.java                 Maps net.runelite.api.vars.AccountType → wire string
├── src/main/resources/                        (empty in v1; reserved for sound/icon assets)
├── src/test/java/io/kyil/osrsbanksync/
│   ├── BankSnapshotSerializationTest.java     Gson round-trip; snake_case field assertion
│   ├── BankSubmitterTest.java                 OkHttp MockWebServer: 200, 401, dedupe, force
│   └── AccountTypeMapperTest.java             Enum coverage including null input
└── stub_server/                               Self-contained FastAPI receiver for plugin dev
    ├── pyproject.toml                         Hatchling, ruff E F I UP B, mypy --strict, python 3.12
    ├── README.md                              `uvicorn osrs_bank_sync_stub.app:app --port 8484`
    └── src/osrs_bank_sync_stub/
        ├── __init__.py
        ├── app.py                             FastAPI app: POST /api/v1/sync/bank, GET /healthz
        └── schemas.py                         Pydantic snake_case mirror of BankSnapshot
```

### Public mirror for Plugin Hub distribution

```
github.com/kyleamielke/osrs-bank-sync   PUBLIC, default branch `main`
  └── snapshot of the orphan branch from kyleamielke/tools, force-pushed
      manually (or via a one-line script) at each release SHA
```

The orphan branch in `tools` remains the canonical dev location and Copilot's working tree; the mirror exists solely so the Plugin Hub `repository=` URL resolves. Release ritual is a 3-line `scripts/release.sh` (Phase 5) that:
1. Verifies the working tree is clean and tagged `vX.Y.Z`.
2. `git push <mirror> osrs-bank-sync:main --force-with-lease`.
3. Prints the SHA to paste into `plugins/osrs-bank-sync` in the plugin-hub fork.

### Runtime data flow

```
RuneLite event loop                        BankCaptureService              BankSubmitter
─────────────────────                      ─────────────────               ─────────────

ItemContainerChanged(BANK) ──► onContainerChanged()
                                  lastCapturedSnapshot =
                                    captureService.captureFrom(event.getItemContainer())
                                  dirty.set(true)   (client thread; no submit)

WidgetClosed{groupId=BANK,
             isUnload=true}  ──► onBankClosed()
   if mode==AUTO_ON_CLOSE
   && dirty.get()               captureNow() (fresh read of
                                client.getItemContainer(InventoryID.BANK))
                                                          │
                                                          ▼
                                                  submitter.submit(snap, force=false)
                                                          │ hash(payload − {snapshot_id,
                                                          │                captured_at})
                                                          │ == lastHash ? skip
                                                          │
                                                          │ OkHttp.enqueue()
                                                          ▼
                                                  POST {baseUrl}/api/v1/sync/bank
                                                  Headers: Content-Type, User-Agent,
                                                           Authorization?

GameStateChanged{HOPPING,
                 CONNECTION_LOST,
                 LOGIN_SCREEN}  ──► onGameStateChanged()
   if mode==AUTO_ON_CLOSE
   && dirty.get()               snap = lastCapturedSnapshot   (already-built POJO,
                                                               kept fresh by
                                                               onContainerChanged;
                                                               no client.* read needed)
                                if snap != null               submitter.submit(snap, force=false)
                                else                          log.debug skip; nothing to send

Bank "Sync" button click   ──► onSyncButtonClicked()
                                captureNow()              │
                                                          ▼
                                                  submitter.submit(snap, force=true)
                                                  (bypass dedupe)
```

The `dirty` flag is an `AtomicBoolean`. It is **reset to `false` only on submission outcomes that mean "no point retrying this payload":** HTTP `200`/`204` (success) and HTTP `4xx` (terminal client-side failure — malformed payload, bad token). It is **preserved (`dirty=true` remains)** on `5xx`/network errors and on the client-side dedupe skip, so the next trigger (`WidgetClosed`, `GameStateChanged`, or the next bank change followed by close) naturally retries. Reset happens on the OkHttp dispatcher thread via `dirty.set(false)` — safe because `AtomicBoolean` is thread-safe and the flag's only other writers (the `@Subscribe` handlers) run on the client thread.

Both `lastCapturedSnapshot` (reference) and `dirty` (boolean) are package-private fields on the plugin class. `lastCapturedSnapshot` uses `volatile` (single-writer thread for the reference: the client thread on container-change events; only-readers off-thread); `dirty` uses `AtomicBoolean` as described above.

### Threading rules

These are the invariants — every change to this codebase must preserve all four.

1. **All `client.*` and `clientThread.*` reads happen on the client thread.** This is already true inside `@Subscribe` handlers (`ItemContainerChanged`, `WidgetClosed`, `GameStateChanged`) and inside any `clientThread.invokeLater(() -> ...)` block. The bank "Sync" button's click handler is invoked by RuneLite on the client thread, so its `captureService.captureNow()` call is safe directly.

2. **OkHttp `enqueue()` is fire-and-forget off the client thread.** The `BankSubmitter` builds the `Request` and the JSON body synchronously on whatever thread called `submit()` (always the client thread today; safe because Gson serialization and POJO construction touch no `client.*` state), then hands off via `enqueue()` to the OkHttp dispatcher.

3. **OkHttp `onResponse`/`onFailure` callbacks run on OkHttp dispatcher threads.** They MUST NOT touch `client.*` directly. Specifically:
   - To call `client.addChatMessage(...)` from a callback, the submitter wraps it in `clientThread.invokeLater(() -> client.addChatMessage(...))`.
   - Updating `lastSuccessfulPayloadHash` does **not** touch `client.*`, so it can happen on the callback thread, but it crosses a thread boundary with the submission thread (which read the previous value to decide whether to skip). Therefore: `lastSuccessfulPayloadHash` is an `AtomicReference<String>` (initialized to `null`; updated via `set(...)` on success). Read-and-compare on submit is `Objects.equals(currentHash, lastSuccessfulPayloadHash.get())`.
   - Logging via SLF4J is thread-safe; no marshalling needed.

4. **POJO construction and JSON serialization are pure.** `BankSnapshot` and `BankItem` are immutable value types containing no references to RuneLite state. Gson serialization is thread-safe.

(v1 contradicted itself between the "HTTP is fire-and-forget, response handling logs to SLF4J only, never touches client state" rule and Phase 3.2's acceptance criterion of "chat message via `client.addChatMessage` on success." The marshalling-back-via-`clientThread.invokeLater` rule above resolves that contradiction.)

### Security posture

- **Auth header value is NEVER logged.** SLF4J calls log header *names* only (e.g., `log.debug("Sending request with headers: {}", headers.names())`), never their values. There is no log statement anywhere in the codebase that takes `config.authToken()` as a `{}` argument.
- **Response body truncation:** on 4xx, the response body is read up to 4 KB, then truncated to **200 chars** before being written to `log.warn`, and further truncated to **80 chars** before being sent to in-game chat via `client.addChatMessage`.
- **URL hygiene at config-save time:** when the user edits `targetUrl`, the plugin parses it (`okhttp3.HttpUrl.parse`) and rejects (logs a warning, prints a chat message, does NOT submit) any URL where:
  - `username` or `password` portion is non-empty (`user:pass@host` form), OR
  - `query` portion is non-empty (any `?...` segment).
  The plugin appends `/api/v1/sync/bank` to the base URL itself; the user is not expected to include a path beyond the base host/port and (optionally) a path prefix.
- **Plaintext-non-loopback warning:** if `targetUrl.scheme == "http"` AND `targetUrl.host` is not one of `127.0.0.1`, `localhost`, `[::1]`, the plugin logs **once per session** (gated by a boolean): `"Bank sync is sending data over plaintext HTTP to a non-loopback host."` and emits the same to chat. Switching the URL re-arms the warning.
- **Token secret-flag:** the `authToken` config field uses `@ConfigItem(secret = true)` so the RuneLite UI masks it. The README explicitly says "treat this like a password; never paste it into bug reports."

### Why no new runtime dependencies

- **Gson** — provided by RuneLite, injected via `@Inject Gson gson`. We construct a derived Gson instance configured with `setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)` for wire serialization — see §Data model.
- **OkHttp** — provided by RuneLite, injected via `@Inject OkHttpClient okHttpClient`.
- **Guice / Lombok / SLF4J** — provided by RuneLite.

This means the Plugin Hub PR does NOT need to touch `package/verification-template/build.gradle` (per plugin-hub README: dep verification only required for non-transitive runtime deps). Reviewer-friendly, fast merge.

### RuneLite API symbols referenced (verified against `runelite/runelite` master)

| Symbol | Fully-qualified name | Notes |
|---|---|---|
| Bank container ID | `net.runelite.api.InventoryID.BANK` | Enum constant used in `client.getItemContainer(InventoryID.BANK)`; use `.getId()` for `ItemContainerChanged` integer comparisons. |
| Bank interface group ID | `net.runelite.api.widgets.InterfaceID.BANK` | Integer group ID for the bank widget. |
| Widget-closed unload check | `event.getGroupId() == InterfaceID.BANK && event.isUnload()` | Pattern lifted verbatim from `runelite/runelite` `TabInterface.java` and `PotionStorage.java`. `isUnload()` is necessary to avoid firing on intermediate widget reloads/redraws. |
| Account hash | `client.getAccountHash()` | Returns `long`; `-1L` when not logged in. |
| Account type | `client.getAccountType()` — returns `net.runelite.api.vars.AccountType` | `@Deprecated` (the deprecation tag refers to "see Varbits#ACCOUNT_TYPE" — i.e., Jagex semantics may shift and Varbit reads are now considered the canonical source). We use the deprecated accessor anyway for v1 because (a) it returns an enum, eliminating an entire class of mapping bugs, and (b) RuneLite has kept it functional through the deprecation. Fallback if the method is ever removed: switch to `client.getVarbitValue(Varbits.ACCOUNT_TYPE)` and map the int manually using the same `AccountTypeMapper` indices as the enum ordinals. |
| Game state | `net.runelite.api.GameState.{HOPPING,CONNECTION_LOST,LOGIN_SCREEN,LOGGED_IN}` | Used in `onGameStateChanged` fallback. |
| Item container changed | `net.runelite.api.events.ItemContainerChanged` with `event.getContainerId() == InventoryID.BANK.getId()` | For dirty-flag updates and `lastCapturedSnapshot` refresh. |

`net.runelite.api.vars.AccountType` (as of current `runelite/runelite` master) exposes exactly six values: `NORMAL`, `IRONMAN`, `ULTIMATE_IRONMAN`, `HARDCORE_IRONMAN`, `GROUP_IRONMAN`, `HARDCORE_GROUP_IRONMAN`. The wire `account_type` enum is narrowed to **exactly these six strings plus null** — v1 of this design listed `UNRANKED_GROUP_IRONMAN` which does not exist in the RuneLite API; it has been removed.

---

## Data model

### Java POJO (`BankSnapshot.java`, serialized with Gson)

```java
public final class BankSnapshot {
    long          accountHash;     // client.getAccountHash(); never sent if -1
    String        displayName;     // client.getLocalPlayer().getName(); nullable
    String        accountType;     // AccountTypeMapper output; nullable
    String        capturedAt;      // Instant.now() ISO-8601 UTC, e.g. "2025-01-15T22:31:04Z"
    String        pluginVersion;   // hardcoded constant matching build.gradle version
    String        snapshotId;      // UUID.randomUUID().toString(); fresh per submission
    List<BankItem> items;          // never null; empty list if bank empty
}

public final class BankItem {
    int slot;       // index in BANK ItemContainer, 0-based
    int itemId;     // Item.getId(); slots with id <= 0 are omitted from the list
    int quantity;   // Item.getQuantity(); placeholders (quantity == 0) are omitted
}
```

Fields are Java camelCase by convention. **Wire serialization is snake_case via a single Gson configuration:**

```java
// In OsrsBankSyncPlugin (or a small Gson provider) — constructed once, reused per submit:
Gson wireGson = injectedGson.newBuilder()
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .serializeNulls()  // displayName / accountType are nullable; server expects explicit nulls
    .create();
```

No `@SerializedName` annotations are required; the naming policy handles every field uniformly, which keeps POJO and wire in lockstep (no risk of forgetting an annotation when adding a field).

### Wire format example

```json
{
  "account_hash":   1234567890123456789,
  "display_name":   "Zezima",
  "account_type":   "IRONMAN",
  "captured_at":    "2025-01-15T22:31:04Z",
  "plugin_version": "0.1.0",
  "snapshot_id":    "550e8400-e29b-41d4-a716-446655440000",
  "items": [
    { "slot": 0, "item_id": 995,  "quantity": 2147483647 },
    { "slot": 1, "item_id": 4151, "quantity": 1 }
  ]
}
```

### Field reference

| Wire field | Type | Nullable | Notes |
|---|---|---|---|
| `account_hash` | int64 | No | `client.getAccountHash()`. Submissions where this is `-1` are NEVER sent. |
| `display_name` | string | Yes | May be null on first tick after login. |
| `account_type` | string | Yes | One of `NORMAL`, `IRONMAN`, `ULTIMATE_IRONMAN`, `HARDCORE_IRONMAN`, `GROUP_IRONMAN`, `HARDCORE_GROUP_IRONMAN`. Null if the RuneLite API returns null. |
| `captured_at` | string (ISO-8601 UTC, second precision, `Z` suffix) | No | Client clock at capture time. **Not** part of the idempotency key — server uses it only for chronological ordering. |
| `plugin_version` | string | No | Semver, baked from `build.gradle`. |
| `snapshot_id` | string (UUIDv4) | No | `UUID.randomUUID().toString()` generated per submission. Idempotency key with `account_hash`. |
| `items` | array | No | May be empty. Each entry: `slot` (≥0), `item_id` (≥1), `quantity` (≥1). Empty slots and placeholders omitted. |

### Server-side mirror (`stub_server/src/osrs_bank_sync_stub/schemas.py`)

Because the wire is already snake_case, the Pydantic models need **no aliases** — they map field-for-field:

```python
from datetime import datetime
from uuid import UUID
from pydantic import BaseModel, Field

class BankItem(BaseModel):
    slot:     int = Field(ge=0)
    item_id:  int = Field(ge=1)
    quantity: int = Field(ge=1)

class BankSnapshot(BaseModel):
    account_hash:   int
    display_name:   str | None = None
    account_type:   str | None = None
    captured_at:    datetime
    plugin_version: str
    snapshot_id:    UUID
    items:          list[BankItem]
```

### Future `osrs-tracker` v0.2 DDL (NOT in this project — documented here for cross-project clarity)

```sql
CREATE TABLE bank_snapshots (
    id             BIGSERIAL    PRIMARY KEY,
    account_hash   BIGINT       NOT NULL,
    snapshot_id    UUID         NOT NULL,
    player_id      INTEGER          NULL REFERENCES players(id) ON DELETE SET NULL,
    display_name   TEXT             NULL,
    account_type   TEXT             NULL,
    captured_at    TIMESTAMPTZ  NOT NULL,
    received_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    plugin_version TEXT         NOT NULL,
    raw            JSONB        NOT NULL,
    CONSTRAINT bank_snapshots_account_snapshot_unique UNIQUE (account_hash, snapshot_id)
);
CREATE INDEX bank_snapshots_account_captured_idx
    ON bank_snapshots (account_hash, captured_at DESC);

CREATE TABLE bank_snapshot_items (
    snapshot_pk  BIGINT  NOT NULL REFERENCES bank_snapshots(id) ON DELETE CASCADE,
    slot         INTEGER NOT NULL,
    item_id      INTEGER NOT NULL,
    quantity     INTEGER NOT NULL,
    PRIMARY KEY (snapshot_pk, slot)
);
```

Notes for the future ingestion route:
- The UNIQUE constraint on `(account_hash, snapshot_id)` is the idempotency key. A repeated POST with the same `snapshot_id` from the same `account_hash` returns the original 200/204 without re-inserting.
- `captured_at` is for chronological ordering and gain-window queries; **not** unique. Two submissions with the same `captured_at` second but different `snapshot_id`s are stored as separate rows.
- `player_id` is nullable because account-hash → player linkage is an `osrs-tracker` UI concern. The plugin sends no opinion on this.
- The route is `POST /api/v1/sync/bank` (matches `osrs-tracker`'s universal `/api/` prefix per DESIGN-v2.md lines 478–650, plus the new `/v1/` namespace).

### Excluded fields (with justification — committed)

| Field | Excluded because |
|---|---|
| Item name | Server can hydrate from item-id via OSRS Wiki or its own cache. Including it would ~3× the payload and bake stale names into the wire. |
| GE price | Server side concern: `osrs-tracker` v0.2 will pull from the OSRS Wiki real-time prices API. |
| Tab structure / tab names | Frequently changes; not part of "what items do I own." |
| Placeholder items (`Item.quantity == 0`) | Filtered out at capture; UI affordances, not inventory. |
| Search-state / current tab / interface mode | Pure UI state. |
| World, world type, game mode beyond account type | Not meaningful for a bank inventory. |
| Inventory, equipment, looting bag, seed vault | v1 is bank-only; additive in future via new top-level arrays (server treats missing arrays as empty). |

---

## Interfaces & contracts

### HTTP contract (this is the spec `osrs-tracker` v0.2 will implement)

**Endpoint:** `POST {targetUrl}/api/v1/sync/bank`

The plugin appends the path; `targetUrl` is base only (scheme + host + port).

**Request headers:**

| Header | Value | Required |
|---|---|---|
| `Content-Type` | `application/json; charset=utf-8` | Yes |
| `User-Agent` | `osrs-bank-sync/<version> (+https://github.com/kyleamielke/osrs-bank-sync)` | Yes |
| `Authorization` | `Bearer <token>` (token from `authToken` config) | Only if `authToken` is non-empty |

**Auth token semantics:** the token is **opaque** to the plugin. It can be a static API key, an OAuth 2 access token, a JWT — the plugin does not parse it, validate it, or refresh it. RFC 6750 `Authorization: Bearer` was chosen over `X-API-Key` because RFC 6648 deprecates custom `X-` prefix headers, and Bearer is supported natively by virtually every HTTP framework's auth middleware.

**Request body:** JSON per §Data model "Wire format example" above.

**Response codes:**

| Code | Meaning | Plugin behavior |
|---|---|---|
| `200 OK` / `204 No Content` (body optional, ignored) | Accepted | Log debug; update `lastSuccessfulPayloadHash` via `AtomicReference.set(...)`; `dirty.set(false)`. If `showChatConfirmations`, marshal back to the client thread and post "Bank synced." |
| `400 Bad Request` | Malformed payload (server-side schema mismatch) | Log warning with response body (truncated 200 chars); marshal back to client thread to chat-message a truncated (80 chars) "Bank sync rejected: ..."; `dirty.set(false)` (terminal — payload would re-fail). |
| `401 Unauthorized` | Bearer token missing/invalid | Log warning (no token value); chat "Bank sync auth failed — check token in config." `dirty.set(false)` (terminal until config change). |
| `5xx`, network error | Server transient failure | Log debug; no chat spam; leave `dirty=true` so the next bank-close / hop / sync-button click retries naturally. |

**Idempotency:**
- Server dedupes by `(account_hash, snapshot_id)` (UNIQUE constraint). A repeated POST with the same key returns the original status without side effects.
- Plugin-side dedupe (separate purpose: bandwidth/spam suppression, not correctness): keep `lastSuccessfulPayloadHash` as an `AtomicReference<String>`. On `submit(snap, force=false)`, compute `SHA-256(payload-minus-snapshot_id-minus-captured_at)` and skip if it equals the stored hash. Both `snapshot_id` and `captured_at` are stripped from the hash input because both vary on every submission; including them would defeat dedupe entirely. `force=true` (manual button) bypasses this check.

**OkHttp timeouts:** distinguished per phase to fail fast at the right granularity.

| Knob | Value | Rationale |
|---|---|---|
| `connectTimeout` | 3 s | Localhost / tailnet round-trips are sub-ms; 3 s catches "server down" without making the user wait. |
| `writeTimeout` | 5 s | Payload is ≤ ~16 KB; 5 s tolerates slow tailnet uploads. |
| `readTimeout` | 5 s | We expect 200/204 with little or no body; 5 s tolerates a stalled receiver. |
| `Call.timeout` | 10 s | Total upper bound (sum of phases + DNS + TLS). One attempt; no retries. |

### RuneLite config interface (`OsrsBankSyncConfig.java`)

```java
@ConfigGroup("osrsbanksync")
public interface OsrsBankSyncConfig extends Config {
    @ConfigItem(keyName = "targetUrl", name = "Target URL", position = 0,
                description = "Base URL of the receiver. POSTs go to {targetUrl}/api/v1/sync/bank.")
    default String targetUrl() { return "http://127.0.0.1:8484"; }

    @ConfigItem(keyName = "authToken", name = "Auth token", position = 1, secret = true,
                description = "Sent as Authorization: Bearer <token>. Leave empty to disable. "
                            + "Token is opaque — static key, OAuth, or JWT all work.")
    default String authToken() { return ""; }

    @ConfigItem(keyName = "submitMode", name = "Submit mode", position = 2,
                description = "When to submit bank snapshots.")
    default SubmitMode submitMode() { return SubmitMode.AUTO_ON_CLOSE; }

    @ConfigItem(keyName = "includeBank", name = "Sync bank", position = 3,
                description = "Capture and submit bank contents. Reserved for future include flags.")
    default boolean includeBank() { return true; }

    @ConfigItem(keyName = "showChatConfirmations", name = "Show chat confirmations", position = 4,
                description = "Print a chat message on each successful submission.")
    default boolean showChatConfirmations() { return true; }

    enum SubmitMode { AUTO_ON_CLOSE, MANUAL_ONLY, OFF }
}
```

### `AccountTypeMapper` (exact code)

```java
import net.runelite.api.vars.AccountType;

final class AccountTypeMapper {
    private AccountTypeMapper() {}

    /** Returns the wire string, or null if the input is null. */
    static String toWire(AccountType type) {
        if (type == null) return null;
        switch (type) {
            case NORMAL:                 return "NORMAL";
            case IRONMAN:                return "IRONMAN";
            case ULTIMATE_IRONMAN:       return "ULTIMATE_IRONMAN";
            case HARDCORE_IRONMAN:       return "HARDCORE_IRONMAN";
            case GROUP_IRONMAN:          return "GROUP_IRONMAN";
            case HARDCORE_GROUP_IRONMAN: return "HARDCORE_GROUP_IRONMAN";
            default:                     return null;  // future-proof against new enum values
        }
    }
}
```

The `default: return null` branch ensures that if Jagex/RuneLite adds a new account type (e.g., a future seasonal variant) before this plugin updates, the plugin sends `null` rather than an invalid enum string. The server treats `null` as "unknown."

### Plugin Hub manifest (`runelite/plugin-hub:plugins/osrs-bank-sync`)

```
repository=https://github.com/kyleamielke/osrs-bank-sync.git
commit=<40-char SHA filled at release time>
warning=This plugin sends your full bank contents (item IDs and quantities), your display name, account hash, and account type to whatever server URL you configure. By default the URL is http://127.0.0.1:8484 (localhost only). If you change the URL to a remote server, your bank data will be sent there in plaintext unless the URL uses https://.
authors=kyleamielke
```

No `build=` line (matches WikiSync — defaults to `gradle` mode, preserves our `build.gradle`). No new runtime deps means we still don't trigger the dependency-verification flow.

### `runelite-plugin.properties` (at repo root)

```
displayName=OSRS Bank Sync
author=kyleamielke
support=https://github.com/kyleamielke/osrs-bank-sync/issues
description=Sync your bank contents to a self-hosted endpoint (e.g. osrs-tracker)
tags=bank,sync,tracker,wealth
plugins=io.kyil.osrsbanksync.OsrsBankSyncPlugin
```

---

## Execution plan

Each task = one PR; dependencies marked. Tasks within a phase are listed in dependency order. Total feature scope is identical to v1 — acceptance criteria have been re-scoped across phases per the critique.

### Phase 0 — Bootstrap

| # | Task | Files | Acceptance | Deps |
|---|---|---|---|---|
| 0.1 | Register branch in `copilot-config` tooling | `~/copilot-config/scripts/sync.sh` (append `osrs-bank-sync` to `STABLE_BRANCHES`); `~/copilot-config/scripts/install.sh` (add `default-jdk` to `APT_PACKAGES`, add worktree-add stanza for `osrs-bank-sync` → `~/osrs-bank-sync/`) | `osrs-bank-sync` accepted as target branch; `install.sh` on fresh box produces worktree and `java -version` exits 0 | — |
| 0.2 | Create public mirror repo | `github.com/kyleamielke/osrs-bank-sync` (PUBLIC, empty `main`, BSD-2 license, README pointing at canonical dev location) | `gh repo view kyleamielke/osrs-bank-sync` returns 200, repo is public | — |
| 0.3 | Create orphan branch + worktree, baseline commit | `~/osrs-bank-sync/`: `LICENSE` (BSD-2 filled), `.gitignore`, `README.md` skeleton, `CHANGELOG.md` (`[Unreleased]`), `DESIGN.md` (this file) | PR opened via `sync.sh`, Copilot review requested, merged | 0.1 |
| 0.4 | Branch protection | GitHub branch protection for `osrs-bank-sync` (PR required, status checks once 0.6 lands) | `gh api repos/kyleamielke/tools/branches/osrs-bank-sync/protection` returns protection config | 0.3 |

### Phase 1 — Java/Gradle skeleton (sideloadable empty plugin)

| # | Task | Files | Acceptance | Deps |
|---|---|---|---|---|
| 1.1 | Gradle wrapper + skeleton build | `gradle/wrapper/*` (Gradle 8.x; the wrapper jar is integrity-checked at runtime by Gradle itself against `gradle-wrapper.jar.sha256` in the wrapper distribution — this is the "wrapper-verification" mechanism, distinct from GitHub Actions SHA pinning which is handled separately in Phase 1.3), `gradlew`, `gradlew.bat`, `settings.gradle` (`rootProject.name = 'osrs-bank-sync'`), `build.gradle` (mirroring WikiSync: `mavenLocal()` + `https://repo.runelite.net` + `mavenCentral()`; `compileOnly net.runelite:client:latest.release`; Lombok compileOnly + annotationProcessor; `options.release.set(11)`; `group = 'io.kyil.osrsbanksync'`; `version = '0.1.0-SNAPSHOT'`; **no extra runtime deps**), `runelite-plugin.properties` (per §Interfaces), `icon.png` (48×72 placeholder) | `./gradlew build` succeeds locally; produces `build/libs/osrs-bank-sync-0.1.0-SNAPSHOT.jar` | 0.3 |
| 1.2 | Minimal plugin class that loads | `OsrsBankSyncPlugin.java` (`@PluginDescriptor(name="OSRS Bank Sync")`, empty `startUp`/`shutDown` with `log.info`); `OsrsBankSyncConfig.java` stub with `targetUrl` only; `@Provides` config method | Sideloading the JAR shows "OSRS Bank Sync"; toggling logs `startUp`/`shutDown` | 1.1 |
| 1.3 | CI workflow | `.github/workflows/ci.yml` — GitHub Actions pinned by commit SHA: `actions/checkout`, `actions/setup-java@v4` (temurin, Java 11), `gradle/actions/setup-gradle`; job runs `./gradlew build --no-daemon` | CI green on the PR | 1.1 |

### Phase 2 — Stub server (parallel with 1.2/1.3)

| # | Task | Files | Acceptance | Deps |
|---|---|---|---|---|
| 2.1 | FastAPI stub server | `stub_server/pyproject.toml` (hatchling, python 3.12, deps `fastapi`, `uvicorn[standard]`, `pydantic>=2`; dev `ruff`, `mypy`, `pytest`), `stub_server/src/osrs_bank_sync_stub/{__init__.py, app.py, schemas.py}`, `stub_server/README.md` | `uvicorn osrs_bank_sync_stub.app:app --port 8484` starts; `curl http://127.0.0.1:8484/healthz` → `{"status":"ok"}`; `curl -X POST -H 'Content-Type: application/json' -d @sample.json http://127.0.0.1:8484/api/v1/sync/bank` returns 200 and prints the parsed snake_case payload to stdout | 0.3 |
| 2.2 | Stub server CI lane | Extend `.github/workflows/ci.yml`: `python-lint` job — `actions/setup-python@v5` Python 3.12, `pip install -e stub_server[dev]`, `ruff check`, `mypy --strict stub_server/src`, `pytest stub_server` | CI green; matrix shows two jobs (`java-build`, `python-lint`) | 2.1, 1.3 |

### Phase 3 — Bank capture + submission (log-only, no chat, no auth)

Phase 3 is scoped to wire up the data path end-to-end with verbose log output and the default `submitMode = AUTO_ON_CLOSE`. **No chat messages, no auth header, no config UI beyond the defaults.** All user-visible polish lives in Phase 4.

| # | Task | Files | Acceptance | Deps |
|---|---|---|---|---|
| 3.1 | POJOs + capture service + account-type mapper | `BankSnapshot.java`, `BankItem.java`, `BankCaptureService.java` with two methods: `BankSnapshot captureNow()` — reads `client.getItemContainer(InventoryID.BANK)` on the client thread, then delegates to `captureFrom(container)`; `BankSnapshot captureFrom(ItemContainer container)` — pure-data: filters `quantity==0` and `itemId<=0`, sets `capturedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)`, generates `snapshotId = UUID.randomUUID().toString()`, reads `client.getAccountHash()`/`getLocalPlayer().getName()`/`getAccountType()` (all client-thread-safe; calls must originate from a client-thread context). `AccountTypeMapper.java` per §Interfaces. | Unit tests: `BankSnapshotSerializationTest` — `wireGson.toJson(fixture)` produces snake_case JSON matching golden file byte-for-byte. `AccountTypeMapperTest` — all six enum values + null mapped correctly. `BankCaptureServiceTest` — fed a fake `ItemContainer` via `captureFrom(...)`, asserts filter behavior. | 1.2 |
| 3.2 | OkHttp submitter (log-only) | `BankSubmitter.java`: `@Inject OkHttpClient`, `@Inject Gson` (rebuilt with snake_case policy at construction time); method `void submit(BankSnapshot snap, boolean force)`; `AtomicReference<String> lastSuccessfulPayloadHash`; OkHttp client configured with the four timeouts from §HTTP contract; headers per §Interfaces **excluding `Authorization`** (Phase 4); on success → log debug + `lastSuccessfulPayloadHash.set(...)`; on 4xx → log warning with truncated body; no `client.addChatMessage` calls anywhere | `BankSubmitterTest` with MockWebServer: (a) 200 → no error log, hash stored; (b) 401 → warning log only; (c) same payload twice with `force=false` (mock-injecting `UUID` and `Instant`) → second skipped after hash-minus-snapshot_id-minus-captured_at comparison; (d) `force=true` → always submits; (e) `connectTimeout` enforced by pointing at a black-hole address. **No assertion on chat lines — none are emitted in Phase 3.** | 3.1 |
| 3.3 | Wire up triggers in plugin class (default-config behavior only) | `OsrsBankSyncPlugin.java`: `AtomicBoolean dirty = new AtomicBoolean(false)`; `volatile BankSnapshot lastCapturedSnapshot` (the cache that keeps the fallback sourced); `@Subscribe onItemContainerChanged` (`event.getContainerId() == InventoryID.BANK.getId()` → `lastCapturedSnapshot = captureService.captureFrom(event.getItemContainer()); dirty.set(true)`); `@Subscribe onWidgetClosed` (`event.getGroupId() == InterfaceID.BANK && event.isUnload()` → if `dirty.get()`, gate on `client.getGameState() == LOGGED_IN` and `client.getAccountHash() != -1`, then call `captureService.captureNow()` and `submitter.submit(snap, false)` — `dirty` is cleared by the submitter on terminal outcomes per §HTTP contract); `@Subscribe onGameStateChanged` (on `HOPPING`/`CONNECTION_LOST`/`LOGIN_SCREEN`, if `dirty.get() && lastCapturedSnapshot != null`, submit `lastCapturedSnapshot` directly — do NOT call `captureNow()` because the container is no longer readable during these transitions; this path intentionally bypasses the LOGGED_IN gate). Assumes `submitMode = AUTO_ON_CLOSE` and `includeBank = true` (defaults); does NOT require config UI work. | Manual end-to-end with stub server running: open & close bank → exactly one POST hits stub with valid snake_case payload including a fresh UUID `snapshot_id`; close again with no changes → no POST (dedupe path exercised). Hop worlds with dirty bank → POST fires (fallback path). Stub server returning 503 → `dirty` stays true; next bank-close attempts again. | 3.1, 3.2, 2.1 |

### Phase 4 — Config UI + auth header + manual sync button + chat polish

| # | Task | Files | Acceptance | Deps |
|---|---|---|---|---|
| 4.1 | Full config interface + URL validation | Expand `OsrsBankSyncConfig.java` to all 5 fields (positions 0–4, `secret=true` on `authToken`); add a `validateTargetUrl()` helper called on config change that rejects userinfo and query strings per §Security posture, with chat + log warnings | Config panel shows all 5 fields; setting `submitMode = OFF` suppresses auto-submit; pasting `http://user:pass@host` produces a warning chat message and the plugin refuses to submit until the URL is fixed | 3.3 |
| 4.2 | Auth header wiring + plaintext-non-loopback warning | `BankSubmitter.java`: read `config.authToken()` per-request; add `Authorization: Bearer <token>` only when non-empty; ensure no log line takes the token as an argument. Add session-scoped flag that emits the plaintext-HTTP-to-non-loopback warning exactly once per session per `targetUrl` | MockWebServer tests: empty token → no header; non-empty → header present with exact value; debug log emits header *names* only. Pointing the plugin at `http://example.com` with logging captured fires the warning once, not on subsequent submits. | 3.2, 4.1 |
| 4.3 | Chat confirmations (marshalled back to client thread) | `BankSubmitter.java` callbacks: on 200/204 with `showChatConfirmations`, call `clientThread.invokeLater(() -> client.addChatMessage(...))` with "Bank synced." On 4xx, similarly marshal a truncated-to-80-chars rejection message | Manual test: with confirmations on, successful submit prints a chat line; with confirmations off, no chat line. Stub server intentionally returning 400 with a long body → chat shows the truncated rejection, log shows the 200-char truncated body. | 3.2, 4.1 |
| 4.4 | Sync button on bank widget | `BankSyncButton.java`: structurally adapt WikiSync's `SyncButtonManager` pattern, target the bank widget loaded with group `InterfaceID.BANK`. Button click runs on the client thread → `submitter.submit(captureService.captureNow(), true)` (force=true bypasses dedupe) | Open bank in dev client → "Sync" button visible; click → POST hits stub regardless of dedupe state; rapid double-click produces two POSTs with two distinct `snapshot_id` UUIDs (server dedupe is by snapshot_id; both rows would land if both are unique — that is correct and intended for `force=true`) | 3.3, 4.1 |

### Phase 5 — Polish + Plugin Hub submission

| # | Task | Files | Acceptance | Deps |
|---|---|---|---|---|
| 5.1 | README polish | `README.md`: screenshots (plugin list, config panel, sync button on bank), `stub_server/` quickstart, link to `osrs-tracker` for the server-side roadmap, explicit "treat your auth token like a password" note | Renders cleanly on GitHub; screenshots embedded; LICENSE/CHANGELOG linked | 4.4 |
| 5.2 | Release script + 0.1.0 tag | `scripts/release.sh` (set version in build.gradle, commit, tag `v0.1.0`, push to `tools` branch, force-push to mirror `main`, print SHA); `CHANGELOG.md` → `[0.1.0] - <date>` | `./scripts/release.sh 0.1.0` produces `v0.1.0` on both repos; printed SHA matches `git rev-parse HEAD` | 5.1 |
| 5.3 | Plugin Hub PR | Fork `runelite/plugin-hub`, add `plugins/osrs-bank-sync` (warning text per §Interfaces, commit from 5.2), open PR | plugin-hub CI green; PR reviewed; merged | 5.2 |

### Permissions config

No new commands beyond what `default-jdk` provides; `permissions-config.json` does not need changes (gradle wrapper, java, javac, python, uvicorn, curl already allowed).

---

## Risks & open questions

| Risk | Likelihood | Mitigation |
|---|---|---|
| `kyleamielke/tools` is private → Plugin Hub cannot clone | Certain | Public mirror `kyleamielke/osrs-bank-sync` (committed in Naming table); release script automates the sync. |
| Plugin Hub reviewer asks why we send data to a user-configurable URL | Medium | Blunt `warning=` line on the manifest; WikiSync sets the precedent for the trust model. Be prepared to cite the warning in the PR thread. |
| `client.getAccountType()` removed before we can replace the call | Low | Code already isolates the call behind `AccountTypeMapper`; switching to `client.getVarbitValue(Varbits.ACCOUNT_TYPE)` is a one-method change. Enum ordinals match the varbit values today. |
| `InterfaceID.BANK` renamed | Very low | The integer ID is stable across cache updates; if the constant moves package, update the import path accordingly. |
| Account hash is `-1` briefly during login | Low | `onWidgetClosed`/`captureNow` early-exit; next `ItemContainerChanged` after login rebuilds the dirty flag. |
| Bank capacity expansion | Very low | We iterate `container.getItems()` not a fixed array. |
| Server returns large response body on 4xx and floods chat/logs | Low | 200-char log truncation + 80-char chat truncation per §Security posture. |
| Gson `long accountHash` precision lost in JS clients | Low (we control the server) | `osrs-tracker` ingestion parses `account_hash` as int64 (Python int unbounded; Postgres `BIGINT`). |
| User accidentally posts auth token in a bug report | Low | `secret = true` on config field; no log statement takes the token value; README warning. |
| User pastes a URL containing a query string or basic-auth userinfo | Low | Config-save validation rejects it with a chat warning (§Security posture); plugin refuses to submit. |
| Force-submit (manual button) double-fires and creates two server rows | By design (low impact) | Each force submit generates a distinct `snapshot_id`, so both are stored. This is intentional for the manual button — server-side cleanup can dedupe on `captured_at` second if needed, but the wire contract guarantees uniqueness only on `snapshot_id`. |
| `--release 11` warning about Lombok preview features on JDK 21 host | Low | Pin Lombok to 1.18.34+ (matches WikiSync). |

**Open questions to confirm before implementation (none blocking design approval):**

- Should the plugin write a `~/.runelite/osrs-bank-sync/last-snapshot.json` for offline-only debugging? (Default: no; add later as `dumpToFile` flag if needed.)
- Should `pluginVersion` be derived from the JAR manifest at runtime instead of hardcoded? (Default: hardcoded constant updated by `scripts/release.sh`; release script can sed both atomically.)

---

## Out of scope (v1)

- Inventory, equipment, looting bag, seed vault, GE-offers capture. (Architecture leaves room via additive top-level arrays; no other captures implemented.)
- Item-name hydration, GE price hydration. (Server-side concern.)
- Bank tab structure / placeholder metadata.
- Wealth totals, pricing, analytics — `osrs-tracker` concerns.
- Server-side ingestion: the new `bank_snapshots` table, the `POST /api/v1/sync/bank` route, the frontend "Bank" tab. All of that lands in `osrs-tracker` v0.2 against the contract specified above.
- WebSocket / live push to a server.
- Multi-target broadcast (sending to >1 URL).
- Retry queues / offline buffering. (One attempt per trigger; next trigger retries.)
- Cross-account merging / linking to `players` table — `osrs-tracker` handles via its own UI; the plugin sends only `account_hash`.
- TLS pinning / mTLS / token rotation. (Server posture is the user's responsibility once they leave loopback.)

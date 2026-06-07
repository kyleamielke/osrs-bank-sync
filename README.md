# OSRS Bank Sync

RuneLite plugin to sync your bank snapshot to a self-hosted HTTP endpoint.

[![CI](https://github.com/kyleamielke/osrs-bank-sync/actions/workflows/ci.yml/badge.svg)](https://github.com/kyleamielke/osrs-bank-sync/actions/workflows/ci.yml) [![License: BSD-2-Clause](https://img.shields.io/badge/License-BSD--2--Clause-orange.svg)](LICENSE)

> Badge note: this repository is a public mirror; CI executes in the private `kyleamielke/tools` repo.

## What it does

- Captures bank item IDs and quantities from the RuneLite client.
- Sends snapshots to your configured server with an auth token header.
- Supports automatic submit on bank close and manual sync from the bank UI.
- Designed for self-hosted ingestion pipelines (Plugin Hub release coming soon / pending).

<!-- TODO: screenshot - plugin list -->
<!-- TODO: screenshot - config panel -->
<!-- TODO: screenshot - sync button on bank widget -->

## Quickstart

1. Build plugin JAR:

   ```bash
   ./gradlew build
   ```

2. Sideload into RuneLite:

   ```bash
   mkdir -p ~/.runelite/sideloaded-plugins
   cp build/libs/osrs-bank-sync-0.1.0.jar ~/.runelite/sideloaded-plugins/
   ```

3. Start local stub server:

   ```bash
   cd stub_server
   pip install -e .[dev]
   uvicorn osrs_bank_sync_stub.app:app --port 8484
   ```

4. Configure plugin settings in RuneLite:
   - Target URL
   - Auth token
   - Submit mode
   - Sync bank
   - Show chat confirmations

## Triggers

- **Auto on close:** submits when the bank interface closes after detected changes.
- **Manual sync button:** submits immediately from the bank widget.
- **Disconnect fallback:** submits cached dirty snapshot on hopping/connection-loss/login-screen transitions.

## Security

- `authToken` is configured with `secret=true` masking in RuneLite settings UI.
- Logs include header names only (never auth token values).
- One-shot warning notes plaintext risk if using non-HTTPS remote endpoints.
- Target URL validation rejects `user:pass@host` userinfo and query-string URLs.

## Development

- Design: [DESIGN.md](DESIGN.md)
- Changelog: [CHANGELOG.md](CHANGELOG.md)
- License: [LICENSE](LICENSE)

## Roadmap

- **v0.1:** RuneLite side-loadable plugin + local stub server + core auto/manual triggers.
- **v0.2:** `osrs-tracker` ingestion endpoint integration and persistence flow.
- **v0.3:** Plugin Hub release track, operational hardening, and UX polish.

## License

BSD-2-Clause. See [LICENSE](LICENSE).

## See also

- Server-side tracker roadmap: `osrs-tracker`
- Canonical development repo: `kyleamielke/tools` (private), mirrored publicly here

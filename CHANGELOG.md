# Changelog

## [Unreleased]

- Initial repo bootstrap (Phase 0)

### Added
- Gradle 8.x wrapper, build.gradle targeting RuneLite latest.release with Java 11 source (Phase 1.1)
- Minimal `OsrsBankSyncPlugin` + stub `OsrsBankSyncConfig` (Phase 1.2)
- FastAPI stub server under `stub_server/` with `POST /api/v1/sync/bank` and `GET /healthz` for plugin development (Phase 2.1)
- GitHub Actions workflow `java-build` running `./gradlew build --no-daemon` (Phase 1.3)
- CI `python-lint` job: ruff + mypy --strict + pytest on `stub_server/` (Phase 2.2)
- `BankSnapshot`, `BankItem` POJOs; `BankCaptureService`; `AccountTypeMapper` (Phase 3.1)
- `BankSubmitter` (OkHttp) with payload-hash dedupe and `SubmitOutcome` enum; no chat output, no Authorization header in Phase 3 (Phase 3.2)
- Bank-close, item-container-change, and game-state-change triggers wired through `OsrsBankSyncPlugin` (Phase 3.3)
- Full config interface (authToken/submitMode/includeBank/showChatConfirmations) with secret=true on token; targetUrl validation rejects user:pass@ and ?query (Phase 4.1)

# Versioning

Baseline: **1.8.1** (versionName `v1.8.1`, versionCode `10`).

The project follows Semantic Versioning: `MAJOR.MINOR.PATCH`.

- **PATCH** (`1.8.2`, `1.8.3`): backward-compatible bug fixes, UI polish, wording, parser corrections, performance fixes.
- **MINOR** (`1.9.0`): backward-compatible new features or meaningful behavior additions. Reset PATCH to 0.
- **MAJOR** (`2.0.0`): incompatible changes, data migration that cannot be rolled back, or a breaking login/API redesign. Reset MINOR and PATCH to 0.
- Pre-release suffixes use `1.9.0-beta.1`, `1.9.0-rc.1`; do not ship them as the final version.
- `versionCode` increases by 1 for every build delivered to a device, independent of SemVer. Next builds: 11, 12, ...
- Do not reuse a published version number. If a build is discarded before distribution, it may be rebuilt with the same version, but a delivered build must be bumped.

Examples:

- Fix email HTML cleanup: `1.8.2`
- Add a new classroom planner mode: `1.9.0`
- Replace the whole course-platform login protocol: `2.0.0`

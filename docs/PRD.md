# Product Requirements Document — Terminal (Alpine Linux via proot)

## 1. Overview
Terminal is an Android application that runs a real Alpine Linux userland on-device
via `proot`, without requiring root access. Users get a full package manager
(`apk`), a responsive terminal emulator, system maintenance tools, and backup/
restore of their Alpine environment — all through a minimalist blue-white UI.

This document is the authoritative specification for the app. It supersedes any
earlier ad-hoc assumptions made before the PRD was finalized, including the
original plain-shell MVP (real PTY, no proot/Alpine/package manager/backup/
logging), which remains the reusable foundation (CI pipeline, repo structure,
terminal-view integration) that this PRD's scope is built on top of.

## 2. Goals / Non-Goals
**Goals**
- Run an Alpine Linux rootfs on Android via `proot`, no root required.
- Manage packages with `apk` from a graphical dashboard.
- Provide a real, responsive terminal emulator with a keyboard helper row.
- Let users back up and restore their Alpine environment, including across
  devices with a different CPU architecture.
- Keep a traceable history (log) of every operation the app performs.

**Non-Goals**
- No desktop GUI Linux environment (X11/Wayland, desktop apps).
- No distros other than Alpine Linux.
- No automatic (non-manual) repository updates — `apk update`/`upgrade` are
  always user-triggered.

## 3. Target Platform
- Android, minSdk 24 (Android 7.0), compileSdk/targetSdk 34.
- No root required; isolation and syscall emulation handled by `proot`.

## 4. Functional Requirements

| ID | Feature | Priority | Summary |
|----|---------|----------|---------|
| FR-1 | Proot Engine | P0 | Integrate `proot` to run the Alpine rootfs without root. |
| FR-2 | Tab Dashboard | P1 | System info (CPU/RAM/Disk) in a blue-white minimalist design. |
| FR-3 | Tab Terminal | P0 | Responsive terminal emulator + keyboard helper row (Ctrl, Alt, Tab, Esc). |
| FR-4 | Dynamic Package Search | P0 | `apk search` field; each result row has an Install button. |
| FR-5 | Repo Maintenance | P1 | Buttons for `apk update` and `apk upgrade`. |
| FR-6 | System Fix | P1 | Button for `apk fix`. |
| FR-7 | Compressed Backup | P1 | Compress rootfs to `.tar.gz`. |
| FR-8 | Custom Backup Path | P1 | Android SAF lets the user pick the backup destination folder. |
| FR-9 | Cross-CPU Restore | P2 | Extract a backup's rootfs on a different-architecture device. |
| FR-10 | Auto-Fix Post-Restore | P1 | Automatically run `apk fix` immediately after restore completes. |
| FR-11 | Auto-Fix Notification | P1 | After auto-fix, show a notification summarizing fixed/failed counts with a link to the full log. |
| FR-12 | System Log Viewer | P1 | History of every process (install/update/upgrade/fix/backup/restore) with timestamp, duration, status, and raw `apk` output. |
| FR-13 | Log Parsing Highlight | P1 | Auto-color lines containing ERROR/WARNING/failed in red/orange. |
| FR-14 | Log Export & Clear | P2 | Export the log to a text file via SAF, or clear history. |
| FR-15 | Tab Setting | P2 | Terminal font size, accent color option, reset-rootfs action, log retention policy. |

## 5. Non-Functional Requirements
- UI: minimalist, white-dominant with a blue accent; red/orange reserved for
  log error/warning highlighting only (never used decoratively elsewhere).
- Backup, install, and auto-fix operations must run on background
  threads/services — the UI must never freeze.
- Restore must checksum-validate (SHA-256) the backup file before extracting.
- Backup compression uses Gzip (`.tar.gz`).
- Logging is non-blocking (writes happen off the UI thread) with automatic
  rotation by retention policy.
- Every GUI-triggered process carries one consistent process ID across its
  action, its log entry, and its completion notification, for traceability.

## 6. Risks (acknowledged)
- Cross-CPU restore may leave manually-installed (non-`apk`) binaries
  unfixable by the automatic `apk fix` pass.
- Scoped Storage / SAF permissions are strict on Android 11+; all file access
  outside the app's sandbox goes through SAF document URIs with persisted
  permissions.
- Unbounded log growth requires a default-but-configurable retention policy.
- `apk` output formatting may change across Alpine repo versions, which can
  affect the accuracy of FR-13's line classification heuristics.

## 7. Decisions on Open Questions
- **Log Viewer filters**: no category/severity filter in the first version;
  the list simply shows every entry newest-first. Can be added later if
  needed.
- **Default log retention**: 7 days (user-adjustable from 1–30 days in
  Settings, FR-15). Chosen as a sensible default balancing traceability
  against unbounded on-device storage growth.

## 8. Architecture Summary
- `core/rootfs` — downloads/verifies/extracts the Alpine minirootfs for the
  device's CPU architecture and wraps `proot` invocation (FR-1).
- `core/pm` — wraps `apk` operations (search/install/update/upgrade/fix),
  every run logged (FR-4/5/6/12).
- `core/backup` — compressed backup (FR-7/8), restore with checksum
  validation + auto-fix (FR-9/10/11), run from a foreground `Service` so
  Android doesn't kill long operations.
- `core/logging` — Room-backed operation log (FR-12), line classifier for
  highlighting (FR-13), retention/rotation and export/clear (FR-14).
- `ui/dashboard`, `ui/terminal`, `ui/settings`, `ui/logs` — the four
  user-facing screens (Dashboard/Terminal/Settings tabs + Log Viewer/Detail
  screens), covering FR-2/3/15/12/13 respectively.

## 9. Native proot Binary
`proot` ships as a per-ABI native library (`app/src/main/jniLibs/<abi>/
libproot.so`) so Android's installer extracts it with the executable bit set
(a workaround for Android not allowing execution of arbitrary files from app
data directories). Binaries come from the `ItsPhysip/uls-proot` project
(a `termux/proot` fork patched for modern kernel syscalls), fetched and
SHA-256-verified during CI (see `.github/workflows/android-build.yml`).
Supported ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64` (`x86` is not supported —
no upstream binary is published for it).

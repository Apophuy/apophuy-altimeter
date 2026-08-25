---
name: android-kotlin
description: Develop, diagnose, review, and verify Kotlin Android, Jetpack Compose, Gradle, resources, Room, services, permissions, and emulator work in this Altimeter repository. Do not activate for unrelated prose-only or generic repository tasks.
---

# Android/Kotlin workflow

Read the root `AGENTS.md`, `README.md`, and the affected implementation before
deciding on a change. Treat `AGENTS.md` as the project-domain reference.

Use `./tools/gradle-android.sh` to invoke Gradle so SDK discovery is consistent
across shells and agents. Select validation by the affected boundary:

- Pure Kotlin logic: run `testDebugUnitTest` and add the smallest focused JVM
  test that demonstrates changed behavior.
- Android API, Compose, resources, manifest, or packaging: also run `lintDebug`
  and `assembleDebug`.
- Activity/service lifecycle, permissions, Room integration, or UI interaction:
  run `make emulator-check` when the host supports virtualization; otherwise
  report that instrumentation was not run and why.
- A developer-supplied running device: use `make device-check`; never assume
  that a connected physical device is disposable.

Use the project Context7 MCP server when current Android, AndroidX, Kotlin, or
Gradle API documentation would change the implementation decision. Prefer
official Android or Kotlin sources when verifying unstable version-specific
behavior.

Keep internal data metric, keep user-facing strings in both English and Russian,
and preserve the GNSS/vendor, foreground-service, storage, and privacy rules in
the repository guide. Do not introduce a framework or upgrade dependencies as
an incidental cleanup.

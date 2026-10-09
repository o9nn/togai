# Togai Kotlin Build Status

_Last verified: 2026-10-09 with `gradle :app:compileDebugKotlin` against a real
Android SDK (platform 34, build-tools 34.0.0), Gradle 8.14.3, Kotlin 1.9.25._

## Layout (as of current `main`)

`main` is an Android multi-project build. **`app/src/main/kotlin` is the code that
gets compiled.** The root `src/main/kotlin` is an older duplicate that no Gradle
module builds; files changed on this branch are kept identical in both copies.

## Status

| Check | Result |
|---|---|
| `:app:compileDebugKotlin` on `main` (after Compose fix) | 120 errors |
| `:app:compileDebugKotlin` on this branch | **14 errors**, all in `cognitive/selfhealing/SelfHealingCognitiveSystem.kt` |
| Errors this branch introduced | **0** (error sets compared against a `main` build in a separate worktree) |
| `compileTestKotlin` | Not reached; 148 errors when last measured on the old root build, all pre-existing test/API drift |

Before any of this, `:app` couldn't compile at all: `app/build.gradle.kts` pinned
Compose Compiler `1.5.4` (Kotlin 1.9.20 only) against Kotlin 1.9.25. Bumped to
`1.5.15`, the paired release.

## Remaining: `SelfHealingCognitiveSystem.kt` (14 errors) — needs a decision

It calls 14 methods that don't exist, and they can't be added as mechanical fixes:

| Calls | Problem |
|---|---|
| `ecanKernel.getQueueStatus()`, `drainLowPriorityTasks()`, `cancelStalledTasks()` | `ECANKernel` has no task queue. The queue lives in `ECANScheduler`, which this class doesn't reference. |
| `hypergraph.findOrphanedAtoms()` / `removeOrphanedAtoms()` | Atoms hold no references in this model, so "atoms with invalid references" can't occur. The real integrity gap is the opposite: `Hypergraph.removeAtom()` leaves **dangling links**. |
| `hypergraph.detectCircularReferences()` / `breakCircularReferences()` | Links are undirected hyperedges; a cycle (A–B–C–A) is ordinary structure, not corruption. Auto-"breaking" cycles would delete valid links. |
| `cognitiveEngine.normalizeAttention / applyAttentionSmoothing / decayHighAttention` | Feasible (attention values live on atoms), but each needs a defined formula. |
| `cognitiveEngine.resetTensor(index) / clampAllTensors() / clearCaches()`, `hypergraph.compactStorage()` | `CognitiveEngine` has no indexed tensor store or caches to act on. |

Several of these are destructive and would run automatically from a monitoring loop,
so their semantics (what may be deleted, when) are a product decision. The only
caller is `Phase7Demo`.

## Design decisions made on this branch

Least-invasive choice in each case; revisit if intent differs.

| Area | Decision |
|---|---|
| `unification` placeholder types | Deleted the four `"Placeholder ... may be implemented elsewhere"` duplicates; use real `hypergraph`/`metacognition` types. Missing values come from real sources: latest `IntrospectionResult` (new `getLatestIntrospection()`), a new `averageSystemHealth` aggregate on `RecursiveVerificationStats`, STI/LTI via `computeECANStats()`. Neutral `0.5f` when no history, matching existing convention. |
| `CognitiveEngine` API | Added `addAtom(...)`, `addLink(source, target, label, type = EVALUATION)`, `runAttentionCycle()`; constructor now accepts an optional `Hypergraph` (default unchanged). |
| `Hypergraph.updateAtom(atom)` | New: replace an existing atom by id (CRDT UPDATE path). |
| Single-Float truth values | Causal graph: graph-wide `confidence` → `TruthValue.confidence`, edge `strength` → `strength`, node atoms strength `1.0`. `Phase7Demo`: number read as strength, confidence = `TruthValue.DEFAULT.confidence`. |
| `StableDiffusionService` | Generator returns bytes; written to `<outputDir>/<taskId>.png` (default `<tmpdir>/togai-sd`) so `imagePath` is a real file, as `SharingService` expects. |
| `MemoryOptimizer` pools | Typed by element (`CognitiveTensor`, `Atom`); the generic-`T` accessors weren't type-safe and had no callers. |
| `SystemIntelligenceService` media detection | Compiles now; still returns `null` at runtime until the app ships a `NotificationListenerService` subclass (none exists). |

Mechanical fixes (imports, `@PublishedApi`, primitive-array conversions, stray brace,
`Sequence.takeLast`, `coroutineScope`, raw-string `$` escaping, etc.) are described in
the individual commit messages.

## Notes

- Android CI (`.github/workflows/android-ci.yml`) has failed on every `main` run since
  2026-01-22. Logs have expired, so the cause can't be confirmed; the Compose
  mismatch above would fail `assembleDebug` on its own.
- Native JNI bindings in `docs/TOGAI_GENERALIZATION_ROADMAP.md` remain stubs (no
  `.so` files or native build wiring).

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
| `:app:compileDebugKotlin` on this branch | **BUILD SUCCESSFUL, 0 errors** |
| Errors this branch introduced | **0** (error sets compared against a `main` build in a separate worktree) |
| `:app:assembleDebug` (what Android CI runs) | **BUILD SUCCESSFUL**, `app-debug.apk` produced. Uses CMake 3.31.5 (shipped on GitHub's Ubuntu 24.04 runners alongside 4.1.2; 3.18.1, previously pinned, is not, which failed CI). AGP auto-installs its default NDK 25.1.8937393 if missing. |
| `compileTestKotlin` | Not addressed; 148 errors when last measured on the old root build, all pre-existing test/API drift |

Before any of this, `:app` couldn't compile at all: `app/build.gradle.kts` pinned
Compose Compiler `1.5.4` (Kotlin 1.9.20 only) against Kotlin 1.9.25. Bumped to
`1.5.15`, the paired release.

## Self-healing recovery semantics (implemented)

`SelfHealingCognitiveSystem` called 14 methods that existed only in
`cognitive/extensions/Phase7Extensions.kt`, which nothing imported. That file was a
stub layer: the engine/ECAN functions were no-ops (recovery still reported success),
and its `Hypergraph` functions ran against a separate global atom map rather than the
real graph. Its `removeOrphanedAtoms()` would have deleted most non-LINK atoms. It has
been replaced with real implementations on the owning classes and removed.

| Self-healing action | What it now does |
|---|---|
| Hypergraph inconsistency | Finds/removes **dangling links** (links naming deleted atoms; `removeAtom` never cleaned them). Atoms hold no references, so "orphaned atoms" isn't a defect in this model. |
| "Circular references" | Finds/repairs **self-referential links** (same atom listed twice): targets de-duplicated, links left with < 2 atoms removed. Cycles across distinct atoms are normal and untouched. |
| Queue overflow / stalls | On `ECANScheduler` (where the queue lives; `SelfHealingCognitiveSystem` now takes the scheduler instead of `ECANKernel`). Drains the lowest-priority fraction; cancels tasks queued longer than `stallTimeoutMs` (same definition for detection and cancellation). |
| Attention drift / instability / saturation | Act on atom STI over the same active-atom set the detector measures: z-score rescale to baseline; smoothing toward the mean; decay above the detector's saturation threshold. |
| Tensor corruption / bounds | Tensors are derived from atoms. Reset non-finite truth/attention values (re-checked first, index drift reports failure); clamp to `TruthValue` [0, 1] and STI/LTI >= 0. |
| Memory pressure | Clears the scheduler's completed-task history and rebuilds hypergraph maps to size. |

The tensor bounds check previously required depth and STI/LTI in [0, 1], but depth is a
per-type constant up to 4.0 and STI/LTI are unbounded. It flagged valid atoms forever,
and clamping them would have crushed ECAN attention data; it now follows the model's
own validity rules.

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
  mismatch above would fail `assembleDebug` on its own. `./gradlew test` (CI's other
  step) is still blocked by the test-compilation errors.
- Native JNI bindings in `docs/TOGAI_GENERALIZATION_ROADMAP.md` remain stubs (no
  `.so` files or native build wiring).

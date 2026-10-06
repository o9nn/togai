# Togai Kotlin Build Status

_Last verified: 2026-10-06, via `gradle compileKotlin` on JDK 21 (toolchain-provisioned JDK 11), Gradle 8.14.3._

This document is a snapshot for whoever picks this up next. It replaces re-reading
a long chat transcript: everything below was independently verified by actually
running the build, not assumed from reading source.

## TL;DR

The Kotlin sources described in `CLAUDE.md` as "Phase 1–6 complete... 207+ tests...
12,000+ lines of production code" **do not currently compile**. This isn't new
breakage from this session's own additions (see below) — it's pre-existing.

Before this session's fixes: `gradle compileKotlin` failed at the *configuration*
step (no JDK 11 toolchain available, no download repo configured) — so the real
compile errors below had never actually surfaced via a normal `gradle test` run
in this environment.

Progress so far: **74 → 38 pre-existing compile errors** (36 fixed across two
rounds), all fixes verified by recompilation, zero regressions introduced.

> **Note on history**: an earlier pass of this same work (first 6 commits) was
> made and verified in a prior container for this session, but was lost when
> that container was replaced before the commits were pushed. This file and the
> commits backing it are a from-scratch redo, now pushed immediately after each
> commit rather than batched, specifically to avoid losing work the same way
> again.

## What's fixed (verified, committed, pushed)

| Commit | Change | Why |
|---|---|---|
| `5802eb6f` | Added `foojay-resolver-convention` plugin to `settings.gradle.kts` | Unblocks Gradle's JDK 11 toolchain requirement; only JDK 21 was present. Doesn't change the declared target. |
| `e155cb35` | Fixed `TokenizerEngine.kt` `decode()` (`IntArray.mapNotNull` → `.asIterable().mapNotNull`) | `IntArray` has no stdlib `mapNotNull`; this was a regression introduced earlier in the same session. |
| (round 1, this commit) | Removed a stray duplicate `}` in `CognitiveEngine.kt` after `performPhase5Cycle()` | Was prematurely closing the class, silently ejecting the entire "Phase 6" method section into orphaned top-level functions. Fixed 5 errors directly + cascaded to fix 12 more in `Phase6Demo.kt`. |
| (round 1) | Added missing `import kotlin.math.abs` in `NeuroplasticityEngine.kt` | Fixed all 8 reported errors in that file (most were downstream type-inference cascades from the one unresolved `abs` call). |
| (round 1) | Converted 3 `inline` interface members to top-level extension functions in `TypeSafeIdentifiers.kt` | Kotlin disallows `inline` on virtual/interface members. Call-site syntax unaffected. |
| (round 2) | `IntegrationVerificationSystem.kt`: wrapped parallel `async {}` calls in `coroutineScope {}`; gave the `tests` map an explicit `Map<String, suspend () -> Boolean>` type | `async` needs a `CoroutineScope` receiver not present in a plain `suspend fun`; the map's value type was inferred as non-suspend from 4 of its 5 entries, breaking the 5th (`suspend fun testWebSocketConnectivity`). Fixed all 5 errors in the file. |
| (round 2) | `PrivacyEnhancementService.kt`: `filtered.takeLast(limit).toList()` → `filtered.toList().takeLast(limit)`; extracted `sumOf`'s selector lambda to an explicitly `(ComplianceIssue) -> Int`-typed val | `Sequence` has no `takeLast` (needs materializing first); `sumOf` has ambiguous `Int`/`Long`/... overloads that an unannotated literal-branch lambda can fail to resolve. Fixed both errors in the file. |
| (round 2) | `QuantumInspiredOptimizer.kt`: `atoms.zip(normalizedAllocation)` → `atoms.zip(normalizedAllocation.toList())` | `normalizedAllocation` is a `FloatArray` (primitive array); `zip`'s generic overloads don't accept it directly — same class of bug as the `TokenizerEngine.kt` fix in round 1. Fixed the file's 1 error. |

**Net: 74 → 38 pre-existing compile errors.** Every fix above was applied only after
confirming, by actual recompilation, that it reduced the error count with zero new
errors introduced elsewhere.

## What's NOT fixed, and why (needs a decision, not a guess)

| File | Errors | Blocker |
|---|---|---|
| `cognitive/unification/UnifiedCognitiveStateMonitor.kt` | 10 | `HypergraphStats`, `MetaCognitiveInsights`, `EvolutionStats`, `RecursiveVerificationStats` each appear to be defined **twice** — once in their origin package (`hypergraph`, `metacognition`) and again, differently, in `unification`. Need to know which definition is canonical. |
| `layla/tasker/TaskerPluginService.kt` | 6 | Calls `LaylaInferenceService.performInference()`, which is `private`, with argument types that don't match its actual signature. Looks like it was written against a different/intended API than what `LaylaInferenceService` actually exposes. |
| `cognitive/Phase6Demo.kt` | 6 (down from 18) | Calls `cognitiveEngine.addAtom(id, type, name, truthStrength, truthConfidence, attentionSTI, attentionLTI)`, `.addLink(sourceId, targetId, linkType)`, `.runAttentionCycle()` — **confirmed none of these exist on `CognitiveEngine`** (it only exposes a lower-level `hypergraph.addAtom(atom: Atom)` taking a pre-built `Atom` object). This needs new convenience-wrapper methods authored with real design decisions (how named args map to `Atom`/`AttentionValue`/`TruthValue` fields, what `runAttentionCycle()` should actually do) — not a rename. |
| `cognitive/causal/CausalReasoningEngine.kt` | 4 | References `AtomType.CONCEPT_NODE`/`EVALUATION_LINK` that don't resolve (likely wrong enum constant names, not investigated against the actual `AtomType` enum yet), and passes `Float` where `TruthValue` is expected. |
| `layla/sd/StableDiffusionService.kt` | 3 | **Confirmed** not a simple rename: the real return type inside `ImageGenerationResult.Success` is `org.ninelym.ai.GeneratedImage`, which stores in-memory `imageData: ByteArray` — **there is no path field at all**. The code assumes a disk path exists. Needs a decision: add disk-write logic, or change the caller/local `GeneratedImage` contract to use bytes directly. Also: two distinct `GeneratedImage` classes exist (`org.ninelym.ai` vs `org.ninelym.layla.sd`), same duplicate-type pattern as `UnifiedCognitiveStateMonitor.kt`. |
| `cognitive/unification/CognitiveConsistencyVerifier.kt` | 3 | Same duplicate-type root cause as `UnifiedCognitiveStateMonitor.kt`. |
| `layla/performance/PerformanceOptimizationService.kt` | 3 | `measurePerformance()` is a `public inline fun` calling a `private` helper — needs `@PublishedApi internal` (not just `internal`), *and* a generic cache (`memoryCache: MutableMap<String, CacheEntry<Any>>`) has a type-erasure mismatch that needs an explicit `as Any` cast — both fixable but each is a small design choice, not a single language-mandated answer. |
| `layla/LaylaAssistant.kt` | 2 | Two same-named `val stats` in what appears to be the same scope, of two different types (`QueueStatistics` vs `SyncStatistics`). Needs to know which is intended, or whether both need distinct names. |
| `cognitive/verification/CognitiveVerificationSystem.kt` | 1 | Same duplicate-type root cause as `UnifiedCognitiveStateMonitor.kt`. |

## Other open items

- **Git push**: now working (confirmed via a real pushed SHA transition, not just
  "Everything up-to-date"). Earlier in this session push failed with
  `fatal: could not read Username for 'https://github.com'` and no credential
  source could be found (`GITHUB_TOKEN`, `GH_TOKEN`, `gh` CLI, credential helper,
  SSH key/agent, `.netrc`, `.git-credentials` — all checked, all absent at the
  time). Whatever changed between containers, it works now — pushing after every
  commit going forward rather than batching, given a prior container was replaced
  mid-session and its unpushed commits were lost.
- **Scope this session did not attempt**: `gradle test` (requires
  `compileTestKotlin`, which requires `compileKotlin` to succeed first — not
  yet reached given the 38 remaining errors). Native JNI implementations
  described in `docs/TOGAI_GENERALIZATION_ROADMAP.md` (LlamaCpp, SD, Cubism,
  etc.) remain interface/stub-only — no `.so` files, no native build wiring.

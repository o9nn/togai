# Togai Kotlin Build Status

_Last verified: 2026-10-09 on JDK 21 (toolchain-provisioned JDK 11), Gradle 8.14.3.
All commits listed are pushed to `claude/verify-smali-manifest-0M7yh`._

## TL;DR

| Task | State |
|---|---|
| `gradle compileKotlin` (main) | **BUILD SUCCESSFUL** — was 74 errors (and before that, failed at configuration) |
| `gradle compileTestKotlin` | **148 errors**, all pre-existing test-vs-source API drift (see below) |
| `gradle test` | Blocked on test compilation |

The test errors were invisible until now: Gradle can't compile tests until the
main sources compile. None of them reference any symbol changed while fixing
main (checked by grepping the full error list).

## How main got to zero

Every change was verified by recompiling before committing.

**Unambiguous fixes (syntax, imports, language rules)**

| File | Change |
|---|---|
| `settings.gradle.kts` | `foojay-resolver-convention` plugin so Gradle can provision the declared JDK 11 toolchain |
| `CognitiveEngine.kt` | Removed a stray `}` that closed the class early and ejected all Phase 6 methods |
| `NeuroplasticityEngine.kt` | Missing `import kotlin.math.abs` |
| `TypeSafeIdentifiers.kt` | `inline` interface members → top-level inline extensions (Kotlin forbids inline virtual members) |
| `IntegrationVerificationSystem.kt` | `async {}` wrapped in `coroutineScope {}`; `tests` map typed `suspend () -> Boolean` |
| `PrivacyEnhancementService.kt` | `Sequence.toList().takeLast()`; explicitly-typed `sumOf` selector |
| `QuantumInspiredOptimizer.kt`, `TokenizerEngine.kt` | Primitive arrays converted before `zip` / `mapNotNull` |
| `CausalReasoningEngine.kt` | `AtomType.CONCEPT_NODE`/`EVALUATION_LINK` → `CONCEPT`/`EVALUATION` (per adjacent comments) |
| `LaylaAssistant.kt` | Two unrelated `stats` vals in one function → `queueStats` / `syncStats` |

**Design decisions made** (each the least invasive option; revisit if intent differs)

| Area | Decision |
|---|---|
| `unification` placeholder types | Deleted the four `"Placeholder data types ... may be implemented elsewhere"` duplicates and used the real `hypergraph`/`metacognition` types. Missing values now come from their real sources: `processingEfficiency`/`attentionCoherence` from the latest `IntrospectionResult` (new `MetaCognitivePathwaySystem.getLatestIntrospection()`), `averageSystemHealth` as a new aggregate on `RecursiveVerificationStats`, STI/LTI aggregates via `computeECANStats()`. Neutral default `0.5f` when no history exists, matching the existing `calculateMetaCognitiveHealth()` convention. |
| `CognitiveEngine` API for `Phase6Demo` | Added `addAtom(...)` (fields map 1:1 to `Atom`/`TruthValue`/`AttentionValue`), `runAttentionCycle()` (delegates to `ECANKernel.runAttentionCycle()`), and `addLink(source, target, label, type = EVALUATION)` — free-text labels have no `LinkType` equivalent; EvaluationLink is OpenCog's predicate-labelled relation. Label is kept in the link id. |
| Causal `TruthValue`s | `causalGraph.confidence` is graph-wide → `TruthValue.confidence`; edges take `strength` from `causalGraph.strengths`; node atoms use strength `1.0` (asserted graph members). |
| `StableDiffusionService` image path | Generator returns bytes, not a path. Bytes are written to `<outputDir>/<taskId>.png` (constructor param, default `<tmpdir>/togai-sd`) so `imagePath` is a real file — `SharingService` reads it with `File(imagePath)`. |
| `TaskerPluginService` | Uses public `LaylaInferenceService.infer()` instead of the private `performInference()`. |
| `PerformanceOptimizationService` | `recordSnapshot` is `@PublishedApi internal`; `cache()` takes `T : Any` (`getCached` already treats `null` as missing). |

## Test compilation: 148 errors

Tests were written against APIs that don't match the sources. By file:

| File | Errors |
|---|---|
| `cognitive/metacognition/RecursiveVerificationSystemTest.kt` | 48 |
| `cognitive/metacognition/EvolutionaryOptimizerTest.kt` | 22 |
| `cognitive/TensorValidationFrameworkTest.kt` | 22 |
| `cognitive/Phase5IntegrationTest.kt` | 21 |
| `cognitive/metacognition/MetaCognitivePathwaySystemTest.kt` | 16 |
| `ai/TogaCharacterTest.kt` | 8 |
| `cognitive/unification/CognitiveUnificationTest.kt` | 6 |
| `layla/phase2/StableDiffusionServiceTest.kt` | 2 |
| `cognitive/causal/CausalReasoningEngineTest.kt` | 2 |
| `ai/AIIntegrationTest.kt` | 1 |

Typical causes: fields that don't exist on result types (`verificationLayers`,
`overallSystemHealth`, `generationsRun`), enum values that don't exist
(`AtomType.RELATION`, `ImageStyle.CYBERPUNK`), constructor arguments in the wrong
order, and nullable map lookups (`Float?`) used where `Float` is required. Each
needs a choice between changing the test to match the source or extending the
source to match the test.

## Other notes

- An earlier pass of this work was lost when a container was replaced before its
  commits were pushed; everything was redone and is now pushed after each commit.
- Native JNI bindings described in `docs/TOGAI_GENERALIZATION_ROADMAP.md` remain
  interface/stub-only — no `.so` files or native build wiring.

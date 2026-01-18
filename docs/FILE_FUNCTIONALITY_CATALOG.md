# Togai File Functionality Catalog

## Complete File-by-File Documentation

This document provides a comprehensive catalog of every file in the Togai repository, describing its purpose, functionality, and relationships.

---

## Table of Contents

1. [Project Root Files](#1-project-root-files)
2. [Android Application (app/)](#2-android-application)
3. [Cognitive Computing Module](#3-cognitive-computing-module)
4. [AID Framework Module](#4-aid-framework-module)
5. [Layla Assistant Module](#5-layla-assistant-module)
6. [Quantum Computing Module](#6-quantum-computing-module)
7. [Evolution/Orchestration Module](#7-evolution-orchestration-module)
8. [Native Integration Modules](#8-native-integration-modules)
9. [Python Personality System](#9-python-personality-system)
10. [Test Files](#10-test-files)
11. [Documentation](#11-documentation)
12. [Build and CI/CD](#12-build-and-cicd)
13. [GitHub Configuration](#13-github-configuration)

---

## 1. Project Root Files

### Configuration Files

| File | Purpose | Key Features |
|------|---------|--------------|
| `build.gradle.kts` | Root Gradle build configuration | Kotlin 1.9.25, AGP 8.2.2, repository management |
| `settings.gradle.kts` | Project settings | Plugin management, repository configuration |
| `gradle.properties` | Gradle properties | JVM args, Android settings, optimization flags |
| `pyproject.toml` | Python project config | Package metadata, dependencies, entry points |
| `setup.py` | Python setuptools | Package installation configuration |
| `requirements.txt` | Python dependencies | Runtime and development dependencies |
| `Makefile` | Build automation | 20+ targets for build, test, lint, deploy |
| `Dockerfile` | Container definition | Multi-stage build, Python 3.11, non-root user |
| `docker-compose.yml` | Service orchestration | Volume mounts, port mapping, health checks |
| `.pre-commit-config.yaml` | Pre-commit hooks | black, pylint, flake8, mypy integration |

### Script Files

| File | Purpose | Usage |
|------|---------|-------|
| `build.sh` | General build | `./build.sh` - Builds entire project |
| `build_apk.sh` | APK building | `./build_apk.sh` - Creates signed APK |
| `install_apk.sh` | APK installation | `./install_apk.sh` - Installs to device |
| `toga_demo.sh` | Character demo | `./toga_demo.sh` - Runs Toga personality demo |
| `toga_character_demo.sh` | Animation demo | Character animation demonstration |
| `layla_phase1_demo.sh` | Phase 1 demo | Foundation services demonstration |
| `layla_phase2_demo.sh` | Phase 2 demo | Enhancement services demonstration |
| `causal_reasoning_demo.sh` | Causal AI demo | Pearl's causal hierarchy demo |
| `planetary_neural_network_demo.sh` | Distributed cognition | Planetary-scale neural network demo |
| `phase5_demo_simulation.sh` | Meta-cognition demo | Recursive self-improvement demo |
| `validate_implementation.sh` | Validation | Implementation verification |
| `verify_phase5.sh` | Phase 5 check | Phase 5 component verification |
| `apply_repairs.sh` | Build fixes | Applies automatic repairs |
| `test_device.sh` | Device testing | Tests on connected device |

### Analysis Tools

| File | Purpose | Output |
|------|---------|--------|
| `analyze_apktool.py` | APK analysis | Configuration and structure analysis |
| `analyze_smali_architecture.py` | Smali analysis | Bytecode architecture analysis |
| `fix_kotlin_errors.py` | Error fixing | Automatic Kotlin error correction |

---

## 2. Android Application

### 2.1 Main Application (`app/src/main/kotlin/org/ninelym/togai/`)

| File | Purpose | Key Classes/Functions |
|------|---------|----------------------|
| `TogaiApplication.kt` | Application entry | `TogaiApplication` - lifecycle, initialization |
| `MainActivity.kt` | Main UI activity | `MainActivity` - Compose UI, navigation |
| `ErrorHandler.kt` | Error management | Global error handling, crash reporting |
| `PerformanceMonitor.kt` | Performance tracking | Metrics collection, timing |

### 2.2 Services (`app/src/main/kotlin/org/ninelym/togai/service/`)

| File | Purpose | Key Features |
|------|---------|--------------|
| `AIInferenceService.kt` | Background AI | Foreground service, model inference |
| `TogaiFirebaseMessagingService.kt` | Push notifications | FCM integration, message handling |

### 2.3 Accessibility (`app/src/main/kotlin/org/ninelym/togai/accessibility/`)

| File | Purpose | Standards |
|------|---------|-----------|
| `AccessibilityManager.kt` | A11y management | WCAG AA/AAA compliance |

### 2.4 Sync (`app/src/main/kotlin/org/ninelym/togai/sync/`)

| File | Purpose | Features |
|------|---------|----------|
| `OfflineFirstSyncManager.kt` | Offline sync | Conflict resolution, queue management |

---

## 3. Cognitive Computing Module

### 3.1 Core (`app/src/main/kotlin/org/ninelym/cognitive/`)

| File | Purpose | Key Functions |
|------|---------|---------------|
| `CognitiveEngine.kt` | Main engine | `processSchemeExpression()`, `getCognitiveState()`, `performPhase5Cycle()`, `performPhase6ValidationCycle()` |
| `CognitiveTensor.kt` | Tensor definition | 9-dimensional cognitive state representation |
| `CognitiveDemo.kt` | Demo code | Phase 1 demonstration |
| `Phase4Demo.kt` | Phase 4 demo | Polish & Scale demonstration |
| `Phase5Demo.kt` | Phase 5 demo | Meta-cognition demonstration |
| `Phase6Demo.kt` | Phase 6 demo | Testing & unification demonstration |
| `Phase7Demo.kt` | Phase 7 demo | Advanced cognitive computing |

### 3.2 Hypergraph (`cognitive/hypergraph/`)

| File | Purpose | Data Structures |
|------|---------|-----------------|
| `Hypergraph.kt` | Graph structure | AtomSpace-compatible hypergraph |
| `Atom.kt` | Node definition | Atoms with truth/attention values |

### 3.3 ECAN System (`cognitive/ecan/`)

| File | Purpose | Algorithm |
|------|---------|-----------|
| `ECANKernel.kt` | Attention kernel | Economic attention allocation |
| `ECANScheduler.kt` | Task scheduler | Priority-based task scheduling |

### 3.4 Meta-Cognition (`cognitive/metacognition/`)

| File | Purpose | Capability |
|------|---------|------------|
| `MetaCognitivePathwaySystem.kt` | Self-awareness | Introspection and reflection |
| `EvolutionaryOptimizer.kt` | Optimization | Genetic algorithm optimization |
| `RecursiveVerificationSystem.kt` | Verification | Self-improvement loops |

### 3.5 Unification (`cognitive/unification/`)

| File | Purpose | Function |
|------|---------|----------|
| `UnifiedCognitiveStateMonitor.kt` | State monitoring | Cross-phase state capture |
| `CognitiveConsistencyVerifier.kt` | Consistency | System-wide verification |

### 3.6 Verification (`cognitive/verification/`)

| File | Purpose | Scope |
|------|---------|-------|
| `CognitiveVerificationSystem.kt` | Core verification | Hypergraph integrity checks |
| `Phase1VerificationFramework.kt` | Phase 1 checks | Foundation verification |

### 3.7 Visualization (`cognitive/visualization/`)

| File | Purpose | Output Formats |
|------|---------|----------------|
| `CognitiveVisualizer.kt` | Visualization | Summary, DOT graph, CSV export |

### 3.8 Tensor Processing (`cognitive/tensor/`)

| File | Purpose | Operations |
|------|---------|------------|
| `TensorFragmentProcessor.kt` | Fragment handling | Atom-to-tensor conversion |

### 3.9 Scheme Integration (`cognitive/scheme/`)

| File | Purpose | Language |
|------|---------|----------|
| `SchemeCognitiveGrammar.kt` | Grammar parser | Scheme expression parsing |

### 3.10 Additional Cognitive Subsystems

| Directory | Purpose | Files |
|-----------|---------|-------|
| `cognitive/causal/` | Causal reasoning | Pearl's causal hierarchy |
| `cognitive/distributed/` | Distributed mesh | Cognitive mesh network |
| `cognitive/embodiment/` | Embodied cognition | Sensorimotor integration |
| `cognitive/neural/` | Neural-symbolic | GGML kernel integration |
| `cognitive/neuroplasticity/` | Learning | Synaptic plasticity |
| `cognitive/neurosymbolic/` | Hybrid processing | Neural-symbolic synthesis |
| `cognitive/planetary/` | Planetary scale | Cities as attention heads |
| `cognitive/quantum/` | Quantum optimization | Quantum-inspired algorithms |
| `cognitive/selfhealing/` | Self-repair | Automatic error correction |

---

## 4. AID Framework Module

### 4.1 Core (`app/src/main/kotlin/org/ninelym/aid/`)

| File | Purpose | Architecture |
|------|---------|--------------|
| `AIDFramework.kt` | Main framework | vNPU architecture, process(), registerPersona() |

### 4.2 Core Components (`aid/core/`)

| File | Purpose | Role |
|------|---------|------|
| `AIDCore.kt` | Central processor | Virtual NPU implementation |

### 4.3 Kernel (`aid/kernel/`)

| File | Purpose | Abstraction |
|------|---------|-------------|
| `SelfKernel.kt` | Identity kernel | AI consciousness abstraction |

### 4.4 Persona System (`aid/persona/`)

| File | Purpose | Features |
|------|---------|----------|
| `PersonaFactory.kt` | Persona creation | Factory for AI personalities |
| `PersonaDriver.kt` | Driver interface | Personality driver system |

### 4.5 Integration (`aid/hub/`)

| File | Purpose | Services |
|------|---------|----------|
| `IntegrationHub.kt` | Service hub | Endpoint registration |
| `ServiceEndpoints.kt` | Endpoint definitions | Service interfaces |

### 4.6 Adapters (`aid/adapter/`)

| File | Purpose | Adaptation |
|------|---------|------------|
| `ServiceAdapter.kt` | Service adaptation | Protocol translation |

---

## 5. Layla Assistant Module

### 5.1 Core (`app/src/main/kotlin/org/ninelym/layla/`)

| File | Purpose | Services |
|------|---------|----------|
| `LaylaAssistant.kt` | Main assistant | 21 services across 4 phases |

### 5.2 Phase 1: Foundation Services (`layla/`)

| Directory | File | Purpose |
|-----------|------|---------|
| `inference/` | `LaylaInferenceService.kt` | LLaMA.cpp language model |
| `chat/` | `ChatService.kt` | Multi-turn conversations |
| `text/` | `TextProcessingService.kt` | Remember/Chat with text |
| `text/` | `RememberActivity.kt` | Text memory activity |
| `text/` | `ChatActivity.kt` | Chat with text activity |
| `file/` | `FileAccessService.kt` | File system access |
| `auth/` | `AuthenticationService.kt` | OAuth 2.0/OIDC |
| `notification/` | `NotificationService.kt` | Advanced notifications |

### 5.3 Phase 2: Enhancement Services

| Directory | File | Purpose |
|-----------|------|---------|
| `sd/` | `StableDiffusionService.kt` | Image generation |
| `voice/` | `VoiceInputService.kt` | Speech-to-text (Kaldi) |
| `voice/` | `VoiceOutputService.kt` | Text-to-speech (Piper) |
| `character/` | `EnhancedCharacterService.kt` | Live2D animation |
| `tasker/` | `TaskerPluginService.kt` | Tasker integration |
| `calendar/` | `CalendarService.kt` | AI-powered calendar |
| `taskmgr/` | `EnhancedTaskManagerService.kt` | Background tasks |

### 5.4 Phase 3: Integration Services

| Directory | File | Purpose |
|-----------|------|---------|
| `document/` | `DocumentProcessingService.kt` | PDF processing, Q&A |
| `billing/` | `BillingService.kt` | Google Play Billing |
| `sharing/` | `SharingService.kt` | Multi-format export |
| `tasker/` | `AdvancedTaskerWorkflowService.kt` | AI decision trees |
| `cloud/` | `CloudSyncService.kt` | Privacy-preserving sync |

### 5.5 Phase 4: Polish & Scale Services

| Directory | File | Purpose |
|-----------|------|---------|
| `ui/` | `ResponsiveUIFramework.kt` | Adaptive layouts, WCAG |
| `performance/` | `PerformanceOptimizationService.kt` | Memory, battery optimization |
| `privacy/` | `PrivacyEnhancementService.kt` | Dashboard, differential privacy |
| `analytics/` | `AnalyticsMonitoringService.kt` | Privacy-respecting metrics |

---

## 6. Quantum Computing Module

### 6.1 Core (`app/src/main/kotlin/org/ninelym/quantum/`)

| File | Purpose | Key Features |
|------|---------|--------------|
| `QuantumNeuralProcessor.kt` | Quantum processor | 64 qubits, Hadamard/CNOT gates, entanglement |

### Data Types

| Type | Purpose | Fields |
|------|---------|--------|
| `Qubit` | Quantum bit | index, alpha, beta, phase |
| `QuantumState` | State vector | amplitudes, numQubits, isEntangled |
| `QuantumProcessingResult` | Processing result | coherence, entanglement, interference |
| `QuantumStatistics` | System stats | numQubits, numEntanglements, averageCoherence |

---

## 7. Evolution/Orchestration Module

### 7.1 Core (`app/src/main/kotlin/org/ninelym/evolution/`)

| File | Purpose | Modalities |
|------|---------|------------|
| `MultimodalOrchestrator.kt` | Multimodal fusion | Language, Image, Speech, Quantum |

### Key Types

| Type | Purpose | Contents |
|------|---------|----------|
| `MultimodalRequest` | Input request | textInput, imagePrompt, audioInput |
| `MultimodalResponse` | Output response | results, fusedResult, processingTime |
| `Modality` | Enum | LANGUAGE, IMAGE, SPEECH, QUANTUM |
| `OrchestrationCapabilities` | System capabilities | Per-modality availability |

---

## 8. Native Integration Modules

### 8.1 Native Library Loading (`app/src/main/kotlin/org/ninelym/native/`)

| File | Purpose | Libraries |
|------|---------|-----------|
| `NativeLibraryLoader.kt` | Library management | 87 ARM64 libraries |

### Library Categories

| Category | Count | Examples |
|----------|-------|----------|
| Core | 4 | c++_shared, folly_runtime, fbjni, glog |
| AI/ML | 10 | ggml, llama, onnxruntime, ncnn, tvm4j |
| GPU | 3 | opencl, vulkan |
| Image | 3 | sd-jni, mediapipe |
| Speech | 9 | kaldi, sherpa, piper, sentencepiece |
| React Native | 10 | hermes, jsi, reanimated |
| Storage | 2 | leveldb, mmkv |
| Animation | 2 | rive, cubism |
| Cognitive | 2 | laylaQNN, ucd |
| Performance | 3 | openblas, openmp, spdlog |

### 8.2 JNI Interfaces (`app/src/main/kotlin/org/ninelym/jni/`)

| File | Purpose | Native Binding |
|------|---------|----------------|
| `CTranslate2Jni.kt` | Translation | M2M100, OPUS models |
| `LlamaCppJni.kt` | LLM inference | LLaMA model interface |
| `SDJni.kt` | Image generation | Stable Diffusion interface |

### 8.3 Database (`app/src/main/kotlin/org/ninelym/db/`)

| File | Purpose | Features |
|------|---------|----------|
| `VectorDatabase.kt` | Vector storage | LVDB-compatible, similarity search |

### 8.4 Optimization (`app/src/main/kotlin/org/ninelym/optimization/`)

| File | Purpose | Strategies |
|------|---------|------------|
| `MemoryOptimizer.kt` | Memory management | Weak references, pools, GC triggers |
| `PerformanceMonitor.kt` | Metrics | Operation timing, memory tracking |

### 8.5 MLC Engine (`app/src/main/kotlin/org/ninelym/mlc/`)

| File | Purpose | Backend |
|------|---------|---------|
| `MLCChatEngine.kt` | Chat engine | TVM-based inference |

### 8.6 Executorch (`app/src/main/kotlin/org/ninelym/executorch/`)

| File | Purpose | Framework |
|------|---------|-----------|
| `ExecutorchRuntime.kt` | Model runtime | PyTorch Executorch |

### 8.7 Tokenizer (`app/src/main/kotlin/org/ninelym/tokenizer/`)

| File | Purpose | Models |
|------|---------|--------|
| `TokenizerEngine.kt` | Tokenization | BPE, SentencePiece |

### 8.8 Animation (`app/src/main/kotlin/org/ninelym/animation/`)

| File | Purpose | Library |
|------|---------|---------|
| `CubismEngine.kt` | Character animation | Live2D Cubism SDK |

---

## 9. Python Personality System

### 9.1 Core Modules (`python/helpers/`)

| File | Purpose | Key Classes |
|------|---------|-------------|
| `toga_personality.py` | Main personality | `TogaPersonality`, `TogaPersonalityTensor`, `TogaEmotionalState` |
| `toga_emotional_intelligence.py` | Emotional AI | `EmotionalIntelligenceEngine`, `EmotionalContext`, `EmpatheticResponse` |
| `toga_config.py` | Configuration | Settings management, validation |
| `toga_logging.py` | Logging | Telemetry, structured logging |
| `toga_performance.py` | Performance | Metrics, benchmarking |
| `toga_context.py` | Context | Conversation context management |
| `toga_evolution.py` | Evolution | Trait evolution, inheritance |

### 9.2 Examples (`python/examples/`)

| File | Purpose | Usage |
|------|---------|-------|
| `demo_toga.py` | Interactive demo | `python -m python.examples.demo_toga` |
| `integration_example.py` | Integration patterns | API usage examples |
| `benchmark_toga.py` | Performance testing | Benchmark suite |

### Key Data Types

| Type | File | Purpose |
|------|------|---------|
| `TogaPersonalityTensor` | toga_personality.py | 8 mutable traits + 3 immutable ethical constraints |
| `TogaEmotionalState` | toga_personality.py | 7 emotional states with intensity/duration |
| `EmotionCategory` | toga_emotional_intelligence.py | 8 emotion categories (Plutchik's wheel) |
| `SentimentPolarity` | toga_emotional_intelligence.py | 5-level sentiment scale |
| `EmotionalContext` | toga_emotional_intelligence.py | Analyzed emotional context |
| `EmpatheticResponse` | toga_emotional_intelligence.py | Generated empathetic response |

---

## 10. Test Files

### 10.1 Kotlin Tests (`app/src/test/kotlin/`)

| File | Purpose | Coverage |
|------|---------|----------|
| `TestUtils.kt` | Test utilities | Helper functions |
| `Phase7Test.kt` | Phase 7 tests | Integration testing |

### 10.2 Python Tests (`tests/`)

| File | Purpose | Assertions |
|------|---------|------------|
| `test_toga_config.py` | Config tests | Settings validation |
| `test_toga_emotional_intelligence.py` | EI tests | Emotion detection, empathy |
| `test_toga_logging.py` | Logging tests | Log formatting, levels |
| `test_toga_performance.py` | Performance tests | Metrics, benchmarks |
| `test_toga_personality.py` | Personality tests | Trait evolution, framing |
| `test_toga_evolution.py` | Evolution tests | Trait inheritance |

---

## 11. Documentation

### 11.1 Main Documentation (`docs/`)

| File | Purpose | Content |
|------|---------|---------|
| `TECHNICAL_ARCHITECTURE.md` | System architecture | 39KB comprehensive overview |
| `SYSTEM_COMPONENTS.md` | Component details | Service interactions |
| `API_INTEGRATION.md` | API patterns | Integration guidelines |
| `SMALI_ARCHITECTURE_ANALYSIS.md` | Reverse engineering | APK structure analysis |
| `SMALI_DETAILED_ANALYSIS.md` | Deep analysis | 11KB bytecode analysis |
| `COGNITIVE_COMPUTING.md` | AI/ML architecture | Cognitive system design |
| `CAUSAL_REASONING_ENGINE.md` | Causal AI | Pearl's hierarchy implementation |
| `PLANETARY_NEURAL_NETWORK.md` | Distributed cognition | Global-scale neural network |
| `REFERENCE_CONTEXT.md` | AI capabilities | System capabilities reference |
| `LAYLA_DEVELOPMENT_ROADMAP.md` | Roadmap | 22KB feature roadmap |
| `TOGA_WONDERFUL_DESIGN.md` | Character design | Personality system design |
| `FORMAL_SPECIFICATION.md` | Z++ spec | Formal mathematical specification |
| `ARCHITECTURE_DIAGRAMS.md` | Mermaid diagrams | Visual architecture |
| `FILE_FUNCTIONALITY_CATALOG.md` | This file | Complete file catalog |

### 11.2 Phase Summaries (`archive/`)

| File | Phase | Content |
|------|-------|---------|
| `LAYLA_PHASE1_SUMMARY.md` | Phase 1 | Foundation (6 services, 21 tests) |
| `LAYLA_PHASE2_SUMMARY.md` | Phase 2 | Enhancement (6 services, 62 tests) |
| `LAYLA_PHASE3_SUMMARY.md` | Phase 3 | Integration (5 services, 35 tests) |
| `LAYLA_PHASE4_SUMMARY.md` | Phase 4 | Polish & Scale (4 services) |
| `LAYLA_PHASE5_SUMMARY.md` | Phase 5 | Meta-Cognition (3 services) |
| `LAYLA_PHASE6_SUMMARY.md` | Phase 6 | Testing & Unification (3 services) |

---

## 12. Build and CI/CD

### 12.1 Gradle Configuration

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Root build |
| `app/build.gradle.kts` | App module build |
| `settings.gradle.kts` | Project settings |
| `gradle.properties` | Gradle properties |
| `gradle/wrapper/gradle-wrapper.properties` | Wrapper config |

### 12.2 CI Workflows (`.github/workflows/`)

| File | Trigger | Actions |
|------|---------|---------|
| `ci.yml` | Push/PR | Main CI pipeline |
| `android-ci.yml` | Push/PR | Android-specific CI |
| `tests.yml` | Push/PR | Comprehensive tests |
| `code-quality.yml` | Push/PR | Linting, type checking |
| `release.yml` | Tag | Release automation |
| `generate-cognitive-project.yml` | Manual | Project generation |

---

## 13. GitHub Configuration

### 13.1 Agent Personas (`.github/agents/`)

| File | Persona | Role |
|------|---------|------|
| `toga.md` | Himiko Toga | Main character personality |
| `layla.md` | Layla | AI Assistant persona |
| `neuro-sama.md` | Neuro-Sama | VTuber AI integration |
| `aria.md` | Aria | Creative companion |
| `marcus.md` | Marcus | Analyst persona |
| (30+ more) | Various | Additional AI personas |

### 13.2 Issue Templates (`.github/ISSUE_TEMPLATE/`)

| File | Type |
|------|------|
| `bug_report.md` | Bug reports |
| `feature_request.md` | Feature requests |
| `question.md` | Questions |

---

## Summary Statistics

| Category | Count |
|----------|-------|
| Kotlin Source Files | 120+ |
| Python Source Files | 12 |
| Test Files | 8 |
| Documentation Files | 25+ |
| Native Libraries | 87 |
| CI Workflows | 6 |
| Agent Personas | 35+ |
| Total Services | 27 |
| Total Lines of Code | ~15,000+ |

---

## File Naming Conventions

| Pattern | Meaning |
|---------|---------|
| `*Service.kt` | Service class |
| `*Engine.kt` | Processing engine |
| `*Processor.kt` | Data processor |
| `*Manager.kt` | State/resource manager |
| `*Demo.kt` | Demonstration code |
| `*Test.kt` | Test file |
| `test_*.py` | Python test file |
| `toga_*.py` | Toga personality module |
| `LAYLA_*.md` | Layla documentation |
| `*_demo.sh` | Demo script |

---

*End of File Functionality Catalog*

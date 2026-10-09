package org.ninelym.cognitive

import org.ninelym.cognitive.hypergraph.Hypergraph
import org.ninelym.cognitive.hypergraph.Atom
import org.ninelym.cognitive.hypergraph.AtomType
import org.ninelym.cognitive.ecan.ECANKernel
import org.ninelym.cognitive.selfhealing.*
import org.ninelym.cognitive.distributed.*
import org.ninelym.cognitive.neurosymbolic.*
import kotlinx.coroutines.*

/**
 * Phase 7 Demo: Advanced Cognitive Computing Capabilities
 *
 * Demonstrates the three major new components:
 * 1. Self-Healing Cognitive System - Autonomous anomaly detection and recovery
 * 2. Distributed Cognitive Synchronization - P2P sync with CRDTs
 * 3. Hybrid Neural-Symbolic Bridge - Differentiable logic programming
 *
 * Phase 7 builds upon Phases 1-6 to add production-ready features for:
 * - System reliability through self-healing
 * - Multi-device collaboration through distributed sync
 * - Explainable AI through neural-symbolic integration
 */
class Phase7Demo {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) = runBlocking {
            println("=" .repeat(70))
            println(" PHASE 7: Advanced Cognitive Computing Demo")
            println(" Self-Healing | Distributed Sync | Neural-Symbolic Bridge")
            println("=".repeat(70))
            println()

            val demo = Phase7Demo()

            // Run all demos
            demo.demonstrateSelfHealing()
            println()

            demo.demonstrateDistributedSync()
            println()

            demo.demonstrateNeuralSymbolicBridge()
            println()

            // Summary
            demo.printSummary()
        }
    }

    private val hypergraph = Hypergraph()
    private val cognitiveEngine = CognitiveEngine(hypergraph)
    private val ecanKernel = ECANKernel(hypergraph)

    init {
        // Initialize with some sample data
        initializeSampleData()
    }

    private fun initializeSampleData() {
        // Add sample atoms for demonstration
        val concepts = listOf(
            "consciousness", "intelligence", "learning", "memory",
            "attention", "reasoning", "perception", "emotion"
        )

        concepts.forEach { name ->
            val atom = Atom(
                id = "concept-$name",
                type = AtomType.CONCEPT,
                name = name,
                truthValue = 0.8f
            )
            hypergraph.addAtom(atom)
        }

        // Add relationships
        hypergraph.addRelation("concept-learning", "concept-memory", "requires")
        hypergraph.addRelation("concept-attention", "concept-perception", "enables")
        hypergraph.addRelation("concept-reasoning", "concept-intelligence", "component_of")
    }

    /**
     * Demonstrate Self-Healing Cognitive System
     */
    suspend fun demonstrateSelfHealing() {
        println("-".repeat(60))
        println(" 1. SELF-HEALING COGNITIVE SYSTEM")
        println("-".repeat(60))

        val selfHealing = SelfHealingCognitiveSystem(
            cognitiveEngine = cognitiveEngine,
            hypergraph = hypergraph,
            ecanKernel = ecanKernel,
            config = SelfHealingConfig(
                attentionMonitorIntervalMs = 1000,
                autoRecoveryEnabled = true
            )
        )

        // Subscribe to alerts
        selfHealing.subscribeToAlerts { alert ->
            println("   [ALERT] ${alert.severity}: ${alert.message}")
        }

        println("\n   Starting self-healing monitoring...")
        selfHealing.startMonitoring()

        // Wait for initial monitoring cycle
        delay(2000)

        // Perform manual health check
        println("\n   Performing manual health check...")
        val healthCheck = selfHealing.performHealthCheck()

        println("\n   Health Check Results:")
        println("   - Duration: ${healthCheck.duration}ms")
        println("   - Anomalies Detected: ${healthCheck.anomaliesDetected}")
        println("   - Repairs Attempted: ${healthCheck.repairsAttempted}")
        println("   - Repairs Successful: ${healthCheck.repairsSuccessful}")
        println("   - Overall Health: ${String.format("%.2f", healthCheck.overallHealth * 100)}%")

        // Get current status
        val status = selfHealing.getHealthStatus()
        println("\n   System Health Status:")
        println("   - State: ${status.status}")
        println("   - Overall Health: ${String.format("%.2f", status.overallHealth * 100)}%")
        println("   - Active Anomalies: ${status.activeAnomalies.size}")
        println("   - Total Recoveries: ${status.totalRecoveriesPerformed}")

        // Component health
        println("\n   Component Health:")
        status.componentHealth.forEach { (component, metric) ->
            println("   - $component: ${String.format("%.2f", metric.score * 100)}% (${metric.details})")
        }

        selfHealing.stopMonitoring()
        println("\n   [OK] Self-healing demo completed")
    }

    /**
     * Demonstrate Distributed Cognitive Synchronization
     */
    suspend fun demonstrateDistributedSync() {
        println("-".repeat(60))
        println(" 2. DISTRIBUTED COGNITIVE SYNCHRONIZATION")
        println("-".repeat(60))

        // Create two nodes for demonstration
        val nodeA = DistributedCognitiveSynchronization(
            localNodeId = "node-alpha",
            hypergraph = hypergraph,
            config = SyncConfig(syncIntervalMs = 1000)
        )

        val nodeB = DistributedCognitiveSynchronization(
            localNodeId = "node-beta",
            hypergraph = Hypergraph(), // Separate hypergraph
            config = SyncConfig(syncIntervalMs = 1000)
        )

        println("\n   Initializing distributed nodes...")
        nodeA.startSync()
        nodeB.startSync()

        // Add atom on node A
        println("\n   Adding atom on Node Alpha...")
        val newAtom = Atom(
            id = "distributed-test-${System.currentTimeMillis()}",
            type = AtomType.CONCEPT,
            name = "distributed_knowledge",
            truthValue = 0.9f
        )
        val addResult = nodeA.localAddAtom(newAtom)
        println("   - Result: ${addResult.message}")

        // Update attention
        println("\n   Updating attention value on Node Alpha...")
        val attentionResult = nodeA.localUpdateAttention(newAtom.id, 0.5f)
        println("   - Result: ${attentionResult.message}")

        // Get sync data that would be sent to peer
        val syncData = nodeA.getFullSyncData()
        println("\n   Sync Data Generated:")
        println("   - Source: ${syncData.sourceNodeId}")
        println("   - Atom Operations: ${syncData.atomOperations.size}")
        println("   - Attention Updates: ${syncData.attentionUpdates.size}")
        println("   - Is Delta: ${syncData.isDelta}")

        // Simulate receiving sync data on node B
        println("\n   Simulating sync reception on Node Beta...")
        val mergeResult = nodeB.receiveSyncData("node-alpha", syncData)
        println("   - Merge Success: ${mergeResult.success}")
        println("   - Operations Merged: ${mergeResult.mergedCount}")

        // Get sync status
        val statusA = nodeA.getSyncStatus()
        val statusB = nodeB.getSyncStatus()

        println("\n   Node Alpha Status:")
        println("   - Local Node ID: ${statusA.localNodeId}")
        println("   - Atom Count: ${statusA.atomCount}")
        println("   - Local Operations: ${statusA.statistics.localOperations}")

        println("\n   Node Beta Status:")
        println("   - Local Node ID: ${statusB.localNodeId}")
        println("   - Atom Count: ${statusB.atomCount}")
        println("   - Remote Operations: ${statusB.statistics.remoteOperations}")

        nodeA.stopSync()
        nodeB.stopSync()
        println("\n   [OK] Distributed sync demo completed")
    }

    /**
     * Demonstrate Hybrid Neural-Symbolic Bridge
     */
    suspend fun demonstrateNeuralSymbolicBridge() {
        println("-".repeat(60))
        println(" 3. HYBRID NEURAL-SYMBOLIC BRIDGE")
        println("-".repeat(60))

        val bridge = HybridNeuralSymbolicBridge(
            hypergraph = hypergraph,
            config = BridgeConfig(
                embeddingDimension = 32,
                embeddingIterations = 50,
                learningRate = 0.05f
            )
        )

        // Learn symbol embeddings
        println("\n   Learning symbol embeddings from hypergraph...")
        val embeddingResult = bridge.learnSymbolEmbeddings()
        println("   - Symbols Learned: ${embeddingResult.symbolsLearned}")
        println("   - Final Loss: ${String.format("%.4f", embeddingResult.finalLoss)}")

        // Neural to Symbolic conversion
        println("\n   Testing Neural to Symbolic conversion...")
        val testTensor = CognitiveTensor(
            modality = 0.7f,
            depth = 0.5f,
            context = 0.8f,
            salience = 0.6f,
            autonomyIndex = 0.5f
        )
        val symbolic = bridge.neuralToSymbolic(testTensor)
        println("   - Direct Matches: ${symbolic.directMatches.size}")
        println("   - Inferred Symbols: ${symbolic.inferredSymbols.size}")
        if (symbolic.directMatches.isNotEmpty()) {
            println("   - Top Match: ${symbolic.directMatches.first().atom.name} " +
                   "(similarity: ${String.format("%.3f", symbolic.directMatches.first().similarity)})")
        }

        // Symbolic to Neural conversion
        println("\n   Testing Symbolic to Neural conversion...")
        val atomIds = listOf("concept-learning", "concept-memory", "concept-intelligence")
        val neural = bridge.symbolicToNeural(atomIds)
        println("   - Input Atoms: ${atomIds.size}")
        println("   - Output Tensor: [${neural.modality}, ${neural.depth}, ${neural.context}, ${neural.salience}]")

        // Learn a concept
        println("\n   Learning concept 'cognitive_process'...")
        val conceptResult = bridge.learnConcept(
            conceptName = "cognitive_process",
            positiveExamples = listOf(
                listOf("concept-learning", "concept-memory"),
                listOf("concept-reasoning", "concept-attention"),
                listOf("concept-perception", "concept-learning")
            ),
            negativeExamples = listOf(
                listOf("concept-emotion")
            )
        )
        println("   - Success: ${conceptResult.success}")
        println("   - Rules Induced: ${conceptResult.rulesInduced}")

        // Classify an example
        println("\n   Classifying new example...")
        val classification = bridge.classifyExample(
            example = listOf("concept-learning", "concept-reasoning")
        )
        println("   - Primary Concept: ${classification.primaryConcept ?: "None"}")
        println("   - Confidence: ${String.format("%.3f", classification.confidence)}")
        if (classification.topConcepts.isNotEmpty()) {
            println("   - Neural Score: ${String.format("%.3f", classification.topConcepts.first().neuralScore)}")
            println("   - Symbolic Score: ${String.format("%.3f", classification.topConcepts.first().symbolicScore)}")
        }

        // Add logic rules for inference
        println("\n   Adding logic rules for inference...")
        bridge.addLogicRule(LogicRule(
            id = "rule-learning-requires-memory",
            premises = listOf("concept-memory"),
            conclusion = "concept-learning",
            initialWeight = 0.5f
        ))
        bridge.addLogicRule(LogicRule(
            id = "rule-intelligence-from-reasoning",
            premises = listOf("concept-reasoning", "concept-learning"),
            conclusion = "concept-intelligence",
            initialWeight = 0.5f
        ))

        // Perform differentiable inference
        println("\n   Performing differentiable inference...")
        val query = InferenceQuery(
            knownFacts = listOf("concept-memory", "concept-reasoning"),
            goal = "concept-intelligence"
        )
        val inferenceResult = bridge.differentiableInference(query)
        println("   - Query Goal: ${query.goal}")
        println("   - Known Facts: ${query.knownFacts}")
        println("   - Results Found: ${inferenceResult.totalInferences}")

        if (inferenceResult.results.isNotEmpty()) {
            val bestResult = inferenceResult.results.first()
            println("   - Goal Achieved: ${bestResult.achieved}")
            println("   - Confidence: ${String.format("%.3f", bestResult.confidence)}")
            println("   - Inference Steps: ${bestResult.path.size}")

            // Generate explanation
            val explanation = bridge.explainInference(bestResult)
            println("\n   Explanation:")
            println("   ${explanation.summary}")
            explanation.steps.forEach { step ->
                println("   Step ${step.stepNumber}: ${step.rule}")
            }
        }

        // Get bridge statistics
        val stats = bridge.getStatistics()
        println("\n   Bridge Statistics:")
        println("   - Symbol Embeddings: ${stats.symbolEmbeddingsCount}")
        println("   - Logic Rules: ${stats.logicRulesCount}")
        println("   - Concepts Learned: ${stats.conceptsLearned}")
        println("   - Embedding Dimension: ${stats.embeddingDimension}")

        println("\n   [OK] Neural-symbolic bridge demo completed")
    }

    /**
     * Print summary of Phase 7 capabilities
     */
    private fun printSummary() {
        println("=".repeat(70))
        println(" PHASE 7 SUMMARY: Advanced Cognitive Computing")
        println("=".repeat(70))
        println()
        println(" NEW CAPABILITIES:")
        println()
        println(" 1. Self-Healing Cognitive System")
        println("    - Continuous health monitoring")
        println("    - Automatic anomaly detection")
        println("    - Autonomous recovery mechanisms")
        println("    - Configurable alert thresholds")
        println()
        println(" 2. Distributed Cognitive Synchronization")
        println("    - CRDT-based conflict resolution")
        println("    - Vector clock causality tracking")
        println("    - Delta synchronization for efficiency")
        println("    - Eventual consistency guarantees")
        println()
        println(" 3. Hybrid Neural-Symbolic Bridge")
        println("    - Bidirectional neural-symbolic conversion")
        println("    - Differentiable logic programming")
        println("    - Concept learning from examples")
        println("    - Explainable inference with traces")
        println()
        println(" INTEGRATION WITH PREVIOUS PHASES:")
        println(" - Phase 1-3: Core services and document processing")
        println(" - Phase 4: Performance optimization and privacy")
        println(" - Phase 5: Meta-cognitive pathways")
        println(" - Phase 6: Cognitive unification")
        println(" - Phase 7: Production reliability and advanced AI")
        println()
        println("=".repeat(70))
        println(" Phase 7 Complete - Production-Ready Cognitive Computing")
        println("=".repeat(70))
    }
}

// Extension function for adding relations to hypergraph
private fun Hypergraph.addRelation(sourceId: String, targetId: String, relationType: String) {
    val relationAtom = Atom(
        id = "relation-$sourceId-$targetId-$relationType",
        type = AtomType.LINK,
        name = relationType,
        truthValue = 0.9f
    )
    this.addAtom(relationAtom)
}

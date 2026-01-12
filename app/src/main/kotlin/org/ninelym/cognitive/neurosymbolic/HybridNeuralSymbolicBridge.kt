package org.ninelym.cognitive.neurosymbolic

import org.ninelym.cognitive.CognitiveTensor
import org.ninelym.cognitive.hypergraph.Hypergraph
import org.ninelym.cognitive.hypergraph.Atom
import org.ninelym.cognitive.hypergraph.AtomType
import kotlin.math.*

/**
 * Hybrid Neural-Symbolic Bridge - Phase 4C Advanced AI Integration
 *
 * Combines symbolic reasoning with neural learning through:
 * - Neural-symbolic integration layer
 * - Differentiable logic programming
 * - Concept learning from examples
 * - Explainable AI through symbolic traces
 *
 * The bridge enables seamless bidirectional transformation between
 * neural representations (tensors) and symbolic representations (atoms).
 *
 * Architecture:
 * ```
 * Neural Layer (Tensors) ←→ Bridge ←→ Symbolic Layer (Atoms)
 *        ↓                    ↕                  ↓
 *   Embeddings         Differentiable      Logic Rules
 *   Gradients          Logic Programs      Inferences
 * ```
 */
class HybridNeuralSymbolicBridge(
    private val hypergraph: Hypergraph,
    private val config: BridgeConfig = BridgeConfig()
) {

    // Symbol embeddings (learned representations)
    private val symbolEmbeddings = mutableMapOf<String, FloatArray>()

    // Logic rule weights (differentiable)
    private val ruleWeights = mutableMapOf<String, DifferentiableWeight>()

    // Concept prototypes
    private val conceptPrototypes = mutableMapOf<String, ConceptPrototype>()

    // Inference trace for explainability
    private val inferenceTraces = mutableListOf<InferenceTrace>()

    /**
     * Learn symbol embeddings from hypergraph structure
     */
    fun learnSymbolEmbeddings(
        iterations: Int = config.embeddingIterations,
        learningRate: Float = config.learningRate
    ): EmbeddingLearningResult {
        val atoms = hypergraph.getAllAtoms()
        if (atoms.isEmpty()) {
            return EmbeddingLearningResult(0, 0f, "No atoms to learn from")
        }

        // Initialize embeddings randomly
        atoms.forEach { atom ->
            if (!symbolEmbeddings.containsKey(atom.id)) {
                symbolEmbeddings[atom.id] = randomEmbedding(config.embeddingDimension)
            }
        }

        var totalLoss = 0f

        // Skip-gram style learning
        repeat(iterations) { iter ->
            var iterLoss = 0f

            atoms.forEach { atom ->
                // Get neighbors (linked atoms)
                val neighbors = hypergraph.getNeighbors(atom.id)

                neighbors.forEach { neighborId ->
                    val embedding = symbolEmbeddings[atom.id] ?: return@forEach
                    val neighborEmbedding = symbolEmbeddings[neighborId] ?: return@forEach

                    // Positive sample loss (should be similar)
                    val similarity = cosineSimilarity(embedding, neighborEmbedding)
                    val positiveLoss = -log(sigmoid(similarity) + 1e-10f)

                    // Negative sampling
                    val negativeSamples = sampleNegatives(atom.id, atoms, config.negativeSamples)
                    var negativeLoss = 0f
                    negativeSamples.forEach { negId ->
                        val negEmbedding = symbolEmbeddings[negId] ?: return@forEach
                        val negSim = cosineSimilarity(embedding, negEmbedding)
                        negativeLoss += -log(sigmoid(-negSim) + 1e-10f)
                    }

                    iterLoss += positiveLoss + negativeLoss

                    // Gradient update (simplified)
                    updateEmbeddings(atom.id, neighborId, negativeSamples, learningRate)
                }
            }

            totalLoss = iterLoss / atoms.size
        }

        return EmbeddingLearningResult(
            symbolsLearned = symbolEmbeddings.size,
            finalLoss = totalLoss,
            message = "Learned embeddings for ${symbolEmbeddings.size} symbols"
        )
    }

    /**
     * Neural to Symbolic: Convert tensor representation to symbolic atoms
     */
    fun neuralToSymbolic(
        tensor: CognitiveTensor,
        threshold: Float = config.symbolizationThreshold
    ): SymbolicRepresentation {
        val tensorArray = tensor.toArray()
        val matchedSymbols = mutableListOf<SymbolMatch>()

        // Find closest symbol embeddings
        symbolEmbeddings.forEach { (symbolId, embedding) ->
            val similarity = cosineSimilarity(tensorArray, embedding)
            if (similarity >= threshold) {
                val atom = hypergraph.getAtom(symbolId)
                if (atom != null) {
                    matchedSymbols.add(SymbolMatch(
                        symbolId = symbolId,
                        atom = atom,
                        similarity = similarity,
                        confidence = calculateConfidence(similarity, threshold)
                    ))
                }
            }
        }

        // Sort by similarity
        matchedSymbols.sortByDescending { it.similarity }

        // Infer additional symbols through logic rules
        val inferred = inferSymbols(matchedSymbols.map { it.symbolId })

        return SymbolicRepresentation(
            directMatches = matchedSymbols.take(config.maxDirectMatches),
            inferredSymbols = inferred,
            originalTensor = tensor,
            totalSymbols = matchedSymbols.size + inferred.size
        )
    }

    /**
     * Symbolic to Neural: Convert symbolic atoms to tensor representation
     */
    fun symbolicToNeural(
        atomIds: List<String>,
        aggregation: AggregationType = AggregationType.ATTENTION_WEIGHTED
    ): CognitiveTensor {
        val embeddings = atomIds.mapNotNull { symbolEmbeddings[it] }

        if (embeddings.isEmpty()) {
            return CognitiveTensor() // Default tensor
        }

        val aggregated = when (aggregation) {
            AggregationType.MEAN -> aggregateMean(embeddings)
            AggregationType.MAX -> aggregateMax(embeddings)
            AggregationType.ATTENTION_WEIGHTED -> aggregateAttentionWeighted(atomIds, embeddings)
        }

        return CognitiveTensor.fromArray(aggregated)
    }

    /**
     * Learn a concept from positive and negative examples
     */
    fun learnConcept(
        conceptName: String,
        positiveExamples: List<List<String>>,
        negativeExamples: List<List<String>> = emptyList()
    ): ConceptLearningResult {
        if (positiveExamples.isEmpty()) {
            return ConceptLearningResult(false, "No positive examples provided")
        }

        // Extract common features from positive examples
        val positiveEmbeddings = positiveExamples.map { example ->
            symbolicToNeural(example).toArray()
        }

        val prototype = FloatArray(config.embeddingDimension) { dim ->
            positiveEmbeddings.map { it.getOrElse(dim) { 0f } }.average().toFloat()
        }

        // Refine with negative examples
        if (negativeExamples.isNotEmpty()) {
            val negativeEmbeddings = negativeExamples.map { example ->
                symbolicToNeural(example).toArray()
            }

            // Move prototype away from negative examples
            val negativeCentroid = FloatArray(config.embeddingDimension) { dim ->
                negativeEmbeddings.map { it.getOrElse(dim) { 0f } }.average().toFloat()
            }

            for (i in prototype.indices) {
                prototype[i] = prototype[i] * 1.5f - negativeCentroid[i] * 0.5f
            }

            normalize(prototype)
        }

        // Induce symbolic rules from examples
        val rules = induceRules(positiveExamples, negativeExamples)

        conceptPrototypes[conceptName] = ConceptPrototype(
            name = conceptName,
            embedding = prototype,
            rules = rules,
            positiveCount = positiveExamples.size,
            negativeCount = negativeExamples.size
        )

        return ConceptLearningResult(
            success = true,
            conceptName = conceptName,
            rulesInduced = rules.size,
            message = "Learned concept '$conceptName' with ${rules.size} rules"
        )
    }

    /**
     * Classify an example using learned concepts
     */
    fun classifyExample(
        example: List<String>,
        topK: Int = 3
    ): ClassificationResult {
        val exampleEmbedding = symbolicToNeural(example).toArray()
        val scores = mutableListOf<ConceptScore>()

        conceptPrototypes.forEach { (conceptName, prototype) ->
            // Neural similarity
            val neuralScore = cosineSimilarity(exampleEmbedding, prototype.embedding)

            // Symbolic rule matching
            val symbolicScore = evaluateRules(example, prototype.rules)

            // Combined score
            val combinedScore = config.neuralWeight * neuralScore +
                              config.symbolicWeight * symbolicScore

            scores.add(ConceptScore(
                conceptName = conceptName,
                neuralScore = neuralScore,
                symbolicScore = symbolicScore,
                combinedScore = combinedScore
            ))
        }

        scores.sortByDescending { it.combinedScore }

        return ClassificationResult(
            example = example,
            topConcepts = scores.take(topK),
            primaryConcept = scores.firstOrNull()?.conceptName,
            confidence = scores.firstOrNull()?.combinedScore ?: 0f
        )
    }

    /**
     * Add a differentiable logic rule
     */
    fun addLogicRule(rule: LogicRule): Boolean {
        val ruleId = rule.id
        if (ruleWeights.containsKey(ruleId)) return false

        ruleWeights[ruleId] = DifferentiableWeight(
            ruleId = ruleId,
            weight = rule.initialWeight,
            gradient = 0f,
            uses = 0
        )

        return true
    }

    /**
     * Perform differentiable inference
     */
    fun differentiableInference(
        query: InferenceQuery,
        maxDepth: Int = config.maxInferenceDepth
    ): DifferentiableInferenceResult {
        inferenceTraces.clear()

        val results = mutableListOf<InferenceResult>()
        val visited = mutableSetOf<String>()

        // Forward chaining with soft logic
        val agenda = mutableListOf(InferenceState(
            facts = query.knownFacts.toMutableSet(),
            confidence = 1.0f,
            depth = 0,
            path = emptyList()
        ))

        while (agenda.isNotEmpty() && results.size < config.maxResults) {
            val state = agenda.removeAt(0)
            if (state.depth >= maxDepth) continue

            ruleWeights.forEach { (ruleId, weight) ->
                val rule = findRule(ruleId) ?: return@forEach

                // Check if rule premises are satisfied
                val (satisfied, premiseConfidence) = checkPremises(rule.premises, state.facts)

                if (satisfied && state.facts.none { it == rule.conclusion }) {
                    // Apply rule with soft confidence
                    val ruleConfidence = sigmoid(weight.weight)
                    val newConfidence = state.confidence * premiseConfidence * ruleConfidence

                    if (newConfidence >= config.confidenceThreshold) {
                        val newFacts = state.facts.toMutableSet()
                        newFacts.add(rule.conclusion)

                        val newPath = state.path + RuleApplication(
                            ruleId = ruleId,
                            premises = rule.premises,
                            conclusion = rule.conclusion,
                            confidence = newConfidence
                        )

                        // Check if we reached the goal
                        if (rule.conclusion == query.goal) {
                            results.add(InferenceResult(
                                goal = query.goal,
                                achieved = true,
                                confidence = newConfidence,
                                path = newPath
                            ))

                            // Record trace for explainability
                            inferenceTraces.add(InferenceTrace(
                                query = query,
                                result = results.last(),
                                timestamp = System.currentTimeMillis()
                            ))
                        }

                        // Continue inference
                        if (state.depth + 1 < maxDepth) {
                            agenda.add(InferenceState(
                                facts = newFacts,
                                confidence = newConfidence,
                                depth = state.depth + 1,
                                path = newPath
                            ))
                        }

                        // Update rule usage for gradient computation
                        weight.uses++
                    }
                }
            }
        }

        return DifferentiableInferenceResult(
            query = query,
            results = results,
            totalInferences = results.size,
            traces = inferenceTraces.toList()
        )
    }

    /**
     * Update rule weights using gradient descent
     */
    fun updateRuleWeights(
        feedback: List<InferenceFeedback>,
        learningRate: Float = config.ruleLearningRate
    ): WeightUpdateResult {
        var totalUpdate = 0f

        feedback.forEach { fb ->
            fb.ruleApplications.forEach { app ->
                val weight = ruleWeights[app.ruleId] ?: return@forEach

                // Compute gradient based on feedback
                val target = if (fb.correct) 1f else 0f
                val predicted = sigmoid(weight.weight)
                val gradient = (target - predicted) * predicted * (1 - predicted)

                // Update weight
                weight.gradient = gradient
                weight.weight += learningRate * gradient
                totalUpdate += abs(gradient)
            }
        }

        return WeightUpdateResult(
            rulesUpdated = feedback.flatMap { it.ruleApplications }.distinctBy { it.ruleId }.size,
            totalGradientMagnitude = totalUpdate,
            message = "Updated rule weights"
        )
    }

    /**
     * Generate explanation for an inference
     */
    fun explainInference(inferenceResult: InferenceResult): Explanation {
        val steps = inferenceResult.path.mapIndexed { index, app ->
            ExplanationStep(
                stepNumber = index + 1,
                rule = "If ${app.premises.joinToString(" AND ")} then ${app.conclusion}",
                confidence = app.confidence,
                reasoning = "Applied rule '${app.ruleId}' with confidence ${app.confidence}"
            )
        }

        return Explanation(
            goal = inferenceResult.goal,
            achieved = inferenceResult.achieved,
            steps = steps,
            overallConfidence = inferenceResult.confidence,
            summary = generateSummary(inferenceResult)
        )
    }

    /**
     * Get bridge statistics
     */
    fun getStatistics(): BridgeStatistics {
        return BridgeStatistics(
            symbolEmbeddingsCount = symbolEmbeddings.size,
            logicRulesCount = ruleWeights.size,
            conceptsLearned = conceptPrototypes.size,
            inferenceTracesCount = inferenceTraces.size,
            averageRuleWeight = ruleWeights.values.map { it.weight }.average().toFloat(),
            embeddingDimension = config.embeddingDimension
        )
    }

    // ==================== Private Implementation ====================

    private val storedRules = mutableMapOf<String, LogicRule>()

    private fun findRule(ruleId: String): LogicRule? = storedRules[ruleId]

    private fun randomEmbedding(dimension: Int): FloatArray {
        return FloatArray(dimension) { (Math.random().toFloat() - 0.5f) * 2f / sqrt(dimension.toFloat()) }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val minLen = minOf(a.size, b.size)

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in 0 until minLen) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    private fun sampleNegatives(
        positiveId: String,
        allAtoms: List<Atom>,
        count: Int
    ): List<String> {
        val neighbors = hypergraph.getNeighbors(positiveId).toSet()
        return allAtoms
            .filter { it.id != positiveId && it.id !in neighbors }
            .shuffled()
            .take(count)
            .map { it.id }
    }

    private fun updateEmbeddings(
        centerId: String,
        neighborId: String,
        negatives: List<String>,
        lr: Float
    ) {
        val center = symbolEmbeddings[centerId] ?: return
        val neighbor = symbolEmbeddings[neighborId] ?: return

        // Positive update
        for (i in center.indices) {
            val grad = neighbor[i] - center[i]
            center[i] += lr * grad * 0.5f
            neighbor[i] += lr * (-grad) * 0.5f
        }

        // Negative updates
        negatives.forEach { negId ->
            val neg = symbolEmbeddings[negId] ?: return@forEach
            for (i in center.indices) {
                val grad = neg[i] - center[i]
                center[i] -= lr * grad * 0.1f
            }
        }

        normalize(center)
        normalize(neighbor)
    }

    private fun normalize(array: FloatArray) {
        val norm = sqrt(array.sumOf { (it * it).toDouble() }.toFloat())
        if (norm > 0) {
            for (i in array.indices) {
                array[i] /= norm
            }
        }
    }

    private fun calculateConfidence(similarity: Float, threshold: Float): Float {
        return ((similarity - threshold) / (1 - threshold)).coerceIn(0f, 1f)
    }

    private fun inferSymbols(matchedIds: List<String>): List<InferredSymbol> {
        val inferred = mutableListOf<InferredSymbol>()

        // Use logic rules to infer additional symbols
        ruleWeights.forEach { (ruleId, weight) ->
            val rule = storedRules[ruleId] ?: return@forEach

            val (satisfied, confidence) = checkPremises(rule.premises, matchedIds.toSet())
            if (satisfied) {
                val inferenceConfidence = confidence * sigmoid(weight.weight)
                inferred.add(InferredSymbol(
                    symbolId = rule.conclusion,
                    confidence = inferenceConfidence,
                    inferenceRule = ruleId,
                    supportingSymbols = matchedIds.filter { it in rule.premises }
                ))
            }
        }

        return inferred.distinctBy { it.symbolId }
    }

    private fun checkPremises(
        premises: List<String>,
        facts: Set<String>
    ): Pair<Boolean, Float> {
        if (premises.isEmpty()) return Pair(true, 1f)

        var confidence = 1f
        for (premise in premises) {
            if (premise in facts) {
                // Direct match
                continue
            }

            // Soft match using embeddings
            val premiseEmb = symbolEmbeddings[premise]
            if (premiseEmb != null) {
                val maxSim = facts.mapNotNull { symbolEmbeddings[it] }
                    .maxOfOrNull { cosineSimilarity(premiseEmb, it) } ?: 0f

                if (maxSim >= config.softMatchThreshold) {
                    confidence *= maxSim
                } else {
                    return Pair(false, 0f)
                }
            } else {
                return Pair(false, 0f)
            }
        }

        return Pair(true, confidence)
    }

    private fun aggregateMean(embeddings: List<FloatArray>): FloatArray {
        val dim = embeddings.first().size
        return FloatArray(dim) { d ->
            embeddings.map { it.getOrElse(d) { 0f } }.average().toFloat()
        }
    }

    private fun aggregateMax(embeddings: List<FloatArray>): FloatArray {
        val dim = embeddings.first().size
        return FloatArray(dim) { d ->
            embeddings.map { it.getOrElse(d) { 0f } }.maxOrNull() ?: 0f
        }
    }

    private fun aggregateAttentionWeighted(
        atomIds: List<String>,
        embeddings: List<FloatArray>
    ): FloatArray {
        // Use attention values from hypergraph as weights
        val attentions = atomIds.map { id ->
            hypergraph.getAtom(id)?.attentionValue?.sti ?: 0.5f
        }

        val totalAttention = attentions.sum()
        val weights = if (totalAttention > 0) attentions.map { it / totalAttention }
                      else List(attentions.size) { 1f / attentions.size }

        val dim = embeddings.first().size
        return FloatArray(dim) { d ->
            embeddings.mapIndexed { i, emb ->
                (emb.getOrElse(d) { 0f }) * weights[i]
            }.sum()
        }
    }

    private fun induceRules(
        positives: List<List<String>>,
        negatives: List<List<String>>
    ): List<InducedRule> {
        val rules = mutableListOf<InducedRule>()

        // Find common patterns in positive examples
        if (positives.size < 2) return rules

        val commonElements = positives.reduce { acc, list ->
            acc.filter { it in list }
        }

        // For each common element, create a rule
        commonElements.forEach { element ->
            val otherElements = positives.first().filter { it != element }
            if (otherElements.isNotEmpty()) {
                val rule = InducedRule(
                    id = "induced-${element}-${System.currentTimeMillis()}",
                    premises = otherElements.take(2),
                    conclusion = element,
                    confidence = 0.8f,
                    support = positives.size
                )
                rules.add(rule)

                // Also add to stored rules and weights
                val logicRule = LogicRule(
                    id = rule.id,
                    premises = rule.premises,
                    conclusion = rule.conclusion,
                    initialWeight = 0f
                )
                storedRules[rule.id] = logicRule
                addLogicRule(logicRule)
            }
        }

        return rules
    }

    private fun evaluateRules(
        example: List<String>,
        rules: List<InducedRule>
    ): Float {
        if (rules.isEmpty()) return 0f

        val scores = rules.map { rule ->
            val premisesSatisfied = rule.premises.count { it in example }
            val premiseRatio = premisesSatisfied.toFloat() / rule.premises.size
            premiseRatio * rule.confidence
        }

        return scores.average().toFloat()
    }

    private fun generateSummary(result: InferenceResult): String {
        return if (result.achieved) {
            "Goal '${result.goal}' was achieved through ${result.path.size} inference steps " +
            "with overall confidence ${String.format("%.2f", result.confidence)}"
        } else {
            "Goal '${result.goal}' could not be achieved from the given facts"
        }
    }
}

// ==================== Data Classes ====================

/**
 * Bridge configuration
 */
data class BridgeConfig(
    val embeddingDimension: Int = 64,
    val embeddingIterations: Int = 100,
    val learningRate: Float = 0.01f,
    val ruleLearningRate: Float = 0.1f,
    val negativeSamples: Int = 5,
    val symbolizationThreshold: Float = 0.5f,
    val softMatchThreshold: Float = 0.7f,
    val confidenceThreshold: Float = 0.1f,
    val maxDirectMatches: Int = 10,
    val maxInferenceDepth: Int = 5,
    val maxResults: Int = 10,
    val neuralWeight: Float = 0.6f,
    val symbolicWeight: Float = 0.4f
)

/**
 * Result of embedding learning
 */
data class EmbeddingLearningResult(
    val symbolsLearned: Int,
    val finalLoss: Float,
    val message: String
)

/**
 * Symbol match from neural to symbolic conversion
 */
data class SymbolMatch(
    val symbolId: String,
    val atom: Atom,
    val similarity: Float,
    val confidence: Float
)

/**
 * Inferred symbol through logic rules
 */
data class InferredSymbol(
    val symbolId: String,
    val confidence: Float,
    val inferenceRule: String,
    val supportingSymbols: List<String>
)

/**
 * Symbolic representation from tensor
 */
data class SymbolicRepresentation(
    val directMatches: List<SymbolMatch>,
    val inferredSymbols: List<InferredSymbol>,
    val originalTensor: CognitiveTensor,
    val totalSymbols: Int
)

/**
 * Aggregation types for symbolic to neural conversion
 */
enum class AggregationType {
    MEAN,
    MAX,
    ATTENTION_WEIGHTED
}

/**
 * Concept prototype
 */
data class ConceptPrototype(
    val name: String,
    val embedding: FloatArray,
    val rules: List<InducedRule>,
    val positiveCount: Int,
    val negativeCount: Int
)

/**
 * Induced rule from examples
 */
data class InducedRule(
    val id: String,
    val premises: List<String>,
    val conclusion: String,
    val confidence: Float,
    val support: Int
)

/**
 * Result of concept learning
 */
data class ConceptLearningResult(
    val success: Boolean,
    val conceptName: String? = null,
    val rulesInduced: Int = 0,
    val message: String
)

/**
 * Concept score in classification
 */
data class ConceptScore(
    val conceptName: String,
    val neuralScore: Float,
    val symbolicScore: Float,
    val combinedScore: Float
)

/**
 * Classification result
 */
data class ClassificationResult(
    val example: List<String>,
    val topConcepts: List<ConceptScore>,
    val primaryConcept: String?,
    val confidence: Float
)

/**
 * Logic rule for inference
 */
data class LogicRule(
    val id: String,
    val premises: List<String>,
    val conclusion: String,
    val initialWeight: Float = 0f
)

/**
 * Differentiable weight for a rule
 */
data class DifferentiableWeight(
    val ruleId: String,
    var weight: Float,
    var gradient: Float,
    var uses: Int
)

/**
 * Inference query
 */
data class InferenceQuery(
    val knownFacts: List<String>,
    val goal: String
)

/**
 * Internal inference state
 */
data class InferenceState(
    val facts: MutableSet<String>,
    val confidence: Float,
    val depth: Int,
    val path: List<RuleApplication>
)

/**
 * Rule application record
 */
data class RuleApplication(
    val ruleId: String,
    val premises: List<String>,
    val conclusion: String,
    val confidence: Float
)

/**
 * Inference result
 */
data class InferenceResult(
    val goal: String,
    val achieved: Boolean,
    val confidence: Float,
    val path: List<RuleApplication>
)

/**
 * Differentiable inference result
 */
data class DifferentiableInferenceResult(
    val query: InferenceQuery,
    val results: List<InferenceResult>,
    val totalInferences: Int,
    val traces: List<InferenceTrace>
)

/**
 * Inference trace for explainability
 */
data class InferenceTrace(
    val query: InferenceQuery,
    val result: InferenceResult,
    val timestamp: Long
)

/**
 * Feedback for rule weight updates
 */
data class InferenceFeedback(
    val correct: Boolean,
    val ruleApplications: List<RuleApplication>
)

/**
 * Result of weight updates
 */
data class WeightUpdateResult(
    val rulesUpdated: Int,
    val totalGradientMagnitude: Float,
    val message: String
)

/**
 * Explanation step
 */
data class ExplanationStep(
    val stepNumber: Int,
    val rule: String,
    val confidence: Float,
    val reasoning: String
)

/**
 * Full explanation
 */
data class Explanation(
    val goal: String,
    val achieved: Boolean,
    val steps: List<ExplanationStep>,
    val overallConfidence: Float,
    val summary: String
)

/**
 * Bridge statistics
 */
data class BridgeStatistics(
    val symbolEmbeddingsCount: Int,
    val logicRulesCount: Int,
    val conceptsLearned: Int,
    val inferenceTracesCount: Int,
    val averageRuleWeight: Float,
    val embeddingDimension: Int
)

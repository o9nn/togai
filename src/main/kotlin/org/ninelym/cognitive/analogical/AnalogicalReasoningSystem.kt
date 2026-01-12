package org.ninelym.cognitive.analogical

import org.ninelym.cognitive.hypergraph.Hypergraph
import org.ninelym.cognitive.hypergraph.Atom
import org.ninelym.cognitive.hypergraph.AtomType
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Analogical Reasoning System - Phase 4C: Advanced AI Integration
 *
 * Implements structure mapping theory (Gentner) for analogical reasoning:
 * - Structure Mapping Engine (SME) for finding correspondences
 * - Analogical transfer learning across domains
 * - Cross-domain pattern recognition
 * - Creative problem solving through analogies
 * - Retrieval of relevant analogies from memory
 *
 * Enables the cognitive system to transfer knowledge from known domains
 * to novel situations, supporting creative thinking and problem-solving.
 */
class AnalogicalReasoningSystem(
    private val hypergraph: Hypergraph
) {

    private val analogyMemory = ConcurrentHashMap<String, StructuredDomain>()
    private val mappingCache = ConcurrentHashMap<String, AnalogicalMapping>()

    /**
     * Represents a structured domain for analogical mapping
     */
    data class StructuredDomain(
        val id: String,
        val name: String,
        val entities: List<Entity>,
        val relations: List<Relation>,
        val attributes: List<Attribute>,
        val higherOrderRelations: List<HigherOrderRelation> = emptyList()
    )

    /**
     * Entity in a domain
     */
    data class Entity(
        val id: String,
        val name: String,
        val type: String,
        val properties: Map<String, Any> = emptyMap()
    )

    /**
     * First-order relation between entities
     */
    data class Relation(
        val id: String,
        val predicate: String,
        val arguments: List<String>, // Entity IDs
        val weight: Float = 1.0f
    )

    /**
     * Attribute of an entity
     */
    data class Attribute(
        val id: String,
        val predicate: String,
        val entityId: String,
        val value: Any
    )

    /**
     * Higher-order relation (relation about relations)
     */
    data class HigherOrderRelation(
        val id: String,
        val predicate: String,
        val relationIds: List<String>,
        val weight: Float = 1.0f
    )

    /**
     * Result of analogical mapping
     */
    data class AnalogicalMapping(
        val id: String,
        val baseDomain: String,
        val targetDomain: String,
        val entityMappings: Map<String, String>, // base entity -> target entity
        val relationMappings: Map<String, String>, // base relation -> target relation
        val structuralScore: Float,
        val systematicityScore: Float,
        val similarityScore: Float,
        val overallScore: Float,
        val inferences: List<TransferredInference>,
        val timestamp: Long
    )

    /**
     * Inference transferred from base to target
     */
    data class TransferredInference(
        val id: String,
        val type: InferenceType,
        val basePredicate: String,
        val targetPredicate: String,
        val arguments: List<String>,
        val confidence: Float,
        val justification: String
    )

    enum class InferenceType {
        CANDIDATE_INFERENCE,    // Novel prediction
        PROJECTED_ATTRIBUTE,    // Attribute transfer
        STRUCTURAL_COMPLETION   // Missing structure
    }

    /**
     * Analogy retrieval result
     */
    data class RetrievalResult(
        val analogies: List<RankedAnalogy>,
        val retrievalTime: Long
    )

    data class RankedAnalogy(
        val domain: StructuredDomain,
        val surfaceSimilarity: Float,
        val structuralSimilarity: Float,
        val overallScore: Float
    )

    // ============================================
    // DOMAIN MANAGEMENT
    // ============================================

    /**
     * Register a domain for analogical reasoning
     */
    fun registerDomain(domain: StructuredDomain) {
        analogyMemory[domain.id] = domain

        // Also store in hypergraph for integration
        integrateDomainToHypergraph(domain)
    }

    /**
     * Create domain from entities and relations
     */
    fun createDomain(
        name: String,
        entities: List<Entity>,
        relations: List<Relation>,
        attributes: List<Attribute> = emptyList()
    ): StructuredDomain {
        val domain = StructuredDomain(
            id = "domain-${System.currentTimeMillis()}-${name.hashCode()}",
            name = name,
            entities = entities,
            relations = relations,
            attributes = attributes
        )

        registerDomain(domain)
        return domain
    }

    /**
     * Integrate domain into hypergraph
     */
    private fun integrateDomainToHypergraph(domain: StructuredDomain) {
        // Add entities as concept nodes
        domain.entities.forEach { entity ->
            val atom = Atom(
                id = "${domain.id}:entity:${entity.id}",
                type = AtomType.CONCEPT_NODE,
                name = entity.name,
                truthValue = 1.0f
            )
            hypergraph.addAtom(atom)
        }

        // Add relations as evaluation links
        domain.relations.forEach { relation ->
            val atom = Atom(
                id = "${domain.id}:relation:${relation.id}",
                type = AtomType.EVALUATION_LINK,
                name = relation.predicate,
                truthValue = relation.weight
            )
            hypergraph.addAtom(atom)
        }
    }

    // ============================================
    // STRUCTURE MAPPING ENGINE (SME)
    // ============================================

    /**
     * Find analogical mapping between two domains
     *
     * Implements Gentner's Structure Mapping Engine algorithm:
     * 1. Local match: Find potential correspondences
     * 2. Structural consistency: Build consistent mapping
     * 3. Systematicity: Prefer mappings with higher-order structure
     */
    fun findAnalogy(
        baseDomain: StructuredDomain,
        targetDomain: StructuredDomain
    ): AnalogicalMapping {
        // Step 1: Local match - find candidate entity and relation mappings
        val entityMatches = findEntityMatches(baseDomain, targetDomain)
        val relationMatches = findRelationMatches(baseDomain, targetDomain)

        // Step 2: Build structurally consistent mapping
        val (entityMappings, relationMappings) = buildConsistentMapping(
            baseDomain, targetDomain,
            entityMatches, relationMatches
        )

        // Step 3: Calculate scores
        val structuralScore = calculateStructuralScore(
            baseDomain, targetDomain, entityMappings, relationMappings
        )
        val systematicityScore = calculateSystematicityScore(
            baseDomain, targetDomain, relationMappings
        )
        val similarityScore = calculateSimilarityScore(
            baseDomain, targetDomain, entityMappings
        )

        // Step 4: Generate inferences
        val inferences = generateInferences(
            baseDomain, targetDomain, entityMappings, relationMappings
        )

        val mapping = AnalogicalMapping(
            id = "mapping-${baseDomain.id}-${targetDomain.id}",
            baseDomain = baseDomain.id,
            targetDomain = targetDomain.id,
            entityMappings = entityMappings,
            relationMappings = relationMappings,
            structuralScore = structuralScore,
            systematicityScore = systematicityScore,
            similarityScore = similarityScore,
            overallScore = (structuralScore * 0.4f + systematicityScore * 0.4f + similarityScore * 0.2f),
            inferences = inferences,
            timestamp = System.currentTimeMillis()
        )

        // Cache the mapping
        mappingCache[mapping.id] = mapping

        return mapping
    }

    /**
     * Find potential entity matches based on type and attributes
     */
    private fun findEntityMatches(
        base: StructuredDomain,
        target: StructuredDomain
    ): Map<String, List<Pair<String, Float>>> {
        val matches = mutableMapOf<String, MutableList<Pair<String, Float>>>()

        base.entities.forEach { baseEntity ->
            val candidates = mutableListOf<Pair<String, Float>>()

            target.entities.forEach { targetEntity ->
                val typeScore = if (baseEntity.type == targetEntity.type) 1.0f else 0.5f
                val propertyScore = calculatePropertySimilarity(
                    baseEntity.properties, targetEntity.properties
                )
                val score = typeScore * 0.3f + propertyScore * 0.7f

                if (score > 0.3f) {
                    candidates.add(targetEntity.id to score)
                }
            }

            matches[baseEntity.id] = candidates.sortedByDescending { it.second }.toMutableList()
        }

        return matches
    }

    /**
     * Find potential relation matches based on predicate similarity
     */
    private fun findRelationMatches(
        base: StructuredDomain,
        target: StructuredDomain
    ): Map<String, List<Pair<String, Float>>> {
        val matches = mutableMapOf<String, MutableList<Pair<String, Float>>>()

        base.relations.forEach { baseRelation ->
            val candidates = mutableListOf<Pair<String, Float>>()

            target.relations.forEach { targetRelation ->
                // Check predicate similarity
                val predicateScore = if (baseRelation.predicate == targetRelation.predicate) {
                    1.0f
                } else {
                    calculatePredicateSimilarity(baseRelation.predicate, targetRelation.predicate)
                }

                // Check arity match
                val arityScore = if (baseRelation.arguments.size == targetRelation.arguments.size) {
                    1.0f
                } else {
                    0.0f
                }

                val score = predicateScore * 0.6f + arityScore * 0.4f

                if (score > 0.3f) {
                    candidates.add(targetRelation.id to score)
                }
            }

            matches[baseRelation.id] = candidates.sortedByDescending { it.second }.toMutableList()
        }

        return matches
    }

    /**
     * Build structurally consistent mapping using constraint satisfaction
     */
    private fun buildConsistentMapping(
        base: StructuredDomain,
        target: StructuredDomain,
        entityMatches: Map<String, List<Pair<String, Float>>>,
        relationMatches: Map<String, List<Pair<String, Float>>>
    ): Pair<Map<String, String>, Map<String, String>> {
        val entityMappings = mutableMapOf<String, String>()
        val relationMappings = mutableMapOf<String, String>()
        val usedTargetEntities = mutableSetOf<String>()
        val usedTargetRelations = mutableSetOf<String>()

        // Greedy assignment with structural consistency
        // First, assign relations (structure-first approach)
        relationMatches.entries
            .sortedByDescending { it.value.firstOrNull()?.second ?: 0f }
            .forEach { (baseRelId, candidates) ->
                val baseRelation = base.relations.find { it.id == baseRelId } ?: return@forEach

                for ((targetRelId, _) in candidates) {
                    if (targetRelId in usedTargetRelations) continue

                    val targetRelation = target.relations.find { it.id == targetRelId } ?: continue

                    // Check if entity mappings are consistent
                    val canMap = baseRelation.arguments.zip(targetRelation.arguments).all { (baseArg, targetArg) ->
                        val existingMapping = entityMappings[baseArg]
                        existingMapping == null || existingMapping == targetArg
                    }

                    if (canMap) {
                        relationMappings[baseRelId] = targetRelId
                        usedTargetRelations.add(targetRelId)

                        // Update entity mappings
                        baseRelation.arguments.zip(targetRelation.arguments).forEach { (baseArg, targetArg) ->
                            if (baseArg !in entityMappings && targetArg !in usedTargetEntities) {
                                entityMappings[baseArg] = targetArg
                                usedTargetEntities.add(targetArg)
                            }
                        }
                        break
                    }
                }
            }

        // Fill in remaining entity mappings
        entityMatches.entries
            .filter { it.key !in entityMappings }
            .sortedByDescending { it.value.firstOrNull()?.second ?: 0f }
            .forEach { (baseEntId, candidates) ->
                for ((targetEntId, _) in candidates) {
                    if (targetEntId !in usedTargetEntities) {
                        entityMappings[baseEntId] = targetEntId
                        usedTargetEntities.add(targetEntId)
                        break
                    }
                }
            }

        return entityMappings to relationMappings
    }

    /**
     * Calculate structural score based on mapped relations
     */
    private fun calculateStructuralScore(
        base: StructuredDomain,
        target: StructuredDomain,
        entityMappings: Map<String, String>,
        relationMappings: Map<String, String>
    ): Float {
        if (base.relations.isEmpty()) return 0f

        val mappedRelations = relationMappings.size.toFloat()
        val totalRelations = base.relations.size.toFloat()

        // Check structural consistency of mappings
        var consistentMappings = 0
        relationMappings.forEach { (baseRelId, targetRelId) ->
            val baseRel = base.relations.find { it.id == baseRelId }
            val targetRel = target.relations.find { it.id == targetRelId }

            if (baseRel != null && targetRel != null) {
                val argsConsistent = baseRel.arguments.zip(targetRel.arguments).all { (baseArg, targetArg) ->
                    entityMappings[baseArg] == targetArg
                }
                if (argsConsistent) consistentMappings++
            }
        }

        val coverage = mappedRelations / totalRelations
        val consistency = if (relationMappings.isNotEmpty()) {
            consistentMappings.toFloat() / relationMappings.size
        } else 0f

        return coverage * 0.5f + consistency * 0.5f
    }

    /**
     * Calculate systematicity score (preference for higher-order relations)
     */
    private fun calculateSystematicityScore(
        base: StructuredDomain,
        target: StructuredDomain,
        relationMappings: Map<String, String>
    ): Float {
        if (base.higherOrderRelations.isEmpty()) {
            // No higher-order relations, use relation chain depth
            return calculateRelationChainDepth(base, relationMappings)
        }

        // Count mapped higher-order relations
        val mappedHigherOrder = base.higherOrderRelations.count { hor ->
            hor.relationIds.all { it in relationMappings }
        }

        return if (base.higherOrderRelations.isNotEmpty()) {
            mappedHigherOrder.toFloat() / base.higherOrderRelations.size
        } else {
            0f
        }
    }

    /**
     * Calculate depth of relation chains (proxy for systematicity)
     */
    private fun calculateRelationChainDepth(
        domain: StructuredDomain,
        relationMappings: Map<String, String>
    ): Float {
        // Find connected components in mapped relations
        val mappedRelations = domain.relations.filter { it.id in relationMappings }

        if (mappedRelations.isEmpty()) return 0f

        // Build adjacency for connected relations
        val entityToRelations = mutableMapOf<String, MutableList<String>>()
        mappedRelations.forEach { rel ->
            rel.arguments.forEach { arg ->
                entityToRelations.getOrPut(arg) { mutableListOf() }.add(rel.id)
            }
        }

        // Calculate average connectivity
        val avgConnectivity = entityToRelations.values
            .map { it.size.toFloat() }
            .average()
            .toFloat()

        return (avgConnectivity / 3f).coerceIn(0f, 1f)
    }

    /**
     * Calculate surface similarity based on attributes
     */
    private fun calculateSimilarityScore(
        base: StructuredDomain,
        target: StructuredDomain,
        entityMappings: Map<String, String>
    ): Float {
        if (entityMappings.isEmpty()) return 0f

        var totalSimilarity = 0f
        var count = 0

        entityMappings.forEach { (baseId, targetId) ->
            val baseEntity = base.entities.find { it.id == baseId }
            val targetEntity = target.entities.find { it.id == targetId }

            if (baseEntity != null && targetEntity != null) {
                totalSimilarity += calculatePropertySimilarity(
                    baseEntity.properties, targetEntity.properties
                )
                count++
            }
        }

        return if (count > 0) totalSimilarity / count else 0f
    }

    /**
     * Calculate property similarity between two property maps
     */
    private fun calculatePropertySimilarity(
        props1: Map<String, Any>,
        props2: Map<String, Any>
    ): Float {
        if (props1.isEmpty() && props2.isEmpty()) return 1f
        if (props1.isEmpty() || props2.isEmpty()) return 0.5f

        val allKeys = props1.keys + props2.keys
        val matchingKeys = props1.keys.intersect(props2.keys)

        var matchScore = 0f
        matchingKeys.forEach { key ->
            val val1 = props1[key]
            val val2 = props2[key]
            matchScore += if (val1 == val2) 1f else 0.5f
        }

        return matchScore / allKeys.size
    }

    /**
     * Calculate predicate similarity using simple string matching
     */
    private fun calculatePredicateSimilarity(pred1: String, pred2: String): Float {
        // Simplified - in production would use word embeddings or ontology
        val words1 = pred1.lowercase().split(Regex("[_\\s-]")).toSet()
        val words2 = pred2.lowercase().split(Regex("[_\\s-]")).toSet()

        val intersection = words1.intersect(words2).size.toFloat()
        val union = (words1 + words2).size.toFloat()

        return if (union > 0) intersection / union else 0f
    }

    // ============================================
    // INFERENCE GENERATION
    // ============================================

    /**
     * Generate inferences by transferring unmapped structure
     */
    private fun generateInferences(
        base: StructuredDomain,
        target: StructuredDomain,
        entityMappings: Map<String, String>,
        relationMappings: Map<String, String>
    ): List<TransferredInference> {
        val inferences = mutableListOf<TransferredInference>()

        // Find unmapped relations in base that could transfer to target
        base.relations
            .filter { it.id !in relationMappings }
            .forEach { baseRelation ->
                // Check if all arguments are mapped
                val mappedArgs = baseRelation.arguments.mapNotNull { entityMappings[it] }

                if (mappedArgs.size == baseRelation.arguments.size) {
                    // All arguments mapped - can generate candidate inference
                    val confidence = baseRelation.weight * 0.7f // Reduce confidence for transfer

                    inferences.add(
                        TransferredInference(
                            id = "inference-${baseRelation.id}-${System.currentTimeMillis()}",
                            type = InferenceType.CANDIDATE_INFERENCE,
                            basePredicate = baseRelation.predicate,
                            targetPredicate = baseRelation.predicate,
                            arguments = mappedArgs,
                            confidence = confidence,
                            justification = "Transferred from ${base.name} via structural analogy"
                        )
                    )
                }
            }

        // Transfer attributes
        base.attributes
            .filter { attr -> entityMappings.containsKey(attr.entityId) }
            .forEach { baseAttr ->
                val targetEntityId = entityMappings[baseAttr.entityId]!!

                // Check if target already has this attribute
                val targetHasAttr = target.attributes.any {
                    it.entityId == targetEntityId && it.predicate == baseAttr.predicate
                }

                if (!targetHasAttr) {
                    inferences.add(
                        TransferredInference(
                            id = "inference-attr-${baseAttr.id}-${System.currentTimeMillis()}",
                            type = InferenceType.PROJECTED_ATTRIBUTE,
                            basePredicate = baseAttr.predicate,
                            targetPredicate = baseAttr.predicate,
                            arguments = listOf(targetEntityId),
                            confidence = 0.6f,
                            justification = "Attribute projected from corresponding entity in ${base.name}"
                        )
                    )
                }
            }

        return inferences
    }

    // ============================================
    // ANALOGY RETRIEVAL
    // ============================================

    /**
     * Retrieve relevant analogies from memory
     */
    fun retrieveAnalogies(
        targetDomain: StructuredDomain,
        maxResults: Int = 5
    ): RetrievalResult {
        val startTime = System.currentTimeMillis()

        val rankedAnalogies = analogyMemory.values
            .filter { it.id != targetDomain.id }
            .map { baseDomain ->
                val surfaceSim = calculateSurfaceSimilarity(baseDomain, targetDomain)
                val structuralSim = calculateQuickStructuralSimilarity(baseDomain, targetDomain)
                val overallScore = structuralSim * 0.7f + surfaceSim * 0.3f

                RankedAnalogy(baseDomain, surfaceSim, structuralSim, overallScore)
            }
            .sortedByDescending { it.overallScore }
            .take(maxResults)

        return RetrievalResult(
            analogies = rankedAnalogies,
            retrievalTime = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Calculate surface similarity for retrieval
     */
    private fun calculateSurfaceSimilarity(
        base: StructuredDomain,
        target: StructuredDomain
    ): Float {
        // Compare entity types
        val baseTypes = base.entities.map { it.type }.toSet()
        val targetTypes = target.entities.map { it.type }.toSet()
        val typeSim = baseTypes.intersect(targetTypes).size.toFloat() /
                max(baseTypes.size, targetTypes.size).toFloat().coerceAtLeast(1f)

        // Compare predicates
        val basePredicates = base.relations.map { it.predicate }.toSet()
        val targetPredicates = target.relations.map { it.predicate }.toSet()
        val predSim = basePredicates.intersect(targetPredicates).size.toFloat() /
                max(basePredicates.size, targetPredicates.size).toFloat().coerceAtLeast(1f)

        return typeSim * 0.5f + predSim * 0.5f
    }

    /**
     * Quick structural similarity estimate for retrieval
     */
    private fun calculateQuickStructuralSimilarity(
        base: StructuredDomain,
        target: StructuredDomain
    ): Float {
        // Compare relation patterns
        val baseArity = base.relations.groupBy { it.arguments.size }
        val targetArity = target.relations.groupBy { it.arguments.size }

        var aritySim = 0f
        val allArities = baseArity.keys + targetArity.keys
        allArities.forEach { arity ->
            val baseCount = baseArity[arity]?.size ?: 0
            val targetCount = targetArity[arity]?.size ?: 0
            if (baseCount > 0 && targetCount > 0) {
                aritySim += min(baseCount, targetCount).toFloat() / max(baseCount, targetCount)
            }
        }
        aritySim /= allArities.size.toFloat().coerceAtLeast(1f)

        // Compare complexity
        val baseComplexity = base.relations.size + base.entities.size
        val targetComplexity = target.relations.size + target.entities.size
        val complexitySim = min(baseComplexity, targetComplexity).toFloat() /
                max(baseComplexity, targetComplexity).toFloat().coerceAtLeast(1f)

        return aritySim * 0.6f + complexitySim * 0.4f
    }

    // ============================================
    // CREATIVE PROBLEM SOLVING
    // ============================================

    /**
     * Solve problem by analogy
     *
     * Given a target problem, find relevant analogies and transfer solutions
     */
    fun solveByAnalogy(
        problemDomain: StructuredDomain,
        solutionPredicate: String
    ): AnalogicalSolution {
        // Retrieve relevant analogies
        val retrieval = retrieveAnalogies(problemDomain, maxResults = 3)

        if (retrieval.analogies.isEmpty()) {
            return AnalogicalSolution(
                problem = problemDomain.name,
                solutionFound = false,
                mapping = null,
                transferredSolution = emptyList(),
                confidence = 0f,
                sourceAnalogy = null
            )
        }

        // Try each analogy in order of relevance
        for (rankedAnalogy in retrieval.analogies) {
            val baseDomain = rankedAnalogy.domain

            // Check if base has the solution structure
            val solutionRelations = baseDomain.relations.filter {
                it.predicate.contains(solutionPredicate, ignoreCase = true)
            }

            if (solutionRelations.isNotEmpty()) {
                // Found a potential source - compute full mapping
                val mapping = findAnalogy(baseDomain, problemDomain)

                if (mapping.overallScore > 0.5f) {
                    // Transfer solution
                    val transferredSolution = mapping.inferences.filter {
                        it.basePredicate.contains(solutionPredicate, ignoreCase = true)
                    }

                    return AnalogicalSolution(
                        problem = problemDomain.name,
                        solutionFound = transferredSolution.isNotEmpty(),
                        mapping = mapping,
                        transferredSolution = transferredSolution,
                        confidence = mapping.overallScore * rankedAnalogy.overallScore,
                        sourceAnalogy = baseDomain.name
                    )
                }
            }
        }

        return AnalogicalSolution(
            problem = problemDomain.name,
            solutionFound = false,
            mapping = null,
            transferredSolution = emptyList(),
            confidence = 0f,
            sourceAnalogy = null
        )
    }

    data class AnalogicalSolution(
        val problem: String,
        val solutionFound: Boolean,
        val mapping: AnalogicalMapping?,
        val transferredSolution: List<TransferredInference>,
        val confidence: Float,
        val sourceAnalogy: String?
    )

    // ============================================
    // UTILITY FUNCTIONS
    // ============================================

    /**
     * Get stored mapping
     */
    fun getMapping(mappingId: String): AnalogicalMapping? = mappingCache[mappingId]

    /**
     * Get all stored domains
     */
    fun getAllDomains(): List<StructuredDomain> = analogyMemory.values.toList()

    /**
     * Get domain by ID
     */
    fun getDomain(domainId: String): StructuredDomain? = analogyMemory[domainId]

    /**
     * Clear analogy memory
     */
    fun clearMemory() {
        analogyMemory.clear()
        mappingCache.clear()
    }

    /**
     * Get statistics
     */
    fun getStats(): AnalogicalReasoningStats {
        return AnalogicalReasoningStats(
            domainsStored = analogyMemory.size,
            mappingsCached = mappingCache.size,
            totalEntities = analogyMemory.values.sumOf { it.entities.size },
            totalRelations = analogyMemory.values.sumOf { it.relations.size }
        )
    }

    data class AnalogicalReasoningStats(
        val domainsStored: Int,
        val mappingsCached: Int,
        val totalEntities: Int,
        val totalRelations: Int
    )
}

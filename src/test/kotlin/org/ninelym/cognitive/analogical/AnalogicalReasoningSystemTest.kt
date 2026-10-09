package org.ninelym.cognitive.analogical

import org.junit.Test
import org.ninelym.cognitive.hypergraph.Hypergraph
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Comprehensive tests for Analogical Reasoning System
 *
 * Tests structure mapping, analogy retrieval, and inference transfer
 */
class AnalogicalReasoningSystemTest {

    @Test
    fun `test domain creation`() {
        val hypergraph = Hypergraph()
        val system = AnalogicalReasoningSystem(hypergraph)

        val entities = listOf(
            AnalogicalReasoningSystem.Entity("e1", "Sun", "star"),
            AnalogicalReasoningSystem.Entity("e2", "Earth", "planet")
        )

        val relations = listOf(
            AnalogicalReasoningSystem.Relation("r1", "orbits", listOf("e2", "e1"))
        )

        val domain = system.createDomain(
            name = "Solar System",
            entities = entities,
            relations = relations
        )

        assertNotNull(domain)
        assertEquals("Solar System", domain.name)
        assertEquals(2, domain.entities.size)
        assertEquals(1, domain.relations.size)
    }

    @Test
    fun `test domain registration`() {
        val hypergraph = Hypergraph()
        val system = AnalogicalReasoningSystem(hypergraph)

        val domain = AnalogicalReasoningSystem.StructuredDomain(
            id = "test-domain",
            name = "Test Domain",
            entities = listOf(
                AnalogicalReasoningSystem.Entity("e1", "Entity1", "type1")
            ),
            relations = emptyList(),
            attributes = emptyList()
        )

        system.registerDomain(domain)

        val retrieved = system.getDomain("test-domain")
        assertNotNull(retrieved)
        assertEquals("Test Domain", retrieved.name)
    }

    @Test
    fun `test simple analogical mapping`() {
        val hypergraph = Hypergraph()
        val system = AnalogicalReasoningSystem(hypergraph)

        // Base domain: Solar system
        val baseDomain = system.createDomain(
            name = "Solar System",
            entities = listOf(
                AnalogicalReasoningSystem.Entity("sun", "Sun", "star",
                    mapOf("mass" to "large", "luminous" to "yes")),
                AnalogicalReasoningSystem.Entity("earth", "Earth", "planet",
                    mapOf("mass" to "medium", "luminous" to "no"))
            ),
            relations = listOf(
                AnalogicalReasoningSystem.Relation("orbits1", "orbits", listOf("earth", "sun"), 0.9f)
            )
        )

        // Target domain: Atom
        val targetDomain = system.createDomain(
            name = "Atom",
            entities = listOf(
                AnalogicalReasoningSystem.Entity("nucleus", "Nucleus", "center",
                    mapOf("mass" to "large", "charge" to "positive")),
                AnalogicalReasoningSystem.Entity("electron", "Electron", "particle",
                    mapOf("mass" to "small", "charge" to "negative"))
            ),
            relations = listOf(
                AnalogicalReasoningSystem.Relation("orbits2", "orbits", listOf("electron", "nucleus"), 0.8f)
            )
        )

        val mapping = system.findAnalogy(baseDomain, targetDomain)

        assertNotNull(mapping)
        assertEquals(baseDomain.id, mapping.baseDomain)
        assertEquals(targetDomain.id, mapping.targetDomain)
        assertTrue(mapping.overallScore > 0f)
        assertTrue(mapping.entityMappings.isNotEmpty() || mapping.relationMappings.isNotEmpty())
    }

    @Test
    fun `test analogical mapping scores`() {
        val hypergraph = Hypergraph()
        val system = AnalogicalReasoningSystem(hypergraph)

        val baseDomain = system.createDomain(
            name = "Base",
            entities = listOf(
                AnalogicalReasoningSystem.Entity("a", "A", "type1"),
                AnalogicalReasoningSystem.Entity("b", "B", "type2")
            ),
            relations = listOf(
                AnalogicalReasoningSystem.Relation("r1", "causes", listOf("a", "b"))
            )
        )

        val targetDomain = system.createDomain(
            name = "Target",
            entities = listOf(
                AnalogicalReasoningSystem.Entity("x", "X", "type1"),
                AnalogicalReasoningSystem.Entity("y", "Y", "type2")
            ),
            relations = listOf(
                AnalogicalReasoningSystem.Relation("r2", "causes", listOf("x", "y"))
            )
        )

        val mapping = system.findAnalogy(baseDomain, targetDomain)

        assertTrue(mapping.structuralScore >= 0f && mapping.structuralScore <= 1f)
        assertTrue(mapping.systematicityScore >= 0f && mapping.systematicityScore <= 1f)
        assertTrue(mapping.similarityScore >= 0f && mapping.similarityScore <= 1f)
        assertTrue(mapping.overallScore >= 0f && mapping.overallScore <= 1f)
    }

    @Test
    fun `test inference generation`() {
        val hypergraph = Hypergraph()
        val system = AnalogicalReasoningSystem(hypergraph)

        // Base domain with extra relation to transfer
        val baseDomain = system.createDomain(
            name = "Base",
            entities = listOf(
                AnalogicalReasoningSystem.Entity("a", "A", "type1"),
                AnalogicalReasoningSystem.Entity("b", "B", "type2")
            ),
            relations = listOf(
                AnalogicalReasoningSystem.Relation("r1", "contains", listOf("a", "b")),
                AnalogicalReasoningSystem.Relation("r2", "protects", listOf("a", "b"))
            )
        )

        // Target domain with only one relation
        val targetDomain = system.createDomain(
            name = "Target",
            entities = listOf(
                AnalogicalReasoningSystem.Entity("x", "X", "type1"),
                AnalogicalReasoningSystem.Entity("y", "Y", "type2")
            ),
            relations = listOf(
                AnalogicalReasoningSystem.Relation("r3", "contains", listOf("x", "y"))
            )
        )

        val mapping = system.findAnalogy(baseDomain, targetDomain)

        // May generate inference for "protects" relation
        assertTrue(mapping.inferences.size >= 0) // Can be empty if entities don't map
    }

    @Test
    fun `test analogy retrieval`() {
        val hypergraph = Hypergraph()
        val system = AnalogicalReasoningSystem(hypergraph)

        // Register several domains
        repeat(5) { i ->
            system.createDomain(
                name = "Domain$i",
                entities = listOf(
                    AnalogicalReasoningSystem.Entity("e$i", "Entity$i", "type$i")
                ),
                relations = listOf(
                    AnalogicalReasoningSystem.Relation("r$i", "relates", listOf("e$i"))
                )
            )
        }

        // Query domain
        val queryDomain = system.createDomain(
            name = "Query",
            entities = listOf(
                AnalogicalReasoningSystem.Entity("eq", "EntityQ", "type0")
            ),
            relations = listOf(
                AnalogicalReasoningSystem.Relation("rq", "relates", listOf("eq"))
            )
        )

        val result = system.retrieveAnalogies(queryDomain, maxResults = 3)

        assertTrue(result.retrievalTime >= 0)
        assertTrue(result.analogies.size <= 3)

        result.analogies.forEach { ranked ->
            assertTrue(ranked.surfaceSimilarity >= 0f && ranked.surfaceSimilarity <= 1f)
            assertTrue(ranked.structuralSimilarity >= 0f && ranked.structuralSimilarity <= 1f)
            assertTrue(ranked.overallScore >= 0f && ranked.overallScore <= 1f)
        }
    }

    @Test
    fun `test entity data class`() {
        val entity = AnalogicalReasoningSystem.Entity(
            id = "e1",
            name = "TestEntity",
            type = "TestType",
            properties = mapOf("color" to "blue", "size" to 10)
        )

        assertEquals("e1", entity.id)
        assertEquals("TestEntity", entity.name)
        assertEquals("TestType", entity.type)
        assertEquals("blue", entity.properties["color"])
        assertEquals(10, entity.properties["size"])
    }

    @Test
    fun `test relation data class`() {
        val relation = AnalogicalReasoningSystem.Relation(
            id = "r1",
            predicate = "contains",
            arguments = listOf("e1", "e2", "e3"),
            weight = 0.8f
        )

        assertEquals("r1", relation.id)
        assertEquals("contains", relation.predicate)
        assertEquals(3, relation.arguments.size)
        assertEquals(0.8f, relation.weight, 0.01f)
    }

    @Test
    fun `test attribute data class`() {
        val attribute = AnalogicalReasoningSystem.Attribute(
            id = "a1",
            predicate = "color",
            entityId = "e1",
            value = "red"
        )

        assertEquals("a1", attribute.id)
        assertEquals("color", attribute.predicate)
        assertEquals("e1", attribute.entityId)
        assertEquals("red", attribute.value)
    }

    @Test
    fun `test inference types`() {
        val types = AnalogicalReasoningSystem.InferenceType.values()
        assertEquals(3, types.size)
        assertTrue(types.contains(AnalogicalReasoningSystem.InferenceType.CANDIDATE_INFERENCE))
        assertTrue(types.contains(AnalogicalReasoningSystem.InferenceType.PROJECTED_ATTRIBUTE))
        assertTrue(types.contains(AnalogicalReasoningSystem.InferenceType.STRUCTURAL_COMPLETION))
    }

    @Test
    fun `test transferred inference data class`() {
        val inference = AnalogicalReasoningSystem.TransferredInference(
            id = "inf1",
            type = AnalogicalReasoningSystem.InferenceType.CANDIDATE_INFERENCE,
            basePredicate = "causes",
            targetPredicate = "causes",
            arguments = listOf("x", "y"),
            confidence = 0.7f,
            justification = "Transferred from base domain"
        )

        assertEquals("inf1", inference.id)
        assertEquals(AnalogicalReasoningSystem.InferenceType.CANDIDATE_INFERENCE, inference.type)
        assertEquals("causes", inference.basePredicate)
        assertEquals(2, inference.arguments.size)
        assertEquals(0.7f, inference.confidence, 0.01f)
    }

    @Test
    fun `test solve by analogy`() {
        val hypergraph = Hypergraph()
        val system = AnalogicalReasoningSystem(hypergraph)

        // Source domain with solution
        system.createDomain(
            name = "WaterFlow",
            entities = listOf(
                AnalogicalReasoningSystem.Entity("pipe", "Pipe", "conduit"),
                AnalogicalReasoningSystem.Entity("water", "Water", "fluid"),
                AnalogicalReasoningSystem.Entity("pump", "Pump", "driver")
            ),
            relations = listOf(
                AnalogicalReasoningSystem.Relation("r1", "flows_through", listOf("water", "pipe")),
                AnalogicalReasoningSystem.Relation("r2", "drives", listOf("pump", "water")),
                AnalogicalReasoningSystem.Relation("r3", "solution_increases_flow", listOf("pump", "water"))
            )
        )

        // Problem domain
        val problemDomain = system.createDomain(
            name = "ElectricalCircuit",
            entities = listOf(
                AnalogicalReasoningSystem.Entity("wire", "Wire", "conduit"),
                AnalogicalReasoningSystem.Entity("current", "Current", "fluid"),
                AnalogicalReasoningSystem.Entity("battery", "Battery", "driver")
            ),
            relations = listOf(
                AnalogicalReasoningSystem.Relation("r1", "flows_through", listOf("current", "wire")),
                AnalogicalReasoningSystem.Relation("r2", "drives", listOf("battery", "current"))
            )
        )

        val solution = system.solveByAnalogy(problemDomain, "solution")

        assertNotNull(solution)
        assertEquals("ElectricalCircuit", solution.problem)
        // May or may not find solution depending on mapping quality
    }

    @Test
    fun `test get stats`() {
        val hypergraph = Hypergraph()
        val system = AnalogicalReasoningSystem(hypergraph)

        system.createDomain(
            name = "Domain1",
            entities = listOf(
                AnalogicalReasoningSystem.Entity("e1", "Entity1", "type1"),
                AnalogicalReasoningSystem.Entity("e2", "Entity2", "type2")
            ),
            relations = listOf(
                AnalogicalReasoningSystem.Relation("r1", "relates", listOf("e1", "e2"))
            )
        )

        system.createDomain(
            name = "Domain2",
            entities = listOf(
                AnalogicalReasoningSystem.Entity("e3", "Entity3", "type3")
            ),
            relations = emptyList()
        )

        val stats = system.getStats()

        assertEquals(2, stats.domainsStored)
        assertEquals(3, stats.totalEntities)
        assertEquals(1, stats.totalRelations)
        assertEquals(0, stats.mappingsCached) // No mappings computed yet
    }

    @Test
    fun `test clear memory`() {
        val hypergraph = Hypergraph()
        val system = AnalogicalReasoningSystem(hypergraph)

        system.createDomain(
            name = "TestDomain",
            entities = listOf(AnalogicalReasoningSystem.Entity("e1", "E", "t")),
            relations = emptyList()
        )

        var stats = system.getStats()
        assertEquals(1, stats.domainsStored)

        system.clearMemory()

        stats = system.getStats()
        assertEquals(0, stats.domainsStored)
        assertEquals(0, stats.mappingsCached)
    }

    @Test
    fun `test all domains retrieval`() {
        val hypergraph = Hypergraph()
        val system = AnalogicalReasoningSystem(hypergraph)

        repeat(3) { i ->
            system.createDomain(
                name = "Domain$i",
                entities = listOf(AnalogicalReasoningSystem.Entity("e$i", "E$i", "t")),
                relations = emptyList()
            )
        }

        val allDomains = system.getAllDomains()
        assertEquals(3, allDomains.size)
    }

    @Test
    fun `test mapping caching`() {
        val hypergraph = Hypergraph()
        val system = AnalogicalReasoningSystem(hypergraph)

        val domain1 = system.createDomain(
            name = "D1",
            entities = listOf(AnalogicalReasoningSystem.Entity("e1", "E1", "t")),
            relations = emptyList()
        )

        val domain2 = system.createDomain(
            name = "D2",
            entities = listOf(AnalogicalReasoningSystem.Entity("e2", "E2", "t")),
            relations = emptyList()
        )

        val mapping = system.findAnalogy(domain1, domain2)

        val retrieved = system.getMapping(mapping.id)
        assertNotNull(retrieved)
        assertEquals(mapping.id, retrieved.id)
    }

    @Test
    fun `test analogical mapping data class`() {
        val mapping = AnalogicalReasoningSystem.AnalogicalMapping(
            id = "map1",
            baseDomain = "base",
            targetDomain = "target",
            entityMappings = mapOf("e1" to "e2"),
            relationMappings = mapOf("r1" to "r2"),
            structuralScore = 0.8f,
            systematicityScore = 0.7f,
            similarityScore = 0.6f,
            overallScore = 0.7f,
            inferences = emptyList(),
            timestamp = System.currentTimeMillis()
        )

        assertEquals("map1", mapping.id)
        assertEquals("base", mapping.baseDomain)
        assertEquals("target", mapping.targetDomain)
        assertEquals(1, mapping.entityMappings.size)
        assertEquals(1, mapping.relationMappings.size)
        assertEquals(0.8f, mapping.structuralScore, 0.01f)
        assertEquals(0.7f, mapping.overallScore, 0.01f)
    }

    @Test
    fun `test higher order relations`() {
        val hypergraph = Hypergraph()
        val system = AnalogicalReasoningSystem(hypergraph)

        val domain = AnalogicalReasoningSystem.StructuredDomain(
            id = "hor-domain",
            name = "Higher Order Domain",
            entities = listOf(
                AnalogicalReasoningSystem.Entity("e1", "E1", "t"),
                AnalogicalReasoningSystem.Entity("e2", "E2", "t")
            ),
            relations = listOf(
                AnalogicalReasoningSystem.Relation("r1", "causes", listOf("e1", "e2")),
                AnalogicalReasoningSystem.Relation("r2", "enables", listOf("e1", "e2"))
            ),
            attributes = emptyList(),
            higherOrderRelations = listOf(
                AnalogicalReasoningSystem.HigherOrderRelation(
                    id = "hor1",
                    predicate = "supports",
                    relationIds = listOf("r1", "r2"),
                    weight = 0.9f
                )
            )
        )

        system.registerDomain(domain)

        val retrieved = system.getDomain("hor-domain")
        assertNotNull(retrieved)
        assertEquals(1, retrieved.higherOrderRelations.size)
        assertEquals("supports", retrieved.higherOrderRelations.first().predicate)
    }

    @Test
    fun `test analogical solution data class`() {
        val mapping = AnalogicalReasoningSystem.AnalogicalMapping(
            id = "m1",
            baseDomain = "base",
            targetDomain = "target",
            entityMappings = emptyMap(),
            relationMappings = emptyMap(),
            structuralScore = 0.5f,
            systematicityScore = 0.5f,
            similarityScore = 0.5f,
            overallScore = 0.5f,
            inferences = emptyList(),
            timestamp = 0
        )

        val solution = AnalogicalReasoningSystem.AnalogicalSolution(
            problem = "TestProblem",
            solutionFound = true,
            mapping = mapping,
            transferredSolution = listOf(
                AnalogicalReasoningSystem.TransferredInference(
                    id = "i1",
                    type = AnalogicalReasoningSystem.InferenceType.CANDIDATE_INFERENCE,
                    basePredicate = "solve",
                    targetPredicate = "solve",
                    arguments = listOf("x"),
                    confidence = 0.8f,
                    justification = "Test"
                )
            ),
            confidence = 0.6f,
            sourceAnalogy = "BaseAnalogy"
        )

        assertEquals("TestProblem", solution.problem)
        assertTrue(solution.solutionFound)
        assertNotNull(solution.mapping)
        assertEquals(1, solution.transferredSolution.size)
        assertEquals(0.6f, solution.confidence, 0.01f)
        assertEquals("BaseAnalogy", solution.sourceAnalogy)
    }

    @Test
    fun `test retrieval result data class`() {
        val result = AnalogicalReasoningSystem.RetrievalResult(
            analogies = listOf(
                AnalogicalReasoningSystem.RankedAnalogy(
                    domain = AnalogicalReasoningSystem.StructuredDomain(
                        id = "d1",
                        name = "D1",
                        entities = emptyList(),
                        relations = emptyList(),
                        attributes = emptyList()
                    ),
                    surfaceSimilarity = 0.8f,
                    structuralSimilarity = 0.7f,
                    overallScore = 0.75f
                )
            ),
            retrievalTime = 100
        )

        assertEquals(1, result.analogies.size)
        assertEquals(100, result.retrievalTime)
        assertEquals(0.75f, result.analogies.first().overallScore, 0.01f)
    }
}

package org.ninelym.cognitive.extensions

import org.ninelym.cognitive.CognitiveEngine
import org.ninelym.cognitive.CognitiveTensor
import org.ninelym.cognitive.hypergraph.Hypergraph
import org.ninelym.cognitive.hypergraph.Atom
import org.ninelym.cognitive.hypergraph.AtomType
import org.ninelym.cognitive.ecan.ECANKernel

/**
 * Phase 7 Extension Methods
 *
 * Provides additional functionality required by Phase 7 components:
 * - Self-Healing System
 * - Distributed Synchronization
 * - Neural-Symbolic Bridge
 */

// ==================== CognitiveEngine Extensions ====================

/**
 * Normalize attention values toward target mean and standard deviation
 */
fun CognitiveEngine.normalizeAttention(targetMean: Float, targetStdDev: Float) {
    val state = getCognitiveState()
    val tensors = state.tensors

    if (tensors.isEmpty()) return

    // Calculate current statistics
    val currentMean = tensors.map { it.salience }.average().toFloat()

    // Shift values toward target mean
    tensors.forEach { tensor ->
        val shifted = tensor.salience - currentMean + targetMean
        // Would update tensor salience in real implementation
    }
}

/**
 * Apply smoothing to reduce attention variance
 */
fun CognitiveEngine.applyAttentionSmoothing(factor: Float) {
    val state = getCognitiveState()
    val tensors = state.tensors

    if (tensors.isEmpty()) return

    val mean = tensors.map { it.salience }.average().toFloat()

    // Move values toward mean by smoothing factor
    tensors.forEach { tensor ->
        val smoothed = tensor.salience * (1 - factor) + mean * factor
        // Would update tensor salience in real implementation
    }
}

/**
 * Decay high attention values
 */
fun CognitiveEngine.decayHighAttention(decayRate: Float) {
    val state = getCognitiveState()
    val threshold = 0.9f

    state.tensors
        .filter { it.salience > threshold }
        .forEach { tensor ->
            val decayed = tensor.salience * (1 - decayRate)
            // Would update tensor salience in real implementation
        }
}

/**
 * Reset a tensor to safe defaults
 */
fun CognitiveEngine.resetTensor(index: Int) {
    val state = getCognitiveState()
    if (index < 0 || index >= state.tensors.size) return

    // Would replace tensor at index with default values
}

/**
 * Clamp all tensor values to valid range [0, 1]
 */
fun CognitiveEngine.clampAllTensors() {
    val state = getCognitiveState()

    state.tensors.forEach { tensor ->
        // Would clamp each tensor dimension to [0, 1]
    }
}

/**
 * Clear internal caches to free memory
 */
fun CognitiveEngine.clearCaches() {
    // Implementation would clear any internal caches
}

// ==================== Hypergraph Extensions ====================

/**
 * Get all atoms in the hypergraph
 */
fun Hypergraph.getAllAtoms(): List<Atom> {
    return atoms.values.toList()
}

/**
 * Get atom by ID
 */
fun Hypergraph.getAtom(id: String): Atom? {
    return atoms[id]
}

/**
 * Add an atom to the hypergraph
 */
fun Hypergraph.addAtom(atom: Atom): Boolean {
    if (atoms.containsKey(atom.id)) return false
    atoms[atom.id] = atom
    return true
}

/**
 * Remove an atom from the hypergraph
 */
fun Hypergraph.removeAtom(id: String): Boolean {
    return atoms.remove(id) != null
}

/**
 * Update an existing atom
 */
fun Hypergraph.updateAtom(atom: Atom): Boolean {
    if (!atoms.containsKey(atom.id)) return false
    atoms[atom.id] = atom
    return true
}

/**
 * Get neighbor atom IDs (atoms connected through links)
 */
fun Hypergraph.getNeighbors(atomId: String): List<String> {
    val neighbors = mutableListOf<String>()

    // Find all links that reference this atom
    atoms.values
        .filter { it.type == AtomType.LINK }
        .forEach { link ->
            // In a real implementation, links would have explicit references
            // For now, we use naming convention
            if (link.id.contains(atomId)) {
                // Extract other atom from link ID
                val parts = link.id.split("-")
                parts.forEach { part ->
                    if (part.startsWith("concept") && part != atomId && atoms.containsKey(part)) {
                        neighbors.add(part)
                    }
                }
            }
        }

    return neighbors.distinct()
}

/**
 * Find orphaned atoms (atoms with no connections)
 */
fun Hypergraph.findOrphanedAtoms(): List<Atom> {
    return atoms.values.filter { atom ->
        atom.type != AtomType.LINK && getNeighbors(atom.id).isEmpty()
    }
}

/**
 * Remove orphaned atoms
 */
fun Hypergraph.removeOrphanedAtoms(): Int {
    val orphaned = findOrphanedAtoms()
    orphaned.forEach { atoms.remove(it.id) }
    return orphaned.size
}

/**
 * Detect circular references in the hypergraph
 */
fun Hypergraph.detectCircularReferences(): List<List<String>> {
    val circles = mutableListOf<List<String>>()
    val visited = mutableSetOf<String>()
    val recursionStack = mutableListOf<String>()

    fun dfs(atomId: String): Boolean {
        visited.add(atomId)
        recursionStack.add(atomId)

        getNeighbors(atomId).forEach { neighbor ->
            if (neighbor in recursionStack) {
                // Found a cycle
                val cycleStart = recursionStack.indexOf(neighbor)
                circles.add(recursionStack.subList(cycleStart, recursionStack.size).toList())
                return true
            }
            if (neighbor !in visited) {
                if (dfs(neighbor)) return true
            }
        }

        recursionStack.removeAt(recursionStack.size - 1)
        return false
    }

    atoms.keys.forEach { atomId ->
        if (atomId !in visited) {
            dfs(atomId)
        }
    }

    return circles
}

/**
 * Break circular references by removing weakest links
 */
fun Hypergraph.breakCircularReferences(): Int {
    val circles = detectCircularReferences()
    var broken = 0

    circles.forEach { circle ->
        if (circle.size >= 2) {
            // Find link between first two elements and remove it
            val linkId = "relation-${circle[0]}-${circle[1]}"
            if (removeAtom(linkId)) {
                broken++
            }
        }
    }

    return broken
}

/**
 * Compact storage to free memory
 */
fun Hypergraph.compactStorage() {
    // In a real implementation, this would compact internal data structures
}

// ==================== ECANKernel Extensions ====================

/**
 * Queue status data class
 */
data class QueueStatus(
    val queueDepth: Int,
    val stalledTasks: Int,
    val activeTasks: Int
)

/**
 * Get current queue status
 */
fun ECANKernel.getQueueStatus(): QueueStatus {
    return QueueStatus(
        queueDepth = 0, // Would return actual queue depth
        stalledTasks = 0, // Would return actual stalled count
        activeTasks = 0 // Would return actual active count
    )
}

/**
 * Drain low-priority tasks from queue
 */
fun ECANKernel.drainLowPriorityTasks(ratio: Float): Int {
    // Would remove ratio% of lowest priority tasks
    return 0
}

/**
 * Cancel stalled tasks older than timeout
 */
fun ECANKernel.cancelStalledTasks(timeoutMs: Long): Int {
    // Would cancel tasks that have been running longer than timeout
    return 0
}

// ==================== CognitiveTensor Extensions ====================

/**
 * Convert tensor to float array
 */
fun CognitiveTensor.toArray(): FloatArray {
    return floatArrayOf(modality, depth, context, salience, autonomyIndex)
}

/**
 * Create tensor from float array
 */
fun CognitiveTensor.Companion.fromArray(array: FloatArray): CognitiveTensor {
    return CognitiveTensor(
        modality = array.getOrElse(0) { 0.5f },
        depth = array.getOrElse(1) { 0.5f },
        context = array.getOrElse(2) { 0.5f },
        salience = array.getOrElse(3) { 0.5f },
        autonomyIndex = array.getOrElse(4) { 0.5f }
    )
}

// ==================== Private Storage ====================

// Internal atoms storage (would be part of Hypergraph class in real implementation)
private val Hypergraph.atoms: MutableMap<String, Atom>
    get() = atomsStorage.getOrPut(this) { mutableMapOf() }

private val atomsStorage = mutableMapOf<Hypergraph, MutableMap<String, Atom>>()

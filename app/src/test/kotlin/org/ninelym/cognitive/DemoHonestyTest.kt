package org.ninelym.cognitive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ninelym.cognitive.neural.NeuralSymbolicSynthesisDemo
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * The demos print only what actually ran. Without the native ggml kernel the neural-symbolic demo
 * must say so and report no results; the Phase 7 demo's figures come from the work it performs.
 */
class DemoHonestyTest {

    private fun captureOutput(block: () -> Unit): String {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true))
        try { block() } finally { System.setOut(original) }
        return buffer.toString()
    }

    @Test
    fun `neural-symbolic demo reports the missing native kernel and no results`() {
        val output = captureOutput { NeuralSymbolicSynthesisDemo.runDemo() }
        assertTrue("demo should say the kernel is unavailable:\n$output", "not available" in output)
        for (result in listOf("Fused tensor signature", "Benchmark", "Verification", "Completed Successfully")) {
            assertFalse("demo reported '$result' without the native kernel:\n$output", result in output)
        }
    }

    @Test
    fun `phase 7 demo reports the outcome of the work it performs`() {
        val output = captureOutput { Phase7Demo.main(emptyArray()) }
        // The sync step really delivers data to the second node and prints the merge it got back
        assertTrue("sync merge should be reported from the real result:\n$output", "Merge Success: true" in output)
        assertTrue(output, "[OK] Self-healing demo completed" in output)
        assertTrue(output, "[OK] Distributed sync demo completed" in output)
        assertTrue(output, "[OK] Neural-symbolic bridge demo completed" in output)
    }
}

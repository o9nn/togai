package org.ninelym.togai.integration

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Every Termux command builder must pass caller-supplied text to its program as data. Each builder
 * is fed a value that tries three ways to run `touch MARKER` (closing a single quote, `$(...)` and
 * backticks); the command it produces is executed by a real `sh` in a scratch directory, with
 * network/package tools shadowed by no-op scripts, and the marker must never appear.
 */
class TermuxCommandInjectionTest {

    private val sandbox: File = Files.createTempDirectory("termux-inject").toFile()
    private val marker = File(sandbox, "PWNED")
    private val evil = "x'; touch ${marker.absolutePath}; echo '\$(touch ${marker.absolutePath})`touch ${marker.absolutePath}`"
    private val commands = mutableListOf<String>()

    @After
    fun cleanUp() {
        sandbox.deleteRecursively()
    }

    private val bin: File = File(sandbox, "bin").apply {
        mkdirs()
        // Tools that would touch the network, packages or other hosts do nothing here
        listOf("curl", "wget", "git", "pkg", "ping", "ssh", "python", "node", "tar", "zip", "unzip", "7z", "tail", "npm", "yarn")
            .forEach { name -> File(this, name).apply { writeText("#!/bin/sh\nexit 0\n"); setExecutable(true) } }
    }

    private fun execute(command: String): TermuxIntegrationService.CommandResult {
        commands += command
        val process = ProcessBuilder("sh", "-c", command).directory(sandbox).apply {
            environment()["PATH"] = "${bin.absolutePath}:/usr/bin:/bin"
            environment()["HOME"] = sandbox.absolutePath
        }.redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        return TermuxIntegrationService.CommandResult(process.waitFor(), out, "")
    }

    private fun termux(): TermuxIntegrationService {
        val t = mockk<TermuxIntegrationService>()
        coEvery { t.executeCommand(any(), any(), any(), any()) } coAnswers { execute(firstArg()) }
        every { t.observeCommandOutput(any()) } answers { execute(firstArg<String>()); flowOf<String>() }
        // The builders under test run as written; only command execution is redirected to the sandbox
        coEvery { t.runScript(any(), any(), any(), any()) } coAnswers { callOriginal() }
        coEvery { t.installPackage(any()) } coAnswers { callOriginal() }
        coEvery { t.isPackageInstalled(any()) } coAnswers { callOriginal() }
        coEvery { t.git(any(), any(), any()) } coAnswers { callOriginal() }
        coEvery { t.gitClone(any(), any(), any()) } coAnswers { callOriginal() }
        coEvery { t.ssh(any(), any(), any(), any(), any(), any()) } coAnswers { callOriginal() }
        coEvery { t.python(any(), any(), any()) } coAnswers { callOriginal() }
        coEvery { t.node(any(), any()) } coAnswers { callOriginal() }
        return t
    }

    @Test
    fun `shellQuote and shellPath deliver text verbatim`() {
        assertEquals(evil, execute("printf %s ${shellQuote(evil)}").stdout)
        assertEquals("${sandbox.absolutePath}/$evil", execute("printf %s ${shellPath("~/$evil")}").stdout)
        assertFalse("quoted text ran as shell", marker.exists())
    }

    @Test
    fun `no command builder runs caller-supplied text`(): Unit = runBlocking {
        val t = termux()
        t.httpRequest("POST", "http://127.0.0.1:9/$evil", mapOf("X-Note" to evil), body = """{"q":"$evil"}""")
        t.downloadFile("http://127.0.0.1:9/$evil", "out-$evil")
        t.downloadFile("http://127.0.0.1:9/$evil", "out-$evil", useCurl = false)
        t.findFiles(evil, evil)
        t.setupPythonProject("proj-$evil", listOf(evil), pythonVersion = "python")
        t.setupNodeProject("node-$evil", listOf(evil))
        t.findTodosInRepo("repo-$evil")
        t.pingHost(evil)
        t.tailLog("log-$evil").toList()
        ArchiveFormat.values().forEach { t.createArchive("src-$evil", "arc-$evil") ; t.createArchive("src-$evil", "arc-$evil", it) }
        listOf(".tar.gz", ".zip", ".tar", ".7z", ".bin").forEach { t.extractArchive("arc-$evil$it", "dest-$evil") }
        t.scrapeUrl("http://127.0.0.1:9/$evil")
        t.runScript("script-$evil", listOf(evil, evil), interpreter = "python")
        t.installPackage(evil)
        t.isPackageInstalled(evil)
        t.git("status", "repo-$evil")
        t.gitClone("http://127.0.0.1:9/$evil", "target-$evil", branch = evil)
        t.ssh(evil, "echo $evil", user = evil, identityFile = "key-$evil")
        t.python("print(1)", args = listOf(evil))
        t.python("print('$evil')")
        t.node("console.log('$evil')")

        val cogai = CogAIBridge(mockk<Context>(relaxed = true), t, "http://127.0.0.1:9")
        cogai.configureProvider(evil, apiKey = evil, baseUrl = evil)
        cogai.setDefaultProvider(evil)

        assertFalse("caller-supplied text ran as shell in one of:\n${commands.joinToString("\n")}", marker.exists())
    }
}

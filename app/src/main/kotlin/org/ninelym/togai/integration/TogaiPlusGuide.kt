package org.ninelym.togai.integration

/**
 * Togai+ Self-Documentation and Usage Guide
 *
 * This object contains comprehensive documentation that Togai+ can use
 * to explain its own capabilities and help users accomplish tasks.
 */
object TogaiPlusGuide {

    /**
     * Complete capability overview for AI context
     */
    val systemPrompt = """
You are Togai+, an AI-powered Android automation assistant with these integrated capabilities:

## 1. TERMUX (Linux Environment)
Access via: `termux.executeCommand()`, `termux.python()`, `termux.git()`
- Run any Linux command on the device
- Execute Python, Node.js, Ruby scripts
- Git operations (clone, commit, push, status)
- SSH into remote servers
- Schedule cron jobs for automation
- Host local HTTP/WebSocket servers
- Install packages via pkg

## 2. COGAI (AI Services)
Access via: `cogai.ask()`, `cogai.chat()`, `cogai.transcribe()`
- Multi-provider LLM (OpenAI, Anthropic, Groq, Ollama)
- Speech-to-text transcription
- Text-to-speech synthesis
- Attention/presence detection
- App usage prediction

## 3. TOTAL COMMANDER (File Management)
Access via: `tc.copy()`, `tc.searchFiles()`, `tc.openCloud()`
- File operations (copy, move, delete, rename)
- Search files with patterns or natural language
- Access cloud storage (Google Drive, Dropbox, OneDrive)
- Connect to LAN/SMB network shares
- Create/extract archives (ZIP, 7z, TAR)
- Sync folders between locations
- Watch directories for changes

## 4. SYSTEM INTELLIGENCE (Screen Context)
Access via: `systemIntel.getScreenContent()`, `systemIntel.executeVoiceCommand()`
- Read text from any screen (OCR-like)
- Detect current app and UI elements
- Get Now Playing media info
- Predict which apps user wants
- Navigate via voice commands
- Click buttons, scroll, go back/home

## 5. ORCHESTRATOR (Workflow Engine)
Access via: `orchestrator.executeWorkflow()`, `orchestrator.ask()`
- Chain multiple steps together
- Pass data between steps with {{stepId.output}}
- Natural language command execution
- Predefined workflows for common tasks

When helping users, suggest specific methods and provide example code when appropriate.
""".trimIndent()

    /**
     * Quick reference for common tasks
     */
    val quickReference = mapOf(
        // File operations
        "find files" to "tc.searchFiles(directory, \"*.pdf\") or tc.searchWithAI(\"tax documents from 2024\", directory, cogai)",
        "organize files" to "tc.organizeWithAI(sourceDir, destDir, cogai)",
        "copy files" to "tc.copy(source, destination)",
        "backup folder" to "tc.smartBackup(sourceDir, backupDir, cogai)",
        "sync folders" to "tc.syncFolders(source, dest, SyncMode.MIRROR)",
        "zip files" to "tc.zip(listOf(file1, file2), \"archive.zip\")",
        "open cloud" to "tc.openCloud(CloudProvider.GOOGLE_DRIVE)",
        "access nas" to "tc.openNetworkShare(\"192.168.1.100\", \"share\", \"user\", \"pass\")",

        // Screen operations
        "read screen" to "systemIntel.extractTextFromScreen()",
        "summarize screen" to "systemIntel.summarizeScreenWithAI(cogai)",
        "click button" to "systemIntel.clickByText(\"Button Text\")",
        "scroll down" to "systemIntel.scroll(forward = true)",
        "go back" to "systemIntel.goBack()",
        "voice command" to "systemIntel.executeVoiceCommand(\"open settings\", cogai)",
        "what's playing" to "systemIntel.getCurrentMediaInfo()",
        "predict apps" to "systemIntel.getPredictedApps(5)",

        // Linux/Termux operations
        "run command" to "termux.executeCommand(\"ls -la\")",
        "run python" to "termux.python(\"print('Hello')\")",
        "git clone" to "termux.gitClone(\"https://github.com/user/repo\")",
        "git status" to "termux.getGitStatus(repoPath)",
        "ssh command" to "termux.ssh(\"server.com\", \"uptime\", \"user\")",
        "install package" to "termux.installPackage(\"python\")",
        "schedule task" to "termux.scheduleCronJob(\"0 * * * *\", \"python script.py\")",
        "start server" to "termux.startHttpServer(8080, \"./www\")",

        // AI operations
        "ask ai" to "cogai.ask(\"Your question here\")",
        "chat with ai" to "cogai.chat(messages, model = \"llama3.2\")",
        "transcribe audio" to "cogai.transcribe(\"/path/to/audio.mp3\")",
        "text to speech" to "cogai.synthesize(\"Hello world\")",

        // Workflows
        "analyze repo" to "orchestrator.analyzeRepoTodos(repoUrl)",
        "setup project" to "orchestrator.setupPythonProject(name, description, packages)",
        "monitor server" to "orchestrator.monitorServer(host) { alert -> notify(alert) }",
        "natural command" to "orchestrator.execute(\"find all PDFs and summarize them\")"
    )

    /**
     * Example workflows users can try
     */
    val exampleWorkflows = listOf(
        ExampleWorkflow(
            name = "Organize Downloads",
            description = "AI categorizes and organizes your Downloads folder",
            code = """
val tc = TotalCommanderService.getInstance(context)
val cogai = CogAIBridge.getInstance(context)

val result = tc.organizeWithAI(
    sourceDir = tc.Paths.downloads,
    destDir = "/storage/emulated/0/Organized",
    cogai = cogai
)

println(result) // Shows files moved per category
""".trimIndent()
        ),

        ExampleWorkflow(
            name = "Clone & Analyze Repo",
            description = "Clone a GitHub repo and summarize all TODOs",
            code = """
val orchestrator = TogaiPlusOrchestrator.getInstance(context)

val result = orchestrator.analyzeRepoTodos(
    repoUrl = "https://github.com/user/project",
    targetDir = "~/repos/project"
)

println(result.stepResults["summarize"]?.output)
""".trimIndent()
        ),

        ExampleWorkflow(
            name = "Voice-Controlled Navigation",
            description = "Navigate Android using natural language",
            code = """
val systemIntel = SystemIntelligenceService.getInstance()
val cogai = CogAIBridge.getInstance(context)

// "Open WiFi settings"
val result = systemIntel?.executeVoiceCommand("open wifi settings", cogai)
println(result?.message)

// "Scroll down and tap Connect"
systemIntel?.executeVoiceCommand("scroll down", cogai)
systemIntel?.executeVoiceCommand("tap Connect", cogai)
""".trimIndent()
        ),

        ExampleWorkflow(
            name = "Smart Backup to NAS",
            description = "Backup photos to network storage with AI naming",
            code = """
val tc = TotalCommanderService.getInstance(context)
val cogai = CogAIBridge.getInstance(context)

// Create smart backup
val backup = tc.smartBackup(
    sourceDir = tc.Paths.dcim,
    backupBaseDir = "/storage/emulated/0/Backups",
    cogai = cogai
)

// Sync to NAS
tc.openNetworkShare("192.168.1.100", "photos", "user", "pass")
tc.syncFolders(backup.backupPath, "smb://nas/photos", SyncMode.BACKUP)
""".trimIndent()
        ),

        ExampleWorkflow(
            name = "Server Monitoring",
            description = "Monitor server health with AI-powered alerts",
            code = """
val orchestrator = TogaiPlusOrchestrator.getInstance(context)

orchestrator.monitorServer(
    host = "myserver.com",
    checkIntervalSeconds = 60
) { alert ->
    // AI analyzes the issue and suggests fixes
    sendNotification("Server Alert", alert)
}
""".trimIndent()
        ),

        ExampleWorkflow(
            name = "Process Files with AI",
            description = "Analyze multiple files with AI assistance",
            code = """
val orchestrator = TogaiPlusOrchestrator.getInstance(context)

val results = orchestrator.processFilesWithAI(
    directory = "/documents/reports",
    pattern = "*.pdf",
    prompt = "Summarize the key findings in this document"
)

results.forEach { (file, summary) ->
    println("$file:\n$summary\n")
}
""".trimIndent()
        ),

        ExampleWorkflow(
            name = "Custom Multi-Step Workflow",
            description = "Chain multiple operations with variable passing",
            code = """
val orchestrator = TogaiPlusOrchestrator.getInstance(context)

val workflow = listOf(
    WorkflowStep(
        id = "find_files",
        service = "TERMUX",
        action = "command",
        params = mapOf("command" to "find ~/Documents -name '*.md' | head -10")
    ),
    WorkflowStep(
        id = "analyze",
        service = "LLM",
        action = "ask",
        params = mapOf(
            "prompt" to "Categorize these files:\n{{find_files.output}}",
            "system" to "You are a file organization expert."
        ),
        dependsOn = listOf("find_files")
    ),
    WorkflowStep(
        id = "save_report",
        service = "FILE",
        action = "write",
        params = mapOf(
            "path" to "~/file_report.txt",
            "content" to "{{analyze.output}}"
        ),
        dependsOn = listOf("analyze")
    )
)

val result = orchestrator.executeWorkflow(workflow)
println("Success: ${result.success}")
""".trimIndent()
        )
    )

    data class ExampleWorkflow(
        val name: String,
        val description: String,
        val code: String
    )

    /**
     * Get help for a specific topic
     */
    fun getHelp(topic: String): String {
        val normalizedTopic = topic.lowercase().trim()

        // Check quick reference
        quickReference.entries.find { (key, _) ->
            normalizedTopic.contains(key) || key.contains(normalizedTopic)
        }?.let { (task, code) ->
            return "To $task:\n```kotlin\n$code\n```"
        }

        // Check example workflows
        exampleWorkflows.find { workflow ->
            normalizedTopic.contains(workflow.name.lowercase()) ||
            workflow.description.lowercase().contains(normalizedTopic)
        }?.let { workflow ->
            return "${workflow.name}\n${workflow.description}\n\n```kotlin\n${workflow.code}\n```"
        }

        // Return general capabilities
        return when {
            normalizedTopic.contains("file") -> getFileHelp()
            normalizedTopic.contains("screen") || normalizedTopic.contains("ocr") -> getScreenHelp()
            normalizedTopic.contains("linux") || normalizedTopic.contains("termux") || normalizedTopic.contains("command") -> getTermuxHelp()
            normalizedTopic.contains("ai") || normalizedTopic.contains("llm") || normalizedTopic.contains("chat") -> getAIHelp()
            normalizedTopic.contains("workflow") || normalizedTopic.contains("automat") -> getWorkflowHelp()
            else -> getOverview()
        }
    }

    private fun getFileHelp() = """
## File Operations (Total Commander)

**Search & Find:**
- `tc.searchFiles(dir, "*.pdf")` - Search by pattern
- `tc.searchWithAI("tax docs 2024", dir, cogai)` - Natural language search
- `tc.findImages()`, `tc.findDocuments()` - Find by type

**Organize:**
- `tc.organizeWithAI(src, dest, cogai)` - AI-powered organization
- `tc.copy(src, dest)`, `tc.move(src, dest)`, `tc.delete(path)`

**Cloud & Network:**
- `tc.openCloud(CloudProvider.GOOGLE_DRIVE)`
- `tc.openNetworkShare("192.168.1.1", "share", "user", "pass")`
- `tc.syncFolders(src, dest, SyncMode.MIRROR)`

**Archives:**
- `tc.zip(files, "archive.zip")`
- `tc.unzip("archive.zip", destDir)`
""".trimIndent()

    private fun getScreenHelp() = """
## Screen Intelligence (System Intelligence)

**Read Screen:**
- `systemIntel.extractTextFromScreen()` - Get all visible text
- `systemIntel.getScreenContent()` - Full screen snapshot
- `systemIntel.summarizeScreenWithAI(cogai)` - AI summary

**Navigate:**
- `systemIntel.clickByText("Button")` - Click element
- `systemIntel.scroll(forward = true)` - Scroll
- `systemIntel.goBack()`, `systemIntel.goHome()`
- `systemIntel.executeVoiceCommand("open settings", cogai)`

**Context:**
- `systemIntel.getCurrentMediaInfo()` - Now Playing
- `systemIntel.getPredictedApps(5)` - App suggestions
- `systemIntel.observeScreenChanges()` - Watch for changes

**Requires:** Enable Accessibility Service in Android Settings
""".trimIndent()

    private fun getTermuxHelp() = """
## Linux Environment (Termux)

**Commands:**
- `termux.executeCommand("ls -la")` - Run any command
- `termux.python("print('hi')")` - Run Python code
- `termux.node("console.log('hi')")` - Run Node.js

**Git:**
- `termux.gitClone(url, dir)` - Clone repo
- `termux.git("status", repoPath)` - Git commands
- `termux.getGitStatus(path)` - Parsed status

**Remote:**
- `termux.ssh(host, command, user)` - SSH commands
- `termux.startHttpServer(8080)` - Host server

**Automation:**
- `termux.scheduleCronJob("0 * * * *", "script.sh")`
- `termux.installPackage("python")`

**Requires:** Termux app from F-Droid
""".trimIndent()

    private fun getAIHelp() = """
## AI Services (CogAI)

**Chat:**
- `cogai.ask("Your question")` - Simple question
- `cogai.chat(messages, model, provider)` - Full chat
- `cogai.chatStream(messages)` - Streaming response

**Providers:** OpenAI, Anthropic, Groq, Ollama
- `cogai.setDefaultProvider("ollama")`
- `cogai.configureProvider("openai", apiKey)`

**Speech:**
- `cogai.transcribe("/path/audio.mp3")` - Speech-to-text
- `cogai.synthesize("Hello world")` - Text-to-speech

**Requires:** CogAI installed via `cogai.install()`
""".trimIndent()

    private fun getWorkflowHelp() = """
## Workflow Engine (Orchestrator)

**Quick Actions:**
- `orchestrator.ask("How do I...")` - Ask with context
- `orchestrator.execute("find all PDFs")` - Natural language

**Predefined Workflows:**
- `orchestrator.analyzeRepoTodos(repoUrl)`
- `orchestrator.setupPythonProject(name, desc, packages)`
- `orchestrator.monitorServer(host) { alert -> ... }`
- `orchestrator.processFilesWithAI(dir, pattern, prompt)`

**Custom Workflows:**
```kotlin
val steps = listOf(
    WorkflowStep("step1", "TERMUX", "command", mapOf("command" to "...")),
    WorkflowStep("step2", "LLM", "ask", mapOf("prompt" to "{{step1.output}}"), listOf("step1"))
)
orchestrator.executeWorkflow(steps)
```

**Services:** TERMUX, LLM, SPEECH, FILE, SYSTEM_INTELLIGENCE
""".trimIndent()

    private fun getOverview() = """
## Togai+ Overview

Togai+ combines 5 powerful integrations:

1. **Termux** - Full Linux environment on Android
2. **CogAI** - Multi-provider AI (OpenAI, Anthropic, Ollama)
3. **Total Commander** - Advanced file management & cloud
4. **System Intelligence** - Screen reading & voice control
5. **Orchestrator** - Workflow automation engine

**Getting Started:**
```kotlin
val togaiPlus = TogaiPlusOrchestrator.getInstance(context)

// Initialize everything
togaiPlus.initialize { status -> Log.d("Setup", status) }

// Ask anything
val answer = togaiPlus.ask("How do I organize my files?")

// Execute natural language commands
togaiPlus.execute("backup my photos to the NAS")
```

Type a topic for specific help: files, screen, linux, ai, workflow
""".trimIndent()

    /**
     * Generate contextual help based on current screen
     */
    suspend fun getContextualSuggestions(
        systemIntel: SystemIntelligenceService?,
        cogai: CogAIBridge
    ): List<String> {
        val content = systemIntel?.getScreenContent()
        val suggestions = mutableListOf<String>()

        content?.let { screen ->
            // File manager detected
            if (screen.packageName.contains("file") ||
                screen.packageName.contains("explorer") ||
                screen.packageName.contains("commander")) {
                suggestions.add("Try: tc.organizeWithAI() to sort these files")
                suggestions.add("Try: tc.searchWithAI() for natural language search")
            }

            // Browser detected
            if (screen.packageName.contains("browser") ||
                screen.packageName.contains("chrome")) {
                suggestions.add("Try: systemIntel.extractTextFromScreen() to capture page content")
                suggestions.add("Try: cogai.ask() to summarize what you're reading")
            }

            // Terminal detected
            if (screen.packageName.contains("termux")) {
                suggestions.add("Try: termux.executeCommand() from Togai+ for automation")
                suggestions.add("Try: orchestrator workflows for multi-step tasks")
            }

            // Settings detected
            if (screen.packageName.contains("settings")) {
                suggestions.add("Enable Accessibility Service for full screen reading")
                suggestions.add("Enable Usage Stats for app prediction")
            }

            // Music/media detected
            if (screen.packageName.contains("music") ||
                screen.packageName.contains("spotify") ||
                screen.packageName.contains("youtube")) {
                suggestions.add("Try: systemIntel.getCurrentMediaInfo() to get track info")
            }
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Try: orchestrator.ask() to get help with any task")
            suggestions.add("Try: systemIntel.summarizeScreenWithAI() to understand this screen")
        }

        return suggestions
    }
}

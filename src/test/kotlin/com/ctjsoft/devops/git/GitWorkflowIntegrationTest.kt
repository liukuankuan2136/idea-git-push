package com.ctjsoft.devops.git

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitWorkflowIntegrationTest {
    @Test
    fun `staged commit amend and local recovery work in a temporary repository`() {
        val root = Files.createTempDirectory("issue-link-push-git-test-")
        val runner = ProcessRunner(root)
        runner.require("init")
        runner.require("config", "user.name", "Plugin Test")
        runner.require("config", "user.email", "plugin-test@example.invalid")
        val workflow = GitWorkflow(runner)

        assertFalse(workflow.hasStagedChanges())
        root.resolve("sample.txt").writeText("one")
        runner.require("add", "sample.txt")
        assertTrue(workflow.hasStagedChanges())

        workflow.commit("feat:test scrum -e TASK-1 -h:1 -s:10")
        assertTrue(workflow.branchState().hasUnpushedCommits)
        assertEquals("feat:test scrum -e TASK-1 -h:1 -s:10", runner.require("log", "-1", "--pretty=%B").trim())

        workflow.amend("fix:test scrum -e TASK-1 -h:1 -s:20")
        assertEquals("fix:test scrum -e TASK-1 -h:1 -s:20", runner.require("log", "-1", "--pretty=%B").trim())
        workflow.recoverAmend()
        assertEquals("feat:test scrum -e TASK-1 -h:1 -s:10", runner.require("log", "-1", "--pretty=%B").trim())
    }

    private class ProcessRunner(private val cwd: Path) : GitCommandRunner {
        override fun run(arguments: List<String>): GitCommandResult {
            val process = ProcessBuilder(listOf("git") + arguments)
                .directory(cwd.toFile())
                .redirectErrorStream(false)
                .start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            return GitCommandResult(process.waitFor(), stdout, stderr)
        }

        fun require(vararg arguments: String): String {
            val result = run(arguments.toList())
            check(result.successful) { result.stderr }
            return result.stdout
        }
    }
}


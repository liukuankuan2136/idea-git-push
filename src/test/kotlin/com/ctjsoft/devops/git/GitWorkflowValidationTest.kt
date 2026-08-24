package com.ctjsoft.devops.git

import com.ctjsoft.devops.core.DevOpsException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GitWorkflowValidationTest {
    @Test
    fun `rejects messages without lowercase scrum directive`() {
        assertFailsWith<DevOpsException> {
            GitWorkflow.validateCommitMessage("feat:test SCRUM -e TASK-1 -h:1 -s:10")
        }
    }

    @Test
    fun `accepts merge message format`() {
        GitWorkflow.validateCommitMessage("Merge branch scrum -e TASK-1 -h:1 -s:10")
    }
}

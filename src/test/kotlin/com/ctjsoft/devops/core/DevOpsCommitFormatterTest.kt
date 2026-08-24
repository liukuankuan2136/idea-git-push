package com.ctjsoft.devops.core

import com.ctjsoft.devops.model.DevOpsCommitMetadata
import com.ctjsoft.devops.model.DevOpsProject
import com.ctjsoft.devops.model.DevOpsTask
import com.ctjsoft.devops.model.DevOpsTaskType
import kotlin.test.Test
import kotlin.test.assertEquals

class DevOpsCommitFormatterTest {
    private val task = DevOpsTask("TASK-7", "Title", DevOpsTaskType.TASK, "incomplete", "P1")
    private val project = DevOpsProject("P1", "Product")

    @Test
    fun `formats normal work-hour metadata`() {
        val metadata = DevOpsCommitMetadata(project, task, "feat", "subject", "1.5", "30", "code", "代码编写")
        assertEquals(
            "feat:subject scrum -e TASK-7 -h:1.5 -s:30",
            DevOpsCommitFormatter.format("\${COMMIT_TYPE}:\${SUBJECT} scrum -e \${CODE} -h:\${HOURS} -s:\${PROGRESS}", metadata),
        )
    }

    @Test
    fun `uses AI hours flag when selected type name contains AI`() {
        val metadata = DevOpsCommitMetadata(project, task, "fix", "subject", "2", "40", "ai", "AI编程")
        assertEquals(
            "fix:subject scrum -e TASK-7 -aih:2 -s:40",
            DevOpsCommitFormatter.format("\${COMMIT_TYPE}:\${SUBJECT} scrum -e \${CODE} -h:\${HOURS} -s:\${PROGRESS}", metadata),
        )
    }
}

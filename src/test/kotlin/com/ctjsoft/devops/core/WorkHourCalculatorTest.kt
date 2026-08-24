package com.ctjsoft.devops.core

import com.ctjsoft.devops.model.DevOpsCommitMetadata
import com.ctjsoft.devops.model.DevOpsProject
import com.ctjsoft.devops.model.DevOpsTask
import com.ctjsoft.devops.model.DevOpsTaskType
import com.ctjsoft.devops.model.WorkHourRecord
import com.ctjsoft.devops.settings.RecordMode
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkHourCalculatorTest {
    private val metadata = DevOpsCommitMetadata(
        DevOpsProject("P", "Product"),
        DevOpsTask("TASK-1", "Task", DevOpsTaskType.TASK, "incomplete", "P"),
        "feat", "new work", "1.5", "30", "24", "代码编写",
        WorkHourRecord("WH-1", 2.0, "• old work", "2026-08-23", "80%"),
    )

    @Test fun `append modes accumulate with limits`() {
        assertEquals(3.5, WorkHourCalculator.hours(metadata, RecordMode.APPEND))
        assertEquals("100%", WorkHourCalculator.completion(metadata, RecordMode.APPEND))
        assertEquals("• old work\n• new work", WorkHourCalculator.content(metadata, RecordMode.APPEND))
    }

    @Test fun `overwrite modes replace values`() {
        assertEquals(1.5, WorkHourCalculator.hours(metadata, RecordMode.OVERWRITE))
        assertEquals("30%", WorkHourCalculator.completion(metadata, RecordMode.OVERWRITE))
        assertEquals("• new work", WorkHourCalculator.content(metadata, RecordMode.OVERWRITE))
    }
}

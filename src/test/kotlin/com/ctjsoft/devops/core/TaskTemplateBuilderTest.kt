package com.ctjsoft.devops.core

import com.ctjsoft.devops.settings.TaskCreateMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskTemplateBuilderTest {
    @Test
    fun `simple mode preserves defaults for unshown sections`() {
        val result = TaskTemplateBuilder.build(TaskCreateMode.SIMPLE, mapOf("taskDesc" to "这是用户实际填写的二十个字以上任务详细描述内容。"))
        assertTrue(result.html.contains("前置任务"))
        assertTrue(result.html.contains("开发目录"))
        assertTrue(result.html.contains("根据实际情况修改。"))
        assertFalse(result.html.contains("六、验收标准"))
    }

    @Test
    fun `normal mode includes entered acceptance criteria and escapes HTML`() {
        val result = TaskTemplateBuilder.build(TaskCreateMode.NORMAL, mapOf("taskDesc" to "描述 & 目标", "acceptCriteria" to "不得出现 <script>"))
        assertTrue(result.html.contains("六、验收标准"))
        assertTrue(result.html.contains("&lt;script&gt;"))
        assertTrue(result.html.contains("描述 &amp; 目标"))
    }
}


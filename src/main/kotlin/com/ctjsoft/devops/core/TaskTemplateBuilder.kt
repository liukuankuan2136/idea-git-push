package com.ctjsoft.devops.core

import com.ctjsoft.devops.settings.TaskCreateMode

data class TaskTemplateResult(val html: String, val taskDescription: String)

object TaskTemplateBuilder {
    data class Section(val id: String, val label: String, val tag: String, val defaultContent: String)

    val sections = listOf(
        Section("prevTask", "前置任务", "h2", "上一任务，非必须，便于了解整个需求的全部任务，条理性清晰。"),
        Section("taskDesc", "任务描述", "h2", "任务明细、目标。"),
        Section("devDir", "开发目录", "h2", "SVN 路径、package、class、method，非必须。"),
        Section("svnPath", "一、SVN 路径", "h2", "指定到对应的 SVN 服务包路径。"),
        Section("dbChanges", "二、数据库修改", "h2", "根据实际情况修改。"),
        Section("uiOverview", "1. 整体界面情况", "h3", "描述按钮、列表、查询区情况，按钮需要指定在哪个状态显示。"),
        Section("featureDev", "2. 功能开发", "h3", "涉及功能页面。"),
        Section("backendChanges", "四、服务端修改", "h2", "设计后端服务。"),
        Section("configChanges", "五、配置修改", "h2", "描述修改的配置，如字段映射等。"),
        Section("acceptCriteria", "六、验收标准", "h2", ""),
        Section("impactScope", "七、影响范围（需要测试的功能）", "h2", ""),
    )

    fun visibleSectionIds(mode: TaskCreateMode): Set<String> = when (mode) {
        TaskCreateMode.SIMPLE -> setOf("taskDesc")
        TaskCreateMode.NORMAL -> setOf("taskDesc", "acceptCriteria")
        TaskCreateMode.BENCHMARK -> sections.map(Section::id).toSet()
    }

    /** C05: every unshown section with a default is still included, matching the VS Code behavior. */
    fun build(mode: TaskCreateMode, input: Map<String, String>): TaskTemplateResult {
        val collected = linkedMapOf<String, String>()
        sections.forEach { section ->
            val entered = input[section.id]?.trim().orEmpty()
            if (entered.isNotBlank()) collected[section.id] = entered
            else if (section.defaultContent.isNotBlank()) collected[section.id] = section.defaultContent
        }
        val html = buildString {
            var frontendOpened = false
            sections.forEach { section ->
                val content = collected[section.id].orEmpty()
                if (content.isBlank()) return@forEach
                if ((section.id == "uiOverview" || section.id == "featureDev") && !frontendOpened) {
                    append("<h2>三、前端修改</h2>")
                    frontendOpened = true
                }
                append("<${section.tag}>${escape(section.label)}</${section.tag}><p>${escape(content)}</p>")
            }
        }
        return TaskTemplateResult(html, collected["taskDesc"].orEmpty())
    }

    private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}


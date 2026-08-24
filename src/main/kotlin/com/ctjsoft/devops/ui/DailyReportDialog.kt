package com.ctjsoft.devops.ui

import com.ctjsoft.devops.core.DailyReportFormatter
import com.ctjsoft.devops.model.DailyReportInput
import com.ctjsoft.devops.model.TodayWorkSummary
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane

class DailyReportDialog(
    project: Project,
    private val reportDate: String,
    private val summary: TodayWorkSummary,
    tomorrowPlan: String,
    private val overdue: Pair<Int, String>,
) : DialogWrapper(project) {
    private val nextPlanArea = JBTextArea(tomorrowPlan, 10, 72).apply { lineWrap = true; wrapStyleWord = true }
    private val otherArea = JBTextArea(6, 72).apply { lineWrap = true; wrapStyleWord = true }

    init {
        title = "提交日报 — $reportDate"
        setOKButtonText("确认提交日报")
        init()
    }

    fun result(): DailyReportInput = DailyReportInput(
        nowWorkHtml = DailyReportFormatter.nowWorkHtml(summary),
        nextPlanHtml = nextPlanArea.text.trim().ifBlank { "<p><br></p>" },
        otherMattersHtml = DailyReportFormatter.paragraphOrBlank(otherArea.text),
        reportDate = reportDate,
        recipientUserIds = emptyList(),
    )

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
        border = JBUI.Borders.empty(8)
        val warning = buildString {
            append("今日工时：${summary.totalHours}h")
            if (summary.totalHours < 8) append("（不足 8h，仍可提交）")
            if (overdue.first > 0) append("；逾期提醒：${overdue.second.ifBlank { "${overdue.first} 项" }}")
        }
        add(JBLabel(warning), BorderLayout.NORTH)
        val tabs = JTabbedPane().apply {
            addTab("今日工作（只读）", JScrollPane(JBTextArea(DailyReportFormatter.summaryText(summary)).apply {
                isEditable = false; lineWrap = true; wrapStyleWord = true
            }))
            addTab("明日计划", JScrollPane(nextPlanArea))
            addTab("其他事项", JScrollPane(otherArea))
            preferredSize = Dimension(760, 430)
        }
        add(tabs, BorderLayout.CENTER)
    }

    override fun doValidate(): ValidationInfo? {
        if (nextPlanArea.text.length > 5000) return ValidationInfo("明日计划不能超过 5000 字。", nextPlanArea)
        if (otherArea.text.length > 2000) return ValidationInfo("其他事项不能超过 2000 字。", otherArea)
        return null
    }
}

package com.ctjsoft.devops.action

import com.ctjsoft.devops.api.DevOpsApi
import com.ctjsoft.devops.model.TodayWorkSummary
import com.ctjsoft.devops.ui.DailyReportDialog
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import java.time.LocalDate
import java.time.ZoneId

class DailyReportAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val reportDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "加载今日日报数据", false) {
            private lateinit var api: DevOpsApi
            private lateinit var summary: TodayWorkSummary
            private var tomorrow = ""
            private var overdue = 0 to ""

            override fun run(indicator: ProgressIndicator) {
                api = ActionSupport.api()
                summary = api.fetchTodayWork(reportDate)
                tomorrow = api.fetchTomorrowPlan()
                api.checkTodayWorkHourEnough(reportDate)
                overdue = api.checkOverdueTasks()
            }

            override fun onSuccess() {
                val dialog = DailyReportDialog(project, reportDate, summary, tomorrow, overdue)
                if (!dialog.showAndGet()) return
                val input = dialog.result()
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, "提交日报", false) {
                    override fun run(indicator: ProgressIndicator) = api.submitDailyReport(input)
                    override fun onSuccess() = ActionSupport.notify(project, "日报提交完成", "$reportDate 日报已提交。")
                    override fun onThrowable(error: Throwable) = ActionSupport.notify(project, "日报提交失败", error.message ?: "提交失败。", NotificationType.ERROR)
                })
            }

            override fun onThrowable(error: Throwable) =
                ActionSupport.notify(project, "日报加载失败", error.message ?: "加载失败。", NotificationType.ERROR)
        })
    }
}


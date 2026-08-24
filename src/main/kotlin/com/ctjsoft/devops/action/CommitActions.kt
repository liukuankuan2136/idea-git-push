package com.ctjsoft.devops.action

import com.ctjsoft.devops.api.DevOpsApi
import com.ctjsoft.devops.core.DevOpsCommitFormatter
import com.ctjsoft.devops.core.WorkHourCalculator
import com.ctjsoft.devops.git.Git4IdeaCommandRunner
import com.ctjsoft.devops.git.GitWorkflow
import com.ctjsoft.devops.git.PushTarget
import com.ctjsoft.devops.model.DevOpsCommitMetadata
import com.ctjsoft.devops.model.DevOpsProject
import com.ctjsoft.devops.model.DevOpsTask
import com.ctjsoft.devops.model.DevOpsTaskType
import com.ctjsoft.devops.settings.IssueLinkPushSettings
import com.ctjsoft.devops.ui.CommitDialogResult
import com.ctjsoft.devops.ui.CommitOperation
import com.ctjsoft.devops.ui.CommitTaskDialog
import com.ctjsoft.devops.ui.RepositoryOption
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager
import java.time.LocalDate
import java.time.ZoneId

abstract class BaseCommitAction(private val operation: CommitOperation) : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val repositories = GitRepositoryManager.getInstance(project).repositories
        if (repositories.isEmpty()) {
            ActionSupport.notify(project, "Issue Link Push", "当前项目没有 Git 仓库。", NotificationType.WARNING)
            return
        }
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "加载 DevOps 工作项", false) {
            private lateinit var api: DevOpsApi
            private lateinit var taskMap: Map<DevOpsTaskType, List<DevOpsTask>>
            private lateinit var repoOptions: List<RepositoryOption>
            private var workTypes = emptyList<com.ctjsoft.devops.model.WorkHourType>()

            override fun run(indicator: ProgressIndicator) {
                api = ActionSupport.api()
                api.testConnection()
                taskMap = DevOpsTaskType.entries.associateWith { type ->
                    ActionSupport.cached("ordinary-work-items:${type.name}") { api.fetchTasks(type) }
                }
                workTypes = ActionSupport.cached("work-hour-types") { api.fetchWorkHourTypes() }
                repoOptions = repositories.map { repository ->
                    val workflow = GitWorkflow(Git4IdeaCommandRunner(project, repository.root))
                    val state = workflow.branchState()
                    RepositoryOption(
                        repository,
                        repository.root.presentableUrl,
                        state.hasUpstream,
                        workflow.remotes(),
                        workflow.currentBranch() ?: "main",
                        workflow.originUrl(),
                    )
                }
            }

            override fun onSuccess() {
                val settings = ApplicationManager.getApplication().getService(IssueLinkPushSettings::class.java).state
                val dialog = CommitTaskDialog(project, operation, repoOptions, taskMap, workTypes, settings.commitTemplate)
                if (!dialog.showAndGet()) return
                execute(project, api, dialog.result())
            }

            override fun onThrowable(error: Throwable) {
                ActionSupport.notify(project, "加载失败", error.message ?: "无法加载 DevOps 数据。", NotificationType.ERROR)
            }
        })
    }

    private fun execute(project: Project, api: DevOpsApi, input: CommitDialogResult) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "执行 Issue Link Push", false) {
            override fun run(indicator: ProgressIndicator) {
                val settings = ApplicationManager.getApplication().getService(IssueLinkPushSettings::class.java).state
                val workflow = GitWorkflow(Git4IdeaCommandRunner(project, input.repository.repository.root))
                if (operation != CommitOperation.AMEND_AND_PUSH && !workflow.hasStagedChanges()) {
                    error("当前没有已暂存的改动，请先 git add。")
                }
                val task = resolveTask(api, input)
                val today = LocalDate.now(SHANGHAI_ZONE).toString()
                val todayRecord = api.fetchWorkHours(task.id).firstOrNull { it.taskWorkhourDate == today }
                val metadata = DevOpsCommitMetadata(
                    project = DevOpsProject(task.projectCode, task.projectName ?: task.projectCode),
                    task = task,
                    commitType = input.commitType,
                    subject = input.subject,
                    hours = input.hours,
                    progress = input.progress,
                    workHourTypeCode = input.workHourType.code,
                    workHourTypeName = input.workHourType.name,
                    todayWorkHour = todayRecord,
                )
                val message = DevOpsCommitFormatter.format(settings.commitTemplate, metadata)
                when (operation) {
                    CommitOperation.AMEND_AND_PUSH -> workflow.amend(message)
                    CommitOperation.COMMIT_AND_PUSH, CommitOperation.COMMIT_ONLY -> workflow.commit(message)
                }
                try {
                    if (operation != CommitOperation.COMMIT_ONLY) {
                        workflow.push(PushTarget(input.repository.hasUpstream, input.remote, input.branch))
                    }
                } catch (error: Throwable) {
                    if (operation == CommitOperation.AMEND_AND_PUSH) workflow.recoverAmend() else workflow.recoverCommit()
                    throw error
                }
                recordHours(api, metadata, settings)
            }

            override fun onSuccess() {
                val content = if (operation == CommitOperation.COMMIT_ONLY) "代码已提交到本地，工时已登记。" else "代码已推送，工时已登记。"
                ActionSupport.notify(project, "Issue Link Push 完成", content)
            }

            override fun onThrowable(error: Throwable) {
                ActionSupport.notify(project, "Issue Link Push 失败", error.message ?: "执行失败。", NotificationType.ERROR)
            }
        })
    }

    private fun resolveTask(api: DevOpsApi, input: CommitDialogResult): DevOpsTask {
        if (input.manualCode.isBlank()) return requireNotNull(input.selectedTask)
        return api.fetchTaskByCode(input.manualCode, input.taskType)
            ?: error("未找到编号 ${input.manualCode} 的 ${input.taskType.name.lowercase()}。")
    }

    private fun recordHours(api: DevOpsApi, metadata: DevOpsCommitMetadata, settings: IssueLinkPushSettings.Data) {
        val today = LocalDate.now(SHANGHAI_ZONE).toString()
        val hours = WorkHourCalculator.hours(metadata, settings.workHourMode)
        val completion = WorkHourCalculator.completion(metadata, settings.progressMode)
        val content = WorkHourCalculator.content(metadata, settings.workContentMode)
        val taskId = metadata.task.id.ifBlank { metadata.task.code }
        val existing = metadata.todayWorkHour
        if (existing != null) {
            api.modifyWorkHour(existing.taskWorkhourId, taskId, today, hours, completion, content, metadata.workHourTypeCode)
        } else {
            api.addWorkHour(taskId, today, hours, completion, content, metadata.workHourTypeCode)
        }
    }

    companion object {
        private val SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai")
    }
}

class SubmitWithDevOpsTaskAction : BaseCommitAction(CommitOperation.AMEND_AND_PUSH)
class CommitAndPushAction : BaseCommitAction(CommitOperation.COMMIT_AND_PUSH)
class CommitOnlyAction : BaseCommitAction(CommitOperation.COMMIT_ONLY)

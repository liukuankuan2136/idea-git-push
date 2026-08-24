package com.ctjsoft.devops.action

import com.ctjsoft.devops.api.DevOpsApi
import com.ctjsoft.devops.core.DevOpsCommitFormatter
import com.ctjsoft.devops.core.WorkHourCatalogMapping
import com.ctjsoft.devops.git.Git4IdeaCommandRunner
import com.ctjsoft.devops.git.GitWorkflow
import com.ctjsoft.devops.git.PushTarget
import com.ctjsoft.devops.model.CreateTaskInput
import com.ctjsoft.devops.model.DevOpsCommitMetadata
import com.ctjsoft.devops.model.DevOpsProject
import com.ctjsoft.devops.model.DevOpsTask
import com.ctjsoft.devops.model.DevOpsTaskType
import com.ctjsoft.devops.settings.IssueLinkPushSettings
import com.ctjsoft.devops.settings.ProductMapping
import com.ctjsoft.devops.settings.ProductMappingStore
import com.ctjsoft.devops.ui.RepositoryOption
import com.ctjsoft.devops.ui.TaskCreationDialog
import com.ctjsoft.devops.ui.TaskCreationDialogResult
import com.ctjsoft.devops.ui.TaskCreationOperation
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import git4idea.repo.GitRepositoryManager
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

abstract class BaseTaskCreationAction(private val operation: TaskCreationOperation) : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "加载任务登记数据", false) {
            private lateinit var api: DevOpsApi
            private var repositories = emptyList<RepositoryOption>()
            private var devProjects = emptyList<com.ctjsoft.devops.model.DevProject>()
            private var regions = emptyList<com.ctjsoft.devops.model.Region>()
            private var workTypes = emptyList<com.ctjsoft.devops.model.WorkHourType>()
            private var products = emptyList<com.ctjsoft.devops.model.Product>()
            private var opsProjects = emptyList<com.ctjsoft.devops.model.OpsProject>()
            private var versions = emptyList<com.ctjsoft.devops.model.ProductVersion>()
            private var modules = emptyList<com.ctjsoft.devops.model.ProductModule>()
            private var initialMapping: ProductMapping? = null

            override fun run(indicator: ProgressIndicator) {
                api = ActionSupport.api()
                api.testConnection()
                if (operation == TaskCreationOperation.OPS_WITH_GIT) {
                    repositories = GitRepositoryManager.getInstance(project).repositories.map { repository ->
                        val workflow = GitWorkflow(Git4IdeaCommandRunner(project, repository.root))
                        val state = workflow.branchState()
                        RepositoryOption(
                            repository, repository.root.presentableUrl, state.hasUpstream, workflow.remotes(),
                            workflow.currentBranch() ?: "main", workflow.originUrl(),
                        )
                    }
                    if (repositories.isEmpty()) error("当前项目没有 Git 仓库。")
                }
                val mappingStore = ApplicationManager.getApplication().getService(ProductMappingStore::class.java)
                val mappingKey = if (operation == TaskCreationOperation.OPS_WITH_GIT) {
                    "repo:${repositories.first().originUrl ?: repositories.first().label}"
                } else {
                    "workspace:${project.basePath.orEmpty()}"
                }
                initialMapping = mappingStore.get(mappingKey)
                devProjects = ActionSupport.cached("dev-projects") { api.fetchDevProjects() }
                regions = ActionSupport.cached("regions") { api.fetchRegions() }
                workTypes = ActionSupport.cached("work-hour-types") { api.fetchWorkHourTypes() }
                val initialDevProject = devProjects.firstOrNull { it.id == initialMapping?.devProjectId } ?: devProjects.firstOrNull()
                if (initialDevProject != null) products = ActionSupport.cached("products:${initialDevProject.id}") {
                    api.fetchProductsByProject(initialDevProject.id)
                }
                if (regions.isNotEmpty()) opsProjects = ActionSupport.cached("ops-projects:${regions.first().id}") {
                    api.fetchOpsProjectsByRegion(regions.first().id)
                }
                val initialProduct = products.firstOrNull { it.id == initialMapping?.productId } ?: products.firstOrNull()
                if (initialProduct != null) {
                    versions = runCatching {
                        ActionSupport.cached("product-versions:${initialProduct.id}") { api.fetchProductVersions(initialProduct.id) }
                    }.getOrDefault(emptyList())
                    modules = runCatching {
                        ActionSupport.cached("product-modules:${initialProduct.id}") { api.fetchModules(initialProduct.id) }
                    }.getOrDefault(emptyList())
                }
                if (devProjects.isEmpty() || regions.isEmpty()) error("DevOps 没有返回可用的研发项目或区域。")
            }

            override fun onSuccess() {
                val settings = ApplicationManager.getApplication().getService(IssueLinkPushSettings::class.java).state
                val dialog = TaskCreationDialog(
                    project, api, operation, repositories, devProjects, regions, workTypes,
                    products, opsProjects, versions, modules, settings.taskCreateMode,
                    initialMapping?.devProjectId, initialMapping?.productId,
                )
                if (!dialog.showAndGet()) return
                execute(project, api, dialog.result())
            }

            override fun onThrowable(error: Throwable) = ActionSupport.notify(project, "加载失败", error.message ?: "加载失败。", NotificationType.ERROR)
        })
    }

    private fun execute(project: com.intellij.openapi.project.Project, api: DevOpsApi, input: TaskCreationDialogResult) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "创建 DevOps 任务", false) {
            private var pushWarning: String? = null

            override fun run(indicator: ProgressIndicator) {
                val settings = ApplicationManager.getApplication().getService(IssueLinkPushSettings::class.java).state
                val today = LocalDate.now(SHANGHAI_ZONE)
                val planHours = if (input.progress < 100) (ChronoUnit.DAYS.between(today, input.endDate) + 1).coerceAtLeast(1) * 8.0 else input.hours
                val workflow = if (operation == TaskCreationOperation.OPS_WITH_GIT) {
                    val repo = requireNotNull(input.repository)
                    GitWorkflow(Git4IdeaCommandRunner(project, repo.repository.root)).also {
                        if (!it.hasStagedChanges()) error("当前没有已暂存的改动，请先 git add。")
                    }
                } else null
                val createInput = CreateTaskInput(
                    taskName = input.taskName,
                    devProjectId = input.devProject.id,
                    productId = input.product.id,
                    regionId = input.region.id,
                    opsProjectId = input.opsProject?.id.orEmpty(),
                    executeUserId = api.getUserId(),
                    importance = "1",
                    priority = "2",
                    workSource = "3",
                    plannedHours = planHours,
                    planStartDate = today.toString(),
                    planEndDate = input.endDate.toString(),
                    expectedCompletionDate = input.endDate.toString(),
                    moduleId = input.module?.id,
                    productVersionId = input.productVersion?.id,
                    remark = input.template.html,
                    workItemCatalog = WorkHourCatalogMapping.taskCatalog(input.workHourType.code),
                )
                val created = api.createTask(createInput)
                val mappingKey = if (operation == TaskCreationOperation.OPS_WITH_GIT) {
                    val repo = requireNotNull(input.repository)
                    "repo:${repo.originUrl ?: repo.label}"
                } else {
                    "workspace:${project.basePath.orEmpty()}"
                }
                ApplicationManager.getApplication().getService(ProductMappingStore::class.java).put(
                    mappingKey, input.devProject.id, input.devProject.name, input.product.id, input.product.name,
                )

                if (operation == TaskCreationOperation.OPS_WITH_GIT) {
                    val repo = requireNotNull(input.repository)
                    val task = DevOpsTask(created.code, created.title, DevOpsTaskType.TASK, "新增", input.product.id, input.product.name, id = created.id, url = created.url)
                    val metadata = DevOpsCommitMetadata(
                        DevOpsProject(input.product.id, input.product.name), task, requireNotNull(input.commitType), input.taskName,
                        format(input.hours), input.progress.toString(), input.workHourType.code, input.workHourType.name,
                    )
                    requireNotNull(workflow).commit(DevOpsCommitFormatter.format(settings.commitTemplate, metadata))
                    try {
                        workflow.push(PushTarget(repo.hasUpstream, input.remote, input.branch))
                    } catch (error: Throwable) {
                        pushWarning = error.message ?: "代码推送失败，请手动推送。"
                    }
                }

                // C04: use the exact work-hour type selected in the dialog.
                api.addWorkHour(
                    created.id, today.toString(), input.hours, "${input.progress}%",
                    input.template.taskDescription.ifBlank { input.taskName }, input.workHourType.code,
                )
            }

            override fun onSuccess() {
                val warning = pushWarning
                if (warning == null) ActionSupport.notify(project, "任务登记完成", "DevOps 任务与工时已登记。")
                else ActionSupport.notify(project, "任务与工时已登记", "代码推送失败：$warning", NotificationType.WARNING)
            }

            override fun onThrowable(error: Throwable) = ActionSupport.notify(project, "任务登记失败", error.message ?: "执行失败。", NotificationType.ERROR)
        })
    }

    private fun format(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    companion object { private val SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai") }
}

class OpsWorkHourRecordAction : BaseTaskCreationAction(TaskCreationOperation.OPS_WITH_GIT)
class DailyTaskAction : BaseTaskCreationAction(TaskCreationOperation.DAILY_ONLY)

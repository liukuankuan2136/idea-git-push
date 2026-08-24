package com.ctjsoft.devops.model

enum class DevOpsTaskType(val configFlag: String, val taskTypeId: String) {
    TASK("Task", "asbdbfkwef"),
    BUG("Bug", "uoyDMdta"),
}

data class DevOpsProject(
    val code: String,
    val name: String,
)

data class DevOpsTask(
    val code: String,
    val title: String,
    val type: DevOpsTaskType,
    val status: String,
    val projectCode: String,
    val projectName: String? = null,
    val estimatedHours: String? = null,
    val usedHours: String? = null,
    val currentProgress: String? = null,
    val url: String? = null,
    val id: String = code,
    val regionId: String? = null,
    val regionName: String? = null,
    val opsProjectId: String? = null,
    val opsProjectName: String? = null,
    val createTime: String? = null,
    val executeUserName: String? = null,
)

data class WorkHourRecord(
    val taskWorkhourId: String,
    val spendTaskTime: Double,
    val workContent: String,
    val taskWorkhourDate: String,
    val dayCompletion: String,
)

data class WorkHourType(
    val id: String,
    val code: String,
    val name: String,
)

data class DevProject(val id: String, val name: String)
data class Product(val id: String, val name: String)
data class Region(val id: String, val name: String)
data class OpsProject(val id: String, val name: String)
data class ProductVersion(val id: String, val name: String)
data class ExecuteUser(val id: String, val code: String, val name: String)
data class DictValue(val id: String, val code: String, val name: String)
data class ProductModule(val id: String, val name: String)

data class CreateTaskInput(
    val taskName: String,
    val devProjectId: String,
    val productId: String,
    val regionId: String,
    val opsProjectId: String,
    val executeUserId: String,
    val importance: String,
    val priority: String,
    val workSource: String,
    val plannedHours: Double,
    val planStartDate: String,
    val planEndDate: String,
    val expectedCompletionDate: String,
    val moduleId: String? = null,
    val productVersionId: String? = null,
    val remark: String? = null,
    val workItemCatalog: String? = null,
)

data class CreateTaskResult(
    val code: String,
    val title: String,
    val id: String,
    val url: String? = null,
)

data class TodayWorkSummary(
    val totalHours: Double,
    val totalHoursText: String,
    val rawTree: List<Any?>,
)

data class DailyReportInput(
    val nowWorkHtml: String,
    val nextPlanHtml: String,
    val otherMattersHtml: String,
    val reportDate: String,
    val recipientUserIds: List<String>,
)

data class DevOpsCommitMetadata(
    val project: DevOpsProject,
    val task: DevOpsTask,
    val commitType: String,
    val subject: String,
    val hours: String,
    val progress: String,
    val workHourTypeCode: String,
    val workHourTypeName: String,
    val todayWorkHour: WorkHourRecord? = null,
)


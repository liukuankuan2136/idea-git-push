package com.ctjsoft.devops.ui

import com.ctjsoft.devops.core.DevOpsCommitFormatter
import com.ctjsoft.devops.model.DevOpsCommitMetadata
import com.ctjsoft.devops.model.DevOpsProject
import com.ctjsoft.devops.model.DevOpsTask
import com.ctjsoft.devops.model.DevOpsTaskType
import com.ctjsoft.devops.model.WorkHourType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import git4idea.repo.GitRepository
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

enum class CommitOperation { AMEND_AND_PUSH, COMMIT_AND_PUSH, COMMIT_ONLY }

data class RepositoryOption(
    val repository: GitRepository,
    val label: String,
    val hasUpstream: Boolean,
    val remotes: List<String>,
    val branch: String,
    val originUrl: String? = null,
) {
    override fun toString(): String = label
}

data class CommitDialogResult(
    val repository: RepositoryOption,
    val taskType: DevOpsTaskType,
    val selectedTask: DevOpsTask?,
    val manualCode: String,
    val commitType: String,
    val subject: String,
    val hours: String,
    val progress: String,
    val workHourType: WorkHourType,
    val remote: String?,
    val branch: String?,
)

class CommitTaskDialog(
    project: Project,
    private val operation: CommitOperation,
    repositories: List<RepositoryOption>,
    private val tasks: Map<DevOpsTaskType, List<DevOpsTask>>,
    workHourTypes: List<WorkHourType>,
    private val commitTemplate: String,
) : DialogWrapper(project) {
    private val repositoryBox = JComboBox(repositories.toTypedArray())
    private val taskTypeBox = JComboBox(DevOpsTaskType.entries.toTypedArray())
    private val taskFilterField = JBTextField()
    private val taskBox = JComboBox<TaskItem>()
    private val manualCodeField = JBTextField()
    private val commitTypeBox = JComboBox(COMMIT_TYPES)
    private val subjectField = JBTextField()
    private val hoursField = JBTextField("1")
    private val progressField = JBTextField("100")
    private val workHourTypeBox = JComboBox(
        (workHourTypes.ifEmpty { listOf(WorkHourType("", "24", "代码编写（默认）")) })
            .map(::WorkHourTypeItem).toTypedArray(),
    )
    private val remoteBox = JComboBox<String>()
    private val branchField = JBTextField()
    private val previewArea = JBTextArea(3, 72).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }

    init {
        title = when (operation) {
            CommitOperation.AMEND_AND_PUSH -> "关联 DevOps 任务并推送"
            CommitOperation.COMMIT_AND_PUSH -> "关联 DevOps 任务提交并推送"
            CommitOperation.COMMIT_ONLY -> "关联 DevOps 任务仅提交"
        }
        setOKButtonText(if (operation == CommitOperation.COMMIT_ONLY) "提交并登记工时" else "执行并登记工时")
        init()
        refreshTasks()
        refreshRepository()
        installListeners()
        updatePreview()
    }

    fun result(): CommitDialogResult {
        val repo = repositoryBox.selectedItem as RepositoryOption
        val type = taskTypeBox.selectedItem as DevOpsTaskType
        val workType = (workHourTypeBox.selectedItem as WorkHourTypeItem).value
        return CommitDialogResult(
            repository = repo,
            taskType = type,
            selectedTask = (taskBox.selectedItem as? TaskItem)?.value,
            manualCode = manualCodeField.text.trim(),
            commitType = commitTypeBox.selectedItem as String,
            subject = subjectField.text.trim(),
            hours = normalizeNumber(hoursField.text),
            progress = normalizeNumber(progressField.text),
            workHourType = workType,
            remote = remoteBox.selectedItem as? String,
            branch = branchField.text.trim().takeIf(String::isNotBlank),
        )
    }

    override fun createCenterPanel(): JComponent = JPanel(GridBagLayout()).apply {
        border = JBUI.Borders.empty(8)
        var row = 0
        fun addRow(label: String, component: JComponent) {
            add(JBLabel(label), constraints(0, row, 0.0))
            add(component, constraints(1, row, 1.0))
            row++
        }
        addRow("Git 仓库", repositoryBox)
        addRow("工作项类型", taskTypeBox)
        addRow("筛选工作项", taskFilterField)
        addRow("未完成工作项", taskBox)
        addRow("手动编号（优先）", manualCodeField)
        addRow("Commit type", commitTypeBox)
        addRow("提交说明", subjectField)
        addRow("投入工时", hoursField)
        addRow("完成度（0-100）", progressField)
        addRow("工时类型", workHourTypeBox)
        if (operation != CommitOperation.COMMIT_ONLY) {
            addRow("远程仓库", remoteBox)
            addRow("远程分支", branchField)
        }
        addRow("Commit 预览", JScrollPane(previewArea))
    }

    override fun doValidate(): ValidationInfo? {
        if (manualCodeField.text.isBlank() && taskBox.selectedItem == null) return ValidationInfo("请选择工作项或输入编号。", taskBox)
        val subject = subjectField.text.trim()
        if (subject.length !in 5..250) return ValidationInfo("提交说明长度必须为 5 到 250 个字符。", subjectField)
        if (Regex("scrum\\s+-e", RegexOption.IGNORE_CASE).containsMatchIn(subject)) return ValidationInfo("提交说明中不要手动输入 scrum -e。", subjectField)
        val hours = hoursField.text.toDoubleOrNull()
        if (hours == null || hours <= 0) return ValidationInfo("工时必须大于 0。", hoursField)
        val progress = progressField.text.toIntOrNull()
        if (progress == null || progress !in 0..100) return ValidationInfo("完成度必须是 0 到 100 的整数。", progressField)
        if (operation != CommitOperation.COMMIT_ONLY) {
            val repo = repositoryBox.selectedItem as RepositoryOption
            if (!repo.hasUpstream && remoteBox.selectedItem == null) return ValidationInfo("当前分支无 upstream，请选择远程仓库。", remoteBox)
            if (!repo.hasUpstream && branchField.text.isBlank()) return ValidationInfo("当前分支无 upstream，请填写远程分支。", branchField)
        }
        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = subjectField

    private fun installListeners() {
        taskTypeBox.addActionListener { refreshTasks(); updatePreview() }
        repositoryBox.addActionListener { refreshRepository() }
        listOf(taskBox, commitTypeBox, workHourTypeBox).forEach { it.addActionListener { updatePreview() } }
        listOf(taskFilterField, manualCodeField, subjectField, hoursField, progressField).forEach { field ->
            field.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent?) = changed(field)
                override fun removeUpdate(event: DocumentEvent?) = changed(field)
                override fun changedUpdate(event: DocumentEvent?) = changed(field)
            })
        }
    }

    private fun refreshTasks() {
        val selected = taskTypeBox.selectedItem as DevOpsTaskType
        val query = taskFilterField.text.trim()
        taskBox.removeAllItems()
        tasks[selected].orEmpty().asSequence()
            .filter { task -> query.isBlank() || listOf(task.code, task.title, task.projectName.orEmpty(), task.projectCode).any { it.contains(query, ignoreCase = true) } }
            .forEach { taskBox.addItem(TaskItem(it)) }
    }

    private fun refreshRepository() {
        val selected = repositoryBox.selectedItem as? RepositoryOption ?: return
        remoteBox.removeAllItems()
        selected.remotes.forEach(remoteBox::addItem)
        remoteBox.isEnabled = !selected.hasUpstream
        branchField.text = selected.branch
        branchField.isEnabled = !selected.hasUpstream
    }

    private fun updatePreview() {
        val task = (taskBox.selectedItem as? TaskItem)?.value
        val code = manualCodeField.text.trim().ifBlank { task?.code.orEmpty() }
        val subject = subjectField.text.trim()
        if (code.isBlank() || subject.isBlank()) {
            previewArea.text = "填写工作项与提交说明后显示预览。"
            return
        }
        val previewTask = task ?: DevOpsTask(code, code, taskTypeBox.selectedItem as DevOpsTaskType, "", "")
        val workType = (workHourTypeBox.selectedItem as? WorkHourTypeItem)?.value ?: return
        val metadata = DevOpsCommitMetadata(
            DevOpsProject(previewTask.projectCode, previewTask.projectName ?: previewTask.projectCode),
            previewTask,
            commitTypeBox.selectedItem as String,
            subject,
            hoursField.text.ifBlank { "?" },
            progressField.text.ifBlank { "?" },
            workType.code,
            workType.name,
        )
        previewArea.text = DevOpsCommitFormatter.format(commitTemplate, metadata)
    }

    private fun changed(field: JBTextField) {
        if (field === taskFilterField) refreshTasks()
        updatePreview()
    }

    private fun constraints(column: Int, row: Int, weight: Double) = GridBagConstraints().apply {
        gridx = column; gridy = row; weightx = weight
        fill = if (column == 1) GridBagConstraints.HORIZONTAL else GridBagConstraints.NONE
        anchor = GridBagConstraints.NORTHWEST
        insets = JBUI.insets(4, 4, 4, 8)
    }

    private data class TaskItem(val value: DevOpsTask) {
        override fun toString(): String = "${value.code}  ${value.title}  [${value.projectName ?: value.projectCode}]"
    }

    private data class WorkHourTypeItem(val value: WorkHourType) {
        override fun toString(): String = value.name
    }

    companion object {
        private val COMMIT_TYPES = arrayOf("feat", "fix", "perf", "refactor", "test", "style", "build", "chore", "upd", "Merge", "doc")
        private fun normalizeNumber(value: String): String = value.toDoubleOrNull()?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: value
    }
}

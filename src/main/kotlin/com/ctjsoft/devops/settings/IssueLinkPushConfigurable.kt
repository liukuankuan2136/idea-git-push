package com.ctjsoft.devops.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class IssueLinkPushConfigurable : Configurable {
    private val settings get() = ApplicationManager.getApplication().getService(IssueLinkPushSettings::class.java)
    private val credentials get() = ApplicationManager.getApplication().getService(DevOpsCredentialStore::class.java)

    private var panel: JPanel? = null
    private val usernameField = JBTextField()
    private val passwordField = JBPasswordField()
    private val commitTemplateField = JBTextField()
    private val timeoutSpinner = JSpinner(SpinnerNumberModel(10_000, 1_000, 120_000, 1_000))
    private val cacheSpinner = JSpinner(SpinnerNumberModel(300_000, 0, 3_600_000, 10_000))
    private val workHourMode = JComboBox(RecordMode.entries.toTypedArray())
    private val workContentMode = JComboBox(RecordMode.entries.toTypedArray())
    private val progressMode = JComboBox(RecordMode.entries.toTypedArray())
    private val taskCreateMode = JComboBox(TaskCreateMode.entries.toTypedArray())
    private val debugMode = JBCheckBox("启用脱敏诊断日志")
    private val upgradeReminder = JBCheckBox("升级后显示提醒")

    override fun getDisplayName(): String = "Issue Link Push"

    override fun createComponent(): JComponent = panel ?: JPanel(GridBagLayout()).also { root ->
        root.border = JBUI.Borders.empty(12)
        var row = 0
        fun addRow(label: String, component: JComponent, note: String? = null) {
            root.add(JBLabel(label), constraints(0, row, 0.0))
            root.add(component, constraints(1, row, 1.0))
            row++
            if (note != null) {
                root.add(JBLabel("<html><small>$note</small></html>"), constraints(1, row, 1.0))
                row++
            }
        }
        addRow("DevOps 用户名", usernameField)
        addRow("登录密码密文", passwordField, "填写浏览器 login 请求负载中的 password 字段；保存到 IDEA PasswordSafe。")
        addRow("Commit 模板", commitTemplateField)
        addRow("请求超时（毫秒）", timeoutSpinner)
        addRow("缓存时长（毫秒）", cacheSpinner)
        addRow("工时记录", workHourMode)
        addRow("工时描述", workContentMode)
        addRow("完成百分比", progressMode)
        addRow("任务内容模式", taskCreateMode)
        root.add(debugMode, constraints(1, row++, 1.0))
        root.add(upgradeReminder, constraints(1, row++, 1.0))
        root.add(JPanel(), GridBagConstraints().apply {
            gridx = 0; gridy = row; gridwidth = 2; weightx = 1.0; weighty = 1.0
            fill = GridBagConstraints.BOTH
        })
        panel = root
        reset()
    }

    override fun isModified(): Boolean {
        val state = settings.state
        val stored = credentials.load()
        return usernameField.text.trim() != stored?.username.orEmpty() ||
            String(passwordField.password) != stored?.encryptedPassword.orEmpty() ||
            commitTemplateField.text != state.commitTemplate ||
            timeoutSpinner.value != state.requestTimeoutMillis ||
            cacheSpinner.value != state.cacheTtlMillis ||
            workHourMode.selectedItem != state.workHourMode ||
            workContentMode.selectedItem != state.workContentMode ||
            progressMode.selectedItem != state.progressMode ||
            taskCreateMode.selectedItem != state.taskCreateMode ||
            debugMode.isSelected != state.debugMode ||
            upgradeReminder.isSelected != state.upgradeReminder
    }

    override fun apply() {
        val state = settings.state
        state.commitTemplate = commitTemplateField.text
        state.requestTimeoutMillis = timeoutSpinner.value as Int
        state.cacheTtlMillis = cacheSpinner.value as Int
        state.workHourMode = workHourMode.selectedItem as RecordMode
        state.workContentMode = workContentMode.selectedItem as RecordMode
        state.progressMode = progressMode.selectedItem as RecordMode
        state.taskCreateMode = taskCreateMode.selectedItem as TaskCreateMode
        state.debugMode = debugMode.isSelected
        state.upgradeReminder = upgradeReminder.isSelected
        val username = usernameField.text.trim()
        val password = String(passwordField.password)
        if (username.isBlank() && password.isBlank()) credentials.clear() else credentials.save(username, password)
    }

    override fun reset() {
        val state = settings.state
        val stored = credentials.load()
        usernameField.text = stored?.username.orEmpty()
        passwordField.text = stored?.encryptedPassword.orEmpty()
        commitTemplateField.text = state.commitTemplate
        timeoutSpinner.value = state.requestTimeoutMillis
        cacheSpinner.value = state.cacheTtlMillis
        workHourMode.selectedItem = state.workHourMode
        workContentMode.selectedItem = state.workContentMode
        progressMode.selectedItem = state.progressMode
        taskCreateMode.selectedItem = state.taskCreateMode
        debugMode.isSelected = state.debugMode
        upgradeReminder.isSelected = state.upgradeReminder
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun constraints(column: Int, row: Int, weight: Double) = GridBagConstraints().apply {
        gridx = column
        gridy = row
        weightx = weight
        fill = if (column == 1) GridBagConstraints.HORIZONTAL else GridBagConstraints.NONE
        anchor = GridBagConstraints.WEST
        insets = JBUI.insets(4, 4, 4, 8)
    }
}


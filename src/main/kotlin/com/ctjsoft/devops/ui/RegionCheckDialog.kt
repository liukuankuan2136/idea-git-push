package com.ctjsoft.devops.ui

import com.ctjsoft.devops.api.DevOpsApi
import com.ctjsoft.devops.api.DevOpsRuntime
import com.ctjsoft.devops.core.RegionCompliance
import com.ctjsoft.devops.model.DevProject
import com.ctjsoft.devops.model.Product
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.concurrent.CompletableFuture
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

class RegionCheckDialog(
    project: Project,
    private val api: DevOpsApi,
    devProjects: List<DevProject>,
    initialProducts: List<Product>,
) : DialogWrapper(project) {
    private val projectBox = JComboBox(devProjects.map { Item(it.name, it) }.toTypedArray())
    private val productBox = JComboBox<Item<Product>>()
    private val status = JBLabel("请选择范围后开始检查。")
    private val report = JBTextArea(28, 92).apply { isEditable = false; lineWrap = false }
    private var completed = false
    private var running = false

    init {
        title = "区域合规检查"
        fillProducts(initialProducts)
        diagnostic("[region] dialog initialized projects=${devProjects.size} initialProducts=${initialProducts.size}")
        setOKButtonText("开始检查")
        init()
        projectBox.addActionListener {
            diagnostic("[region] project selection changed")
            loadProducts((projectBox.selectedItem as Item<DevProject>).value.id)
        }
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
        border = JBUI.Borders.empty(8)
        val selectors = JPanel(GridBagLayout()).apply {
            add(JBLabel("研发项目"), constraints(0)); add(projectBox, constraints(1))
            add(JBLabel("所属产品"), constraints(2)); add(productBox, constraints(3))
        }
        add(selectors, BorderLayout.NORTH)
        add(JScrollPane(report), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
    }

    override fun doValidate(): ValidationInfo? = if (!completed && productBox.selectedItem == null) ValidationInfo("请选择产品。", productBox) else null

    override fun doOKAction() {
        if (completed) {
            super.doOKAction()
            return
        }
        if (running || doValidate() != null) return
        running = true
        isOKActionEnabled = false
        status.text = "正在拉取本周任务与工时并执行检查…"
        diagnostic("[region] report check started")
        val project = (projectBox.selectedItem as Item<DevProject>).value
        val product = (productBox.selectedItem as Item<Product>).value
        CompletableFuture.supplyAsync { buildReport(project, product) }.whenComplete { text, error ->
            diagnostic("[region] report callback received")
            ApplicationManager.getApplication().invokeLater({
                running = false
                completed = true
                report.text = if (error == null) text else "检查失败：${error.cause?.message ?: error.message}"
                report.caretPosition = 0
                status.text = if (error == null) "检查完成。" else "检查失败。"
                if (error == null) {
                    diagnostic("[region] report check completed")
                } else {
                    val cause = error.cause ?: error
                    diagnostic("[region] report check failed ${cause.javaClass.simpleName}: ${cause.message.orEmpty()}")
                }
                setOKButtonText("关闭")
                isOKActionEnabled = true
            }, ModalityState.any())
        }
    }

    private fun buildReport(devProject: DevProject, product: Product): String {
        diagnostic("[region] report task load started")
        val tasks = api.fetchTasksByProduct(devProject.id, product.id)
        diagnostic("[region] report tasks loaded count=${tasks.size}")
        data class Checked(val task: com.ctjsoft.devops.model.DevOpsTask, val records: List<com.ctjsoft.devops.model.WorkHourRecord>, val result: com.ctjsoft.devops.core.RegionCheckResult)
        val checked = tasks.map { task ->
            val records = runCatching { api.fetchWorkHours(task.id) }.getOrElse { error ->
                diagnostic("[region] work-hour load failed ${error.javaClass.simpleName}: ${error.message.orEmpty()}")
                emptyList()
            }
            val contents = records.joinToString("\n") { it.workContent }
            Checked(task, records, RegionCompliance.check(task.title, contents, task.regionName.orEmpty(), task.opsProjectName.orEmpty()))
        }
        val violations = checked.filter { it.result.hasViolation }
        return buildString {
            appendLine("区域合规检查报告")
            appendLine("研发项目：${devProject.name}")
            appendLine("所属产品：${product.name}")
            appendLine("检查任务：${checked.size}；合规：${checked.size - violations.size}；存疑：${violations.size}")
            appendLine("=".repeat(72))
            if (violations.isEmpty()) appendLine("所有任务均通过区域合规检查。")
            violations.forEach { item ->
                appendLine("【${item.task.code}】${item.task.title}")
                appendLine("处理人：${item.task.executeUserName.orEmpty()}；区域：${item.task.regionName.orEmpty()}；实施项目：${item.task.opsProjectName.orEmpty()}")
                if (item.result.regionViolations.isNotEmpty()) appendLine("区域异常：${item.result.regionViolations.joinToString { "${it.name}(${it.province})" }}")
                if (item.result.opsProjectViolations.isNotEmpty()) appendLine("实施项目异常：${item.result.opsProjectViolations.joinToString { "${it.name}(${it.province})" }}")
                item.records.filter { it.workContent.isNotBlank() }.forEach { appendLine("  [${it.taskWorkhourDate}] ${it.workContent.take(200)}") }
                appendLine("-".repeat(72))
            }
            appendLine("全部任务编号：${checked.joinToString { it.task.code }}")
            if (violations.isNotEmpty()) appendLine("存疑任务编号：${violations.joinToString { it.task.code }}")
        }
    }

    private fun loadProducts(devProjectId: String) {
        status.text = "正在加载产品…"
        diagnostic("[region] product load started")
        CompletableFuture.supplyAsync {
            ApplicationManager.getApplication().getService(DevOpsRuntime::class.java)
                .cached("products:$devProjectId") { api.fetchProductsByProject(devProjectId) }
        }.whenComplete { products, error ->
            diagnostic("[region] product load callback received")
            ApplicationManager.getApplication().invokeLater({
                if (error == null) {
                    fillProducts(products)
                    status.text = "请选择范围后开始检查。"
                    diagnostic("[region] product load completed count=${products.size}")
                } else {
                    val cause = error.cause ?: error
                    status.text = "产品加载失败：${cause.message ?: cause.javaClass.simpleName}"
                    diagnostic("[region] product load failed ${cause.javaClass.simpleName}: ${cause.message.orEmpty()}")
                }
            }, ModalityState.any())
        }
    }

    private fun fillProducts(products: List<Product>) {
        productBox.removeAllItems(); products.forEach { productBox.addItem(Item(it.name, it)) }
    }

    private fun diagnostic(message: String) {
        ApplicationManager.getApplication().getService(DevOpsRuntime::class.java).diagnostic(message)
    }

    private fun constraints(column: Int) = GridBagConstraints().apply {
        gridx = column; gridy = 0; weightx = if (column % 2 == 1) 1.0 else 0.0
        fill = if (column % 2 == 1) GridBagConstraints.HORIZONTAL else GridBagConstraints.NONE
        insets = JBUI.insets(4)
    }
    private data class Item<T>(val label: String, val value: T) { override fun toString(): String = label }
}

package com.ctjsoft.devops.ui

import com.ctjsoft.devops.api.DevOpsApi
import com.ctjsoft.devops.api.DevOpsRuntime
import com.ctjsoft.devops.core.TaskTemplateBuilder
import com.ctjsoft.devops.core.TaskTemplateResult
import com.ctjsoft.devops.model.DevProject
import com.ctjsoft.devops.model.OpsProject
import com.ctjsoft.devops.model.Product
import com.ctjsoft.devops.model.ProductModule
import com.ctjsoft.devops.model.ProductVersion
import com.ctjsoft.devops.model.Region
import com.ctjsoft.devops.model.WorkHourType
import com.ctjsoft.devops.settings.TaskCreateMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

enum class TaskCreationOperation { OPS_WITH_GIT, DAILY_ONLY }

data class TaskCreationDialogResult(
    val repository: RepositoryOption?,
    val taskName: String,
    val devProject: DevProject,
    val product: Product,
    val region: Region,
    val opsProject: OpsProject?,
    val productVersion: ProductVersion?,
    val module: ProductModule?,
    val workHourType: WorkHourType,
    val hours: Double,
    val progress: Int,
    val endDate: LocalDate,
    val commitType: String?,
    val remote: String?,
    val branch: String?,
    val template: TaskTemplateResult,
)

class TaskCreationDialog(
    project: Project,
    private val api: DevOpsApi,
    private val operation: TaskCreationOperation,
    repositories: List<RepositoryOption>,
    devProjects: List<DevProject>,
    regions: List<Region>,
    workHourTypes: List<WorkHourType>,
    initialProducts: List<Product>,
    initialOpsProjects: List<OpsProject>,
    initialVersions: List<ProductVersion>,
    initialModules: List<ProductModule>,
    private val templateMode: TaskCreateMode,
    initialDevProjectId: String? = null,
    initialProductId: String? = null,
) : DialogWrapper(project) {
    private val repositoryBox = JComboBox(repositories.toTypedArray())
    private val taskNameField = JBTextField()
    private val devProjectBox = JComboBox(devProjects.map { Item(it.name, it) }.toTypedArray())
    private val productBox = JComboBox<Item<Product>>()
    private val regionBox = JComboBox(regions.map { Item(it.name, it) }.toTypedArray())
    private val opsProjectBox = JComboBox<Item<OpsProject>>()
    private val versionBox = JComboBox<Item<ProductVersion>>()
    private val moduleBox = JComboBox<Item<ProductModule>>()
    private val workHourTypeBox = JComboBox(
        workHourTypes.ifEmpty { listOf(WorkHourType("", "24", "代码编写（默认）")) }.map { Item(it.name, it) }.toTypedArray(),
    )
    private val hoursField = JBTextField("1")
    private val progressField = JBTextField("100")
    private val endDateField = JBTextField(LocalDate.now().toString())
    private val commitTypeBox = JComboBox(COMMIT_TYPES)
    private val remoteBox = JComboBox<String>()
    private val branchField = JBTextField()
    private val statusLabel = JBLabel(" ")
    private val sectionAreas = TaskTemplateBuilder.sections.associate { section ->
        section.id to JBTextArea(3, 68).apply { lineWrap = true; wrapStyleWord = true }
    }

    init {
        title = if (operation == TaskCreationOperation.OPS_WITH_GIT) "运维工时补录" else "日常任务登记"
        setOKButtonText(if (operation == TaskCreationOperation.OPS_WITH_GIT) "创建、提交、推送并登记工时" else "创建并登记工时")
        selectValue(devProjectBox, initialDevProjectId)
        fillProducts(initialProducts)
        selectValue(productBox, initialProductId)
        fillOpsProjects(initialOpsProjects)
        fillVersions(initialVersions)
        fillModules(initialModules)
        init()
        installListeners()
        if (operation == TaskCreationOperation.OPS_WITH_GIT) refreshRepository()
    }

    fun result(): TaskCreationDialogResult {
        val templateInput = sectionAreas.mapValues { it.value.text }
        return TaskCreationDialogResult(
            repository = (repositoryBox.selectedItem as? RepositoryOption),
            taskName = taskNameField.text.trim(),
            devProject = selected(devProjectBox),
            product = selected(productBox),
            region = selected(regionBox),
            opsProject = selectedOrNull(opsProjectBox),
            productVersion = selectedOrNull(versionBox),
            module = selectedOrNull(moduleBox),
            workHourType = selected(workHourTypeBox),
            hours = hoursField.text.toDouble(),
            progress = progressField.text.toInt(),
            endDate = LocalDate.parse(endDateField.text.trim()),
            commitType = if (operation == TaskCreationOperation.OPS_WITH_GIT) commitTypeBox.selectedItem as String else null,
            remote = remoteBox.selectedItem as? String,
            branch = branchField.text.trim().takeIf(String::isNotBlank),
            template = TaskTemplateBuilder.build(templateMode, templateInput),
        )
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 6)).apply {
        border = JBUI.Borders.empty(8)
        val tabs = JTabbedPane()
        tabs.addTab("任务与工时", JScrollPane(createMainPanel()))
        tabs.addTab("任务内容（${modeLabel()}）", JScrollPane(createTemplatePanel()))
        add(tabs, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
    }

    override fun doValidate(): ValidationInfo? {
        val title = taskNameField.text.trim()
        if (title.length !in 3..200) return ValidationInfo("任务标题长度必须为 3 到 200 个字符。", taskNameField)
        if (productBox.selectedItem == null) return ValidationInfo("请选择所属产品。", productBox)
        if (regionBox.selectedItem == null) return ValidationInfo("请选择区域。", regionBox)
        val hours = hoursField.text.toDoubleOrNull()
        if (hours == null || hours <= 0) return ValidationInfo("工时必须大于 0。", hoursField)
        val progress = progressField.text.toIntOrNull()
        if (progress == null || progress !in 0..100) return ValidationInfo("完成度必须是 0 到 100 的整数。", progressField)
        val endDate = runCatching { LocalDate.parse(endDateField.text.trim()) }.getOrNull()
        if (endDate == null || endDate.isBefore(LocalDate.now())) return ValidationInfo("预计结束日期格式应为 YYYY-MM-DD，且不能早于今天。", endDateField)
        val description = sectionAreas.getValue("taskDesc").text.trim()
        if (templateMode != TaskCreateMode.BENCHMARK && description.length < 20) {
            return ValidationInfo("任务描述至少需要 20 个字符。", sectionAreas.getValue("taskDesc"))
        }
        if (operation == TaskCreationOperation.OPS_WITH_GIT) {
            val repo = repositoryBox.selectedItem as? RepositoryOption ?: return ValidationInfo("请选择 Git 仓库。", repositoryBox)
            if (!repo.hasUpstream && remoteBox.selectedItem == null) return ValidationInfo("当前分支无 upstream，请选择远程仓库。", remoteBox)
            if (!repo.hasUpstream && branchField.text.isBlank()) return ValidationInfo("请填写远程分支。", branchField)
        }
        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = taskNameField

    private fun createMainPanel(): JPanel = JPanel(GridBagLayout()).apply {
        var row = 0
        fun addRow(label: String, component: JComponent) {
            add(JBLabel(label), constraints(0, row, 0.0)); add(component, constraints(1, row, 1.0)); row++
        }
        if (operation == TaskCreationOperation.OPS_WITH_GIT) addRow("Git 仓库", repositoryBox)
        addRow("任务标题", taskNameField)
        addRow("研发项目", devProjectBox)
        addRow("所属产品", productBox)
        addRow("产品版本", versionBox)
        addRow("产品模块", moduleBox)
        addRow("区域", regionBox)
        addRow("实施项目", opsProjectBox)
        addRow("工时类型", workHourTypeBox)
        addRow("投入工时", hoursField)
        addRow("完成度（0-100）", progressField)
        addRow("预计结束日期", endDateField)
        if (operation == TaskCreationOperation.OPS_WITH_GIT) {
            addRow("Commit type", commitTypeBox)
            addRow("远程仓库", remoteBox)
            addRow("远程分支", branchField)
        }
    }

    private fun createTemplatePanel(): JPanel = JPanel(GridBagLayout()).apply {
        var row = 0
        val visible = TaskTemplateBuilder.visibleSectionIds(templateMode)
        TaskTemplateBuilder.sections.filter { it.id in visible }.forEach { section ->
            add(JBLabel(section.label), constraints(0, row, 0.0))
            add(JScrollPane(sectionAreas.getValue(section.id)), constraints(1, row, 1.0).apply { fill = GridBagConstraints.BOTH; weighty = 0.2 })
            row++
        }
        add(JBLabel("未显示章节会继续写入 VS Code 现状中的默认指导文案。"), constraints(1, row, 1.0))
    }

    private fun installListeners() {
        devProjectBox.addActionListener { loadProducts(selected(devProjectBox).id) }
        productBox.addActionListener {
            val product = selectedOrNull(productBox) ?: return@addActionListener
            loadProductChildren(product.id)
        }
        regionBox.addActionListener { loadOpsProjects(selected(regionBox).id) }
        repositoryBox.addActionListener { refreshRepository() }
        taskNameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent?) = matchRegionFromTitle()
            override fun removeUpdate(event: DocumentEvent?) = Unit
            override fun changedUpdate(event: DocumentEvent?) = Unit
        })
    }

    private fun loadProducts(devProjectId: String) = asyncLoad("正在加载产品…", {
        cached("products:$devProjectId") { api.fetchProductsByProject(devProjectId) }
    }) { fillProducts(it) }
    private fun loadOpsProjects(regionId: String) = asyncLoad("正在加载实施项目…", {
        cached("ops-projects:$regionId") { api.fetchOpsProjectsByRegion(regionId) }
    }) { fillOpsProjects(it) }
    private fun loadProductChildren(productId: String) {
        asyncLoad("正在加载产品版本与模块…", {
            cached("product-versions:$productId") { api.fetchProductVersions(productId) } to
                cached("product-modules:$productId") { api.fetchModules(productId) }
        }) {
            fillVersions(it.first); fillModules(it.second)
        }
    }

    private fun <T : Any> cached(key: String, loader: () -> T): T = ApplicationManager.getApplication()
        .getService(DevOpsRuntime::class.java).cached(key, loader)

    private fun <T> asyncLoad(message: String, loader: () -> T, consumer: (T) -> Unit) {
        statusLabel.text = message
        CompletableFuture.supplyAsync(loader).whenComplete { value, error ->
            ApplicationManager.getApplication().invokeLater {
                if (error == null) {
                    consumer(value)
                    statusLabel.text = " "
                } else statusLabel.text = "加载失败：${error.cause?.message ?: error.message}"
            }
        }
    }

    private fun fillProducts(values: List<Product>) = fill(productBox, values.map { Item(it.name, it) })
    private fun fillOpsProjects(values: List<OpsProject>) = fill(opsProjectBox, values.map { Item(it.name, it) }, "（不关联）")
    private fun fillVersions(values: List<ProductVersion>) = fill(versionBox, values.map { Item(it.name, it) }, "（自动）")
    private fun fillModules(values: List<ProductModule>) = fill(moduleBox, values.map { Item(it.name, it) }, "（不关联）")

    private fun <T> fill(box: JComboBox<Item<T>>, values: List<Item<T>>, emptyLabel: String? = null) {
        box.removeAllItems()
        if (emptyLabel != null) box.addItem(Item(emptyLabel, null))
        values.forEach(box::addItem)
    }

    private fun <T> selectValue(box: JComboBox<Item<T>>, id: String?) {
        if (id.isNullOrBlank()) return
        for (index in 0 until box.itemCount) {
            val value = box.getItemAt(index).value
            val valueId = when (value) {
                is DevProject -> value.id
                is Product -> value.id
                else -> null
            }
            if (valueId == id) {
                box.selectedIndex = index
                return
            }
        }
    }

    private fun refreshRepository() {
        val repository = repositoryBox.selectedItem as? RepositoryOption ?: return
        remoteBox.removeAllItems(); repository.remotes.forEach(remoteBox::addItem)
        remoteBox.isEnabled = !repository.hasUpstream
        branchField.text = repository.branch
        branchField.isEnabled = !repository.hasUpstream
    }

    private fun matchRegionFromTitle() {
        val title = taskNameField.text
        for (i in 0 until regionBox.itemCount) {
            val item = regionBox.getItemAt(i)
            if (title.contains(item.value?.name.orEmpty(), ignoreCase = true)) {
                regionBox.selectedIndex = i
                return
            }
        }
    }

    private fun modeLabel() = when (templateMode) {
        TaskCreateMode.SIMPLE -> "简易"
        TaskCreateMode.NORMAL -> "普通"
        TaskCreateMode.BENCHMARK -> "标杆"
    }

    private fun constraints(column: Int, row: Int, weight: Double) = GridBagConstraints().apply {
        gridx = column; gridy = row; weightx = weight
        fill = if (column == 1) GridBagConstraints.HORIZONTAL else GridBagConstraints.NONE
        anchor = GridBagConstraints.NORTHWEST
        insets = JBUI.insets(4, 4, 4, 8)
    }

    @Suppress("UNCHECKED_CAST") private fun <T> selected(box: JComboBox<Item<T>>): T = (box.selectedItem as Item<T>).value!!
    @Suppress("UNCHECKED_CAST") private fun <T> selectedOrNull(box: JComboBox<Item<T>>): T? = (box.selectedItem as? Item<T>)?.value

    private data class Item<T>(val label: String, val value: T?) { override fun toString(): String = label }

    companion object {
        private val COMMIT_TYPES = arrayOf("feat", "fix", "perf", "refactor", "test", "style", "build", "chore", "upd", "Merge", "doc")
    }
}

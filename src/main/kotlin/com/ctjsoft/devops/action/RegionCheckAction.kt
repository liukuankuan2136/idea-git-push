package com.ctjsoft.devops.action

import com.ctjsoft.devops.api.DevOpsApi
import com.ctjsoft.devops.ui.RegionCheckDialog
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task

class RegionCheckAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        ActionSupport.diagnostic("[region] action invoked")
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "加载区域检查范围", false) {
            private lateinit var api: DevOpsApi
            private var projects = emptyList<com.ctjsoft.devops.model.DevProject>()
            private var products = emptyList<com.ctjsoft.devops.model.Product>()

            override fun run(indicator: ProgressIndicator) {
                ActionSupport.diagnostic("[region] scope load started")
                api = ActionSupport.api()
                projects = ActionSupport.cached("dev-projects") { api.fetchDevProjects() }
                ActionSupport.diagnostic("[region] projects loaded count=${projects.size}")
                if (projects.isEmpty()) error("没有可用的研发项目。")
                products = ActionSupport.cached("products:${projects.first().id}") {
                    api.fetchProductsByProject(projects.first().id)
                }
                ActionSupport.diagnostic("[region] initial products loaded count=${products.size}")
            }

            override fun onSuccess() {
                ActionSupport.diagnostic("[region] opening dialog projects=${projects.size} initialProducts=${products.size}")
                RegionCheckDialog(project, api, projects, products).show()
            }

            override fun onThrowable(error: Throwable): Unit {
                ActionSupport.diagnostic("[region] scope load failed ${error.javaClass.simpleName}: ${error.message.orEmpty()}")
                ActionSupport.notify(project, "区域检查加载失败", error.message ?: "加载失败。", NotificationType.ERROR)
            }
        })
    }
}

package com.ctjsoft.devops.action

import com.ctjsoft.devops.settings.IssueLinkPushSettings
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class UpgradeReminderActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val version = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))?.version ?: return
        val state = ApplicationManager.getApplication().getService(IssueLinkPushSettings::class.java).state
        val previous = state.lastSeenVersion
        state.lastSeenVersion = version
        if (state.upgradeReminder && previous.isNotBlank() && previous != version) {
            ActionSupport.notify(project, "Issue Link Push 已升级", "版本已从 $previous 升级到 $version，建议执行一次关键流程冒烟测试。")
        }
    }

    companion object {
        private const val PLUGIN_ID = "com.ctjsoft.devops.issue-link-push"
    }
}

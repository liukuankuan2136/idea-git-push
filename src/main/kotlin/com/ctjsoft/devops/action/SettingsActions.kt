package com.ctjsoft.devops.action

import com.ctjsoft.devops.settings.IssueLinkPushConfigurable
import com.ctjsoft.devops.settings.IssueLinkPushSettings
import com.ctjsoft.devops.api.DevOpsRuntime
import com.ctjsoft.devops.settings.ProductMappingStore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil

class InitializeDevOpsAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(event.project, IssueLinkPushConfigurable::class.java)
    }
}

class ClearCacheAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        ApplicationManager.getApplication().getService(DevOpsRuntime::class.java).clear()
        ApplicationManager.getApplication().getService(ProductMappingStore::class.java).clear()
        ApplicationManager.getApplication().getService(IssueLinkPushSettings::class.java).state.lastSeenVersion = ""
        ActionSupport.notify(event.project, "Issue Link Push", "运行时缓存、版本记录、仓库映射和工作区映射已清除；账号凭据未修改。")
    }
}

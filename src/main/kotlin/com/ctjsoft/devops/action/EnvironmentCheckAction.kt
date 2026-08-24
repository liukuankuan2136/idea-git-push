package com.ctjsoft.devops.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class EnvironmentCheckAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        Messages.showInfoMessage(
            event.project,
            "插件已在 IntelliJ IDEA 2026.1.4 环境中加载。",
            "Issue Link Push",
        )
    }
}


package com.ctjsoft.devops.action

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopupFactory

class OpenIssueLinkPushAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val group = ActionManager.getInstance().getAction("IssueLinkPush.Menu") as? ActionGroup ?: return
        JBPopupFactory.getInstance()
            .createActionGroupPopup(
                "Issue Link Push",
                group,
                event.dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true,
            )
            .showInBestPositionFor(event.dataContext)
    }
}

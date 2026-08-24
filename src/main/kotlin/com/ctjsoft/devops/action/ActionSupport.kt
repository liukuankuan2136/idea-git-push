package com.ctjsoft.devops.action

import com.ctjsoft.devops.api.DevOpsApi
import com.ctjsoft.devops.api.DevOpsRuntime
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

internal object ActionSupport {
    fun runtime(): DevOpsRuntime = ApplicationManager.getApplication().getService(DevOpsRuntime::class.java)
    fun api(): DevOpsApi = runtime().api()

    fun diagnostic(message: String) = runtime().diagnostic(message)

    fun <T : Any> cached(key: String, loader: () -> T): T = runtime().cached(key, loader)

    fun notify(project: Project?, title: String, content: String, type: NotificationType = NotificationType.INFORMATION) {
        NotificationGroupManager.getInstance().getNotificationGroup("Issue Link Push")
            .createNotification(title, content, type).notify(project)
    }
}

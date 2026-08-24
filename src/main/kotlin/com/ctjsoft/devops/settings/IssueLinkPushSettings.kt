package com.ctjsoft.devops.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

enum class RecordMode { APPEND, OVERWRITE }
enum class TaskCreateMode { SIMPLE, NORMAL, BENCHMARK }

@Service(Service.Level.APP)
@State(name = "IssueLinkPushSettings", storages = [Storage("issueLinkPush.xml")])
class IssueLinkPushSettings : PersistentStateComponent<IssueLinkPushSettings.Data> {
    data class Data(
        var schemaVersion: Int = 1,
        var commitTemplate: String = DEFAULT_COMMIT_TEMPLATE,
        var requestTimeoutMillis: Int = 10_000,
        var cacheTtlMillis: Int = 300_000,
        var workHourMode: RecordMode = RecordMode.APPEND,
        var workContentMode: RecordMode = RecordMode.APPEND,
        var progressMode: RecordMode = RecordMode.OVERWRITE,
        var taskCreateMode: TaskCreateMode = TaskCreateMode.SIMPLE,
        var debugMode: Boolean = false,
        var upgradeReminder: Boolean = false,
        var lastSeenVersion: String = "",
    )

    private var data = Data()

    override fun getState(): Data = data

    override fun loadState(state: Data) {
        XmlSerializerUtil.copyBean(state, data)
    }

    companion object {
        const val DEFAULT_COMMIT_TEMPLATE = "\${COMMIT_TYPE}:\${SUBJECT} scrum -e \${CODE} -h:\${HOURS} -s:\${PROGRESS}"
    }
}

package com.ctjsoft.devops.core

import com.ctjsoft.devops.model.DevOpsCommitMetadata

object DevOpsCommitFormatter {
    fun format(template: String, metadata: DevOpsCommitMetadata): String {
        val resolved = if (metadata.workHourTypeName.contains("AI", ignoreCase = true)) {
            template.replace("-h:", "-aih:")
        } else {
            template
        }

        val message = resolved
            .replace("\${COMMIT_TYPE}", metadata.commitType)
            .replace("\${SUBJECT}", metadata.subject)
            .replace("\${CODE}", metadata.task.code)
            .replace("\${HOURS}", metadata.hours)
            .replace("\${PROGRESS}", metadata.progress)
            .replace("\${TYPE}", metadata.task.type.name.lowercase())
            .replace("\${PROJECT}", metadata.project.code)

        return if (metadata.commitType == "Merge") message.replaceFirst("Merge:", "Merge ") else message
    }
}


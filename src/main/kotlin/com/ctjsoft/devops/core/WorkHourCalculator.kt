package com.ctjsoft.devops.core

import com.ctjsoft.devops.model.DevOpsCommitMetadata
import com.ctjsoft.devops.settings.RecordMode

object WorkHourCalculator {
    fun hours(metadata: DevOpsCommitMetadata, mode: RecordMode): Double {
        val input = metadata.hours.toDouble()
        return if (mode == RecordMode.APPEND) input + (metadata.todayWorkHour?.spendTaskTime ?: 0.0) else input
    }

    fun completion(metadata: DevOpsCommitMetadata, mode: RecordMode): String {
        val input = metadata.progress.toDouble()
        val total = if (mode == RecordMode.APPEND) {
            input + (metadata.todayWorkHour?.dayCompletion?.removeSuffix("%")?.toDoubleOrNull() ?: 0.0)
        } else input
        return "${format(total.coerceAtMost(100.0))}%"
    }

    fun content(metadata: DevOpsCommitMetadata, mode: RecordMode): String {
        val subject = metadata.subject.replace(Regex("^[•\\-*+]\\s*"), "")
        val entry = "• $subject"
        val existing = metadata.todayWorkHour?.workContent
        return if (mode == RecordMode.APPEND && !existing.isNullOrBlank()) "$existing\n$entry" else entry
    }

    private fun format(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}


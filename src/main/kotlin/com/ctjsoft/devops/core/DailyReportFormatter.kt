package com.ctjsoft.devops.core

import com.ctjsoft.devops.model.TodayWorkSummary

object DailyReportFormatter {
    fun summaryText(summary: TodayWorkSummary): String = buildString {
        appendLine(summary.totalHoursText)
        for ((label, node) in planNodes(summary)) {
            appendLine("$label：")
            val groups = node?.mapList("children").orEmpty()
            if (groups.isEmpty()) appendLine("  无")
            groups.forEach { group ->
                appendLine("  【${group.string("text")}】")
                group.mapList("children").forEach { item ->
                    appendLine("    ${item.string("taskNo")} ${item.string("text")}，${item.number("spendTaskTime")}h，${item.string("completion").ifBlank { "0%" }}")
                    item.mapList("children").forEach { detail -> appendLine("      ${detail.string("text")}") }
                }
            }
        }
    }.trim()

    fun nowWorkHtml(summary: TodayWorkSummary): String = buildString {
        append("<p style=\"font-size:14px;font-weight:bold\">${escape(summary.totalHoursText)}</p>")
        for ((label, node) in planNodes(summary)) {
            append("<p style=\"font-size:14px;font-weight:bold\">${escape(label)}：</p>")
            val groups = node?.mapList("children").orEmpty()
            if (groups.isEmpty()) {
                append("<p>无</p>")
                continue
            }
            groups.forEach { group ->
                append("<p style=\"font-size:14px\">【${escape(group.string("text"))}】</p>")
                group.mapList("children").forEachIndexed { index, item ->
                    val taskId = urlEncode(item.string("taskId"))
                    val href = if (taskId.isBlank()) "#" else "/devops-web4/linkIframe/HNm7jHP?detailId=$taskId"
                    append("<p style=\"text-indent:8px;font-size:14px\">${index + 1}、")
                    append("<a href=\"$href\" rel=\"noopener noreferrer\" target=\"_blank\">${escape(item.string("taskNo"))}</a>")
                    append("  ${escape(item.string("text"))}，${escape(item.string("completion").ifBlank { "0%" })}，${item.number("spendTaskTime")}</p>")
                    item.mapList("children").forEach { detail ->
                        append("<p style=\"text-indent:16px;font-size:14px;color:#666\">${escape(detail.string("text"))}</p>")
                    }
                }
            }
        }
    }

    fun paragraphOrBlank(text: String): String = if (text.isBlank()) "<p><br></p>" else "<p>${escape(text.trim())}</p>"

    private fun planNodes(summary: TodayWorkSummary): List<Pair<String, Map<String, Any?>?>> {
        val nodes = summary.rawTree.mapNotNull { it as? Map<*, *> }.associateBy { it["id"]?.toString() }
        @Suppress("UNCHECKED_CAST")
        return listOf(
            "计划内" to nodes["planIn"] as? Map<String, Any?>,
            "计划外" to nodes["planOut"] as? Map<String, Any?>,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.mapList(key: String): List<Map<String, Any?>> =
        (this[key] as? List<*>)?.mapNotNull { it as? Map<String, Any?> }.orEmpty()

    private fun Map<String, Any?>.string(key: String): String = this[key]?.toString().orEmpty()
    private fun Map<String, Any?>.number(key: String): String {
        val value = this[key] as? Number ?: return "0"
        val number = value.toDouble()
        return if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()
    }

    private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)
}


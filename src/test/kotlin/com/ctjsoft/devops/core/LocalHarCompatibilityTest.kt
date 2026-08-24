package com.ctjsoft.devops.core

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Optional local-only regression. The HAR path is supplied via an environment variable and is never packaged. */
class LocalHarCompatibilityTest {
    @Test
    fun `all task-list IIFE responses in local HAR are accepted`() {
        val path = System.getenv("DEVOPS_HAR_PATH")?.takeIf { it.isNotBlank() } ?: return
        val har = JsonParser.parseString(Files.readString(Path.of(path))).asJsonObject
        val entries = har["log"].asJsonObject["entries"].asJsonArray
        val payloads = entries.mapNotNull { entry ->
            val text = entry.asJsonObject["response"].asJsonObject["content"].asJsonObject["text"]?.asString
            text?.takeIf { it.contains("(function()") }
        }

        assertEquals(3, payloads.size)
        payloads.forEach { payload ->
            val parsed = DevOpsPayloadParser(maxResponseChars = 10 * 1024 * 1024).parse(payload, allowLegacyIife = true)
            assertTrue(parsed.asJsonObject["data"].isJsonArray)
        }
    }
}

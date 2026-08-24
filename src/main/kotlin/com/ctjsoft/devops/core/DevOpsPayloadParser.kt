package com.ctjsoft.devops.core

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser

/**
 * Parses normal JSON and the compressed IIFE shape returned by the task-list endpoint.
 * The legacy path is deliberately not a JavaScript engine: it accepts only constants,
 * row arrays, and a dd(row) field-index mapping.
 */
class DevOpsPayloadParser(
    private val maxResponseChars: Int = 5 * 1024 * 1024,
) {
    fun parse(text: String, allowLegacyIife: Boolean = false): JsonElement {
        val payload = text.trim().removePrefix("\uFEFF")
        if (payload.length > maxResponseChars) {
            throw DevOpsException("DevOps 响应超过允许大小。", ErrorKind.PARSE)
        }
        if (payload.isEmpty()) return JsonParser.parseString("null")

        runCatching { return JsonParser.parseString(payload) }
        if (!allowLegacyIife || !payload.contains("(function()")) {
            throw DevOpsException("DevOps 返回了无法解析的响应。", ErrorKind.PARSE)
        }

        return runCatching { JsonParser.parseString(expandLegacyIife(payload)) }
            .getOrElse { error ->
                throw DevOpsException("DevOps IIFE 响应结构不受支持。", ErrorKind.PARSE, cause = error)
            }
    }

    private fun expandLegacyIife(payload: String): String {
        val marker = "(function(){"
        val start = payload.indexOf(marker)
        require(start >= 0)
        val end = findIifeEnd(payload, start + marker.length)
        val body = payload.substring(start + marker.length, end.bodyEnd)

        require(!body.contains("eval("))
        require(!body.contains("Function("))
        require(!body.contains("Java"))
        require(!body.contains("Packages"))

        val dataAssignment = body.indexOf(",data=")
        require(body.startsWith("var ") && dataAssignment > 4)
        val variablesText = body.substring(4, dataAssignment)
        val dataStart = dataAssignment + ",data=".length
        val dataEnd = body.indexOf(",rs=[]", dataStart)
        require(dataEnd > dataStart)

        val values = linkedMapOf<String, Any?>()
        splitTopLevel(variablesText).forEach { declaration ->
            val equals = declaration.indexOf('=')
            require(equals > 0)
            val name = declaration.substring(0, equals).trim()
            require(IDENTIFIER.matches(name))
            values[name] = JsSubsetReader(declaration.substring(equals + 1), values).readValue()
        }

        val rowsValue = JsSubsetReader(body.substring(dataStart, dataEnd), values).readValue()
        val rows = rowsValue as? List<*> ?: error("data is not an array")
        val mapping = parseFieldMapping(body)
        require(mapping.isNotEmpty())

        val objects = rows.map { rawRow ->
            val row = rawRow as? List<*> ?: error("row is not an array")
            linkedMapOf<String, Any?>().also { output ->
                mapping.forEach { (field, index) -> output[field] = row.getOrNull(index) }
            }
        }

        val replacement = Gson().toJson(objects)
        return payload.substring(0, start) + replacement + payload.substring(end.expressionEnd)
    }

    private fun parseFieldMapping(body: String): List<Pair<String, Int>> {
        val functionStart = body.indexOf("function dd(d){return ")
        require(functionStart >= 0)
        val objectStart = body.indexOf('{', functionStart + "function dd(d){return ".length)
        val objectEnd = findMatching(body, objectStart, '{', '}')
        val objectBody = body.substring(objectStart + 1, objectEnd)
        return FIELD_MAPPING.findAll(objectBody).map { match ->
            decodeQuoted(match.groupValues[1]) to match.groupValues[2].toInt()
        }.toList()
    }

    private fun findIifeEnd(text: String, bodyStart: Int): IifeEnd {
        var depth = 1
        var i = bodyStart
        var quote: Char? = null
        var escaped = false
        while (i < text.length) {
            val c = text[i]
            if (quote != null) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == quote) quote = null
            } else when (c) {
                '\'', '"' -> quote = c
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        val suffix = text.substring(i + 1).trimStart()
                        val consumedWhitespace = text.substring(i + 1).length - suffix.length
                        val callLength = when {
                            suffix.startsWith(")()") -> 3
                            suffix.startsWith("())") -> 3
                            else -> error("IIFE call suffix not supported")
                        }
                        return IifeEnd(i, i + 1 + consumedWhitespace + callLength)
                    }
                }
            }
            i++
        }
        error("unterminated IIFE")
    }

    private fun splitTopLevel(text: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var arrayDepth = 0
        var objectDepth = 0
        var quote: Char? = null
        var escaped = false
        text.forEachIndexed { index, c ->
            if (quote != null) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == quote) quote = null
            } else when (c) {
                '\'', '"' -> quote = c
                '[' -> arrayDepth++
                ']' -> arrayDepth--
                '{' -> objectDepth++
                '}' -> objectDepth--
                ',' -> if (arrayDepth == 0 && objectDepth == 0) {
                    parts += text.substring(start, index)
                    start = index + 1
                }
            }
        }
        parts += text.substring(start)
        return parts.filter { it.isNotBlank() }
    }

    private fun findMatching(text: String, start: Int, open: Char, close: Char): Int {
        require(start >= 0 && text[start] == open)
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (quote != null) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == quote) quote = null
            } else when (c) {
                '\'', '"' -> quote = c
                open -> depth++
                close -> if (--depth == 0) return i
            }
        }
        error("unmatched delimiter")
    }

    private fun decodeQuoted(token: String): String =
        Gson().fromJson(if (token.startsWith('\'')) "\"${token.substring(1, token.length - 1)}\"" else token, String::class.java)

    private data class IifeEnd(val bodyEnd: Int, val expressionEnd: Int)

    private class JsSubsetReader(
        private val source: String,
        private val variables: Map<String, Any?>,
    ) {
        private var position = 0

        fun readValue(): Any? {
            skipWhitespace()
            val value = when (peek()) {
                '[' -> readArray()
                '"', '\'' -> readString()
                '-', in '0'..'9' -> readNumber()
                else -> readIdentifierValue()
            }
            skipWhitespace()
            require(position == source.length) { "unexpected trailing input" }
            return value
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            var expectingValue = true
            while (true) {
                skipWhitespace()
                when (peek()) {
                    ']' -> {
                        position++
                        return result
                    }
                    ',' -> {
                        if (expectingValue) result += null
                        position++
                        expectingValue = true
                    }
                    else -> {
                        result += readNestedValue()
                        expectingValue = false
                        skipWhitespace()
                        if (peek() == ',') {
                            position++
                            expectingValue = true
                        } else require(peek() == ']')
                    }
                }
            }
        }

        private fun readNestedValue(): Any? {
            skipWhitespace()
            return when (peek()) {
                '[' -> readArray()
                '"', '\'' -> readString()
                '-', in '0'..'9' -> readNumber()
                else -> readIdentifierValue()
            }
        }

        private fun readString(): String {
            val quote = source[position++]
            val raw = StringBuilder()
            var escaped = false
            while (position < source.length) {
                val c = source[position++]
                if (escaped) {
                    raw.append('\\').append(c)
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == quote) {
                    val json = "\"${raw.toString().replace("\"", "\\\"")}\""
                    return Gson().fromJson(json, String::class.java)
                } else raw.append(c)
            }
            error("unterminated string")
        }

        private fun readNumber(): Number {
            val start = position
            if (peek() == '-') position++
            while (peek()?.isDigit() == true) position++
            if (peek() == '.') {
                position++
                while (peek()?.isDigit() == true) position++
            }
            val value = source.substring(start, position)
            return if (value.contains('.')) value.toDouble() else value.toLong()
        }

        private fun readIdentifierValue(): Any? {
            val start = position
            while (peek()?.let { it.isLetterOrDigit() || it == '_' || it == '$' } == true) position++
            require(position > start)
            return when (val name = source.substring(start, position)) {
                "null" -> null
                "true" -> true
                "false" -> false
                else -> {
                    require(variables.containsKey(name)) { "unknown identifier" }
                    variables[name]
                }
            }
        }

        private fun skipWhitespace() {
            while (peek()?.isWhitespace() == true) position++
        }

        private fun expect(c: Char) {
            require(peek() == c)
            position++
        }

        private fun peek(): Char? = source.getOrNull(position)
    }

    private companion object {
        val IDENTIFIER = Regex("[A-Za-z_$][A-Za-z0-9_$]*")
        val FIELD_MAPPING = Regex("((?:\\\"(?:\\\\.|[^\\\"\\\\])*\\\")|(?:'(?:\\\\.|[^'\\\\])*'))\\s*:\\s*d\\[(\\d+)]")
    }
}


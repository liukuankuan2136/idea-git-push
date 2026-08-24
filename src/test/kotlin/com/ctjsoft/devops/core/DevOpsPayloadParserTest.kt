package com.ctjsoft.devops.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DevOpsPayloadParserTest {
    private val parser = DevOpsPayloadParser()

    @Test
    fun `parses standard JSON and BOM`() {
        val result = parser.parse("\uFEFF {\"status_code\":\"0000\",\"data\":[1]}")
        assertEquals("0000", result.asJsonObject["status_code"].asString)
    }

    @Test
    fun `expands the restricted task-list IIFE shape`() {
        val input = """
            {"status_code":"0000","reason":"ok","data":(function(){var n=null,code="TASK-1",title="Synthetic task",done=false,data=[[code,title,8,done,n]],rs=[];function dd(d){return {"taskNo":d[0],"taskName":d[1],"planTaskTime":d[2],"done":d[3],"optional":d[4]};}for(var i=0;i<data.length;i++){rs.push(dd(data[i]));}return rs;})()}
        """.trimIndent()

        val result = parser.parse(input, allowLegacyIife = true)
        val task = result.asJsonObject["data"].asJsonArray[0].asJsonObject
        assertEquals("TASK-1", task["taskNo"].asString)
        assertEquals("Synthetic task", task["taskName"].asString)
        assertEquals(8, task["planTaskTime"].asInt)
    }

    @Test
    fun `does not execute unknown script structures`() {
        val input = """{"data":(function(){eval("danger");return [];})()}"""
        assertFailsWith<DevOpsException> { parser.parse(input, allowLegacyIife = true) }
    }

    @Test
    fun `does not enable legacy parsing implicitly`() {
        val input = """{"data":(function(){return [];})()}"""
        assertFailsWith<DevOpsException> { parser.parse(input) }
    }
}


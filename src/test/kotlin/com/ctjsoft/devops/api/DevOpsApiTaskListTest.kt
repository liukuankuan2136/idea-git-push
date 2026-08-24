package com.ctjsoft.devops.api

import com.ctjsoft.devops.model.DevOpsTaskType
import com.google.gson.JsonParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevOpsApiTaskListTest {
    @Test
    fun `ordinary task list is fixed to group 6 and executeUser`() {
        val requests = mutableListOf<TransportRequest>()
        val responses = ArrayDeque(
            listOf(
                response("{\"data\":{\"userId\":\"7001\"}}", mapOf("Set-Cookie" to listOf("SESSION=test; Path=/"))),
                response("{\"status_code\":\"0000\",\"data\":[{\"groupFieldValue\":\"executeUser7001${'$'}user-id\",\"groupTaskCount\":1}]}"),
                response("{\"status_code\":\"0000\",\"data\":[{\"taskNo\":\"TASK-1\",\"taskName\":\"Synthetic task\",\"taskId\":\"id-1\",\"prodId\":\"prod-1\"}]}"),
            ),
        )
        val transport = DevOpsTransport { request ->
            requests += request
            responses.removeFirst()
        }
        val api = DevOpsApi(DevOpsCredentials("user", "cipher"), transport = transport)

        val tasks = api.fetchTasks(DevOpsTaskType.TASK)

        assertEquals(1, tasks.size)
        assertEquals("TASK-1", tasks.single().code)
        assertEquals(3, requests.size)
        val step1 = JsonParser.parseString(requests[1].body!!.toString(Charsets.UTF_8)).asJsonObject
        val step2 = JsonParser.parseString(requests[2].body!!.toString(Charsets.UTF_8)).asJsonObject
        assertEquals("6", step1["groupId"].asString)
        assertFalse(step1.has("groupField"))
        assertEquals("6", step2["groupId"].asString)
        assertEquals("executeUser", step2["groupField"].asString)
        assertTrue(step2["simpleFieldCondition"].asJsonObject["executeUser"].asJsonArray.any { it.asString == "7001" })
    }

    @Test
    fun `login form and session secrets are not copied into task query body`() {
        val requests = mutableListOf<TransportRequest>()
        val responses = ArrayDeque(
            listOf(
                response("{\"data\":{\"userId\":\"7001\"}}", mapOf("set-cookie" to listOf("A=1; HttpOnly", "B=2; Secure"))),
                response("{\"data\":[]}"),
            ),
        )
        val transport = DevOpsTransport { request -> requests += request; responses.removeFirst() }
        DevOpsApi(DevOpsCredentials("secret-user", "secret-cipher"), transport = transport).fetchTasks(DevOpsTaskType.BUG)

        val loginBody = requests[0].body!!.toString(Charsets.UTF_8)
        assertTrue(loginBody.contains("secret-user"))
        assertTrue(loginBody.contains("secret-cipher"))
        val queryBody = requests[1].body!!.toString(Charsets.UTF_8)
        assertFalse(queryBody.contains("secret-user"))
        assertFalse(queryBody.contains("secret-cipher"))
        assertTrue(requests[1].headers.getValue("cookie").contains("A=1"))
        assertTrue(requests[1].headers.getValue("cookie").contains("B=2"))
    }

    private fun response(body: String, headers: Map<String, List<String>> = emptyMap()) =
        TransportResponse(200, headers, body)
}

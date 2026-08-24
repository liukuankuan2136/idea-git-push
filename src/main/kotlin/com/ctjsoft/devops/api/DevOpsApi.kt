package com.ctjsoft.devops.api

import com.ctjsoft.devops.core.DevOpsException
import com.ctjsoft.devops.core.DevOpsPayloadParser
import com.ctjsoft.devops.core.ErrorKind
import com.ctjsoft.devops.model.DevOpsTask
import com.ctjsoft.devops.model.DevOpsTaskType
import com.ctjsoft.devops.model.CreateTaskInput
import com.ctjsoft.devops.model.CreateTaskResult
import com.ctjsoft.devops.model.DailyReportInput
import com.ctjsoft.devops.model.DevProject
import com.ctjsoft.devops.model.DevOpsProject
import com.ctjsoft.devops.model.DictValue
import com.ctjsoft.devops.model.ExecuteUser
import com.ctjsoft.devops.model.OpsProject
import com.ctjsoft.devops.model.Product
import com.ctjsoft.devops.model.ProductModule
import com.ctjsoft.devops.model.ProductVersion
import com.ctjsoft.devops.model.Region
import com.ctjsoft.devops.model.TodayWorkSummary
import com.ctjsoft.devops.model.WorkHourRecord
import com.ctjsoft.devops.model.WorkHourType
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Year
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class DevOpsCredentials(
    val username: String,
    /** The encrypted password field captured from the DevOps web login request. */
    val encryptedPassword: String,
)

class DevOpsApi(
    private val credentials: DevOpsCredentials,
    private val timeoutMillis: Long = 10_000,
    private val transport: DevOpsTransport = JavaHttpDevOpsTransport(),
    private val parser: DevOpsPayloadParser = DevOpsPayloadParser(),
    private val logger: (String) -> Unit = {},
) {
    private var session: Session? = null

    fun testConnection(): Boolean {
        getSession()
        return true
    }

    fun clearSession() {
        session = null
    }

    fun fetchTasks(type: DevOpsTaskType): List<DevOpsTask> {
        val activeSession = getSession()
        val baseCondition = JsonObject().apply {
            addProperty("topMenuId", TOP_MENU_ID)
            addProperty("pageId", TASK_PAGE_ID)
            addProperty("currentUser", activeSession.userId)
            addProperty("currentProductId", "undefined")
            addProperty("configFlag", type.configFlag)
            add("tasktypeId", JsonArray().also { it.add(type.taskTypeId) })
            add("executeUser", JsonArray().also { it.add(activeSession.userId) })
            addProperty("progressStatus", "incomplete")
            addProperty("taskTypeQueryRule", "0")
        }

        val step1Body = JsonObject().apply {
            addProperty("current", "1")
            addProperty("size", "50")
            add("simpleFieldCondition", baseCondition.deepCopy())
            addProperty("groupId", NORMAL_TASK_GROUP_ID)
        }
        logger("[fetchTasks] step1 type=${type.name} groupId=$NORMAL_TASK_GROUP_ID")
        val groups = dataArray(postJson(TASK_LIST_PATH, step1Body, activeSession, allowLegacyIife = true))
        val group = groups.firstOrNull { item ->
            item.takeIf(JsonElement::isJsonObject)
                ?.asJsonObject?.string("groupFieldValue")
                ?.let(EXECUTE_USER_GROUP::containsMatchIn) == true
        }?.asJsonObject ?: return emptyList()

        val groupFieldValue = group.string("groupFieldValue") ?: return emptyList()
        val groupTaskCount = group.number("groupTaskCount")?.toInt() ?: 5
        val condition = baseCondition.deepCopy().apply { addProperty("parentId", groupFieldValue) }
        val step2Body = JsonObject().apply {
            add("simpleFieldCondition", condition)
            addProperty("groupId", NORMAL_TASK_GROUP_ID)
            addProperty("groupField", NORMAL_TASK_GROUP_FIELD)
            addProperty("groupFieldValue", groupFieldValue)
            add("parentGroupInfos", JsonArray())
            addProperty("groupTaskCount", groupTaskCount)
        }
        logger("[fetchTasks] step2 type=${type.name} groupField=$NORMAL_TASK_GROUP_FIELD count=$groupTaskCount")

        return dataArray(postJson(TASK_LIST_PATH, step2Body, activeSession, allowLegacyIife = true))
            .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject?.toTask(type) }
            .filter { it.code.isNotBlank() && it.title.isNotBlank() }
    }

    fun fetchTaskByCode(code: String, type: DevOpsTaskType): DevOpsTask? {
        require(code.isNotBlank())
        val activeSession = getSession()
        val baseCondition = JsonObject().apply {
            addProperty("topMenuId", TOP_MENU_ID)
            addProperty("pageId", TASK_PAGE_ID)
            addProperty("currentUser", activeSession.userId)
            addProperty("currentProductId", "undefined")
            addProperty("configFlag", type.configFlag)
            addProperty("progressStatus", "incomplete")
            addProperty("taskTypeQueryRule", "0")
            addProperty("params", code)
        }
        val step1 = JsonObject().apply {
            addProperty("current", "1")
            addProperty("size", "50")
            add("simpleFieldCondition", baseCondition)
            addProperty("groupId", NORMAL_TASK_GROUP_ID)
        }
        val group = dataArray(postJson(TASK_LIST_PATH, step1, activeSession, allowLegacyIife = true))
            .firstOrNull { item -> item.asJsonObject.string("groupFieldValue")?.let(EXECUTE_USER_GROUP::containsMatchIn) == true }
            ?.asJsonObject ?: return null
        val groupValue = group.string("groupFieldValue") ?: return null

        val condition = JsonObject().apply {
            addProperty("topMenuId", TOP_MENU_ID)
            addProperty("pageId", TASK_PAGE_ID)
            addProperty("currentUser", activeSession.userId)
            addProperty("currentProductId", "undefined")
            addProperty("configFlag", type.configFlag)
            addProperty("parentId", groupValue)
            addProperty("taskTypeQueryRule", "0")
            addProperty(if (type == DevOpsTaskType.TASK) "taskNo" else "problemNo", code)
        }
        val step2 = JsonObject().apply {
            add("simpleFieldCondition", condition)
            addProperty("groupId", NORMAL_TASK_GROUP_ID)
            addProperty("groupField", NORMAL_TASK_GROUP_FIELD)
            addProperty("groupFieldValue", groupValue)
            add("parentGroupInfos", JsonArray())
            addProperty("groupTaskCount", 5)
        }
        return dataArray(postJson(TASK_LIST_PATH, step2, activeSession, allowLegacyIife = true))
            .firstOrNull()?.takeIf(JsonElement::isJsonObject)?.asJsonObject?.toTask(type)
    }

    fun fetchProjects(): List<DevOpsProject> {
        val activeSession = getSession()
        val response = getJson(
            "/devops-server/config/commonQuery/query/product/listByUserRight",
            mapOf("userId" to activeSession.userId, "pageId" to TASK_PAGE_ID),
            activeSession,
        )
        val results = mutableListOf<DevOpsProject>()
        collectObjects(response) { obj ->
            val code = obj.string("prodId", "productId", "prodCode", "code", "id")
            val name = obj.string("prodCname", "prodName", "productName", "name", "title") ?: code
            if (!code.isNullOrBlank() && !name.isNullOrBlank()) results += DevOpsProject(code, name)
        }
        return results.distinctBy(DevOpsProject::code)
    }

    fun fetchTasksByProduct(devProjectId: String, productId: String): List<DevOpsTask> {
        logger("[region] fetchTasksByProduct start")
        val activeSession = getSession()
        val baseCondition = JsonObject().apply {
            addProperty("topMenuId", DEV_TASK_TOP_MENU_ID)
            addProperty("pageId", DEV_TASK_PAGE_ID)
            addProperty("currentUser", activeSession.userId)
            addProperty("currentProductId", "undefined")
            addProperty("configFlag", "Task")
            addProperty("progressStatus", "")
            addProperty("taskTypeQueryRule", "0")
            add("devprojId", JsonArray().also { it.add(devProjectId) })
            add("prodId", JsonArray().also { it.add(productId) })
        }
        val step1 = JsonObject().apply {
            addProperty("current", "1")
            addProperty("size", "50")
            add("simpleFieldCondition", baseCondition.deepCopy())
            addProperty("groupId", "1")
        }
        val groups = dataArray(postJson(TASK_LIST_PATH, step1, activeSession, true, DEV_TASK_PAGE_ID))
        val today = LocalDate.now(SHANGHAI_ZONE)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val validDates = generateSequence(weekStart) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }
            .map(DateTimeFormatter.ISO_DATE::format).toSet()

        val tasks = mutableListOf<DevOpsTask>()
        groups.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }.forEach { group ->
            val name = group.string("groupName").orEmpty()
            val groupValue = group.string("groupFieldValue") ?: return@forEach
            val count = group.number("groupTaskCount")?.toInt() ?: 0
            val date = DATE_PREFIX.find(name)?.groupValues?.get(1)
            if (count <= 0 || !(name.startsWith("今天") || name.startsWith("昨天") || date in validDates)) return@forEach

            val condition = baseCondition.deepCopy().apply { addProperty("parentId", groupValue) }
            val step2 = JsonObject().apply {
                add("simpleFieldCondition", condition)
                addProperty("groupId", "1")
                addProperty("groupField", "createTime")
                addProperty("groupFieldValue", groupValue)
                add("parentGroupInfos", JsonArray())
                addProperty("groupTaskCount", count)
            }
            tasks += dataArray(postJson(TASK_LIST_PATH, step2, activeSession, true, DEV_TASK_PAGE_ID))
                .mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject?.toTask(DevOpsTaskType.TASK) }
        }
        return tasks.distinctBy(DevOpsTask::code).also {
            logger("[region] fetchTasksByProduct completed count=${it.size}")
        }
    }

    fun getUserId(): String = getSession().userId

    fun fetchWorkHours(taskId: String): List<WorkHourRecord> {
        val activeSession = getSession()
        val records = dataArray(getJson("/devops-server/config/v3/task/query/workHour/list", mapOf("taskId" to taskId), activeSession))
            .mapNotNull { element ->
                element.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let { obj ->
                    val id = obj.string("taskWorkhourId") ?: return@let null
                    WorkHourRecord(
                        taskWorkhourId = id,
                        spendTaskTime = obj.number("spendTaskTime")?.toDouble() ?: 0.0,
                        workContent = obj.string("workContent").orEmpty(),
                        taskWorkhourDate = obj.string("taskWorkhourDate").orEmpty(),
                        dayCompletion = obj.string("dayCompletion").orEmpty(),
                    )
                }
            }
        logger("[region] fetchWorkHours completed count=${records.size}")
        return records
    }

    fun fetchWorkHourTypes(): List<WorkHourType> = fetchDictValues("taskWorkhourType")
        .map { WorkHourType(it.id, it.code, it.name) }

    fun addWorkHour(
        taskId: String,
        createTime: String,
        spendTaskTime: Double,
        dayCompletion: String,
        workContent: String,
        workHourTypeCode: String,
    ) {
        val activeSession = getSession()
        val payload = JsonObject().apply {
            addProperty("createTime", createTime)
            addProperty("taskWorkhourType", workHourTypeCode)
            addProperty("spendTaskTime", spendTaskTime)
            addProperty("dayCompletion", dayCompletion)
            addProperty("workContent", workContent)
            addProperty("taskId", taskId)
            addProperty("createUser", activeSession.userId)
        }
        requireBusinessSuccess(postJson("/devops-server/config/v3/task/add/addWorkHour", payload, activeSession, false))
    }

    fun modifyWorkHour(
        taskWorkhourId: String,
        taskId: String,
        createTime: String,
        spendTaskTime: Double,
        dayCompletion: String,
        workContent: String,
        workHourTypeCode: String,
    ) {
        val activeSession = getSession()
        val payload = JsonObject().apply {
            addProperty("createTime", createTime)
            addProperty("taskWorkhourType", workHourTypeCode)
            addProperty("spendTaskTime", spendTaskTime)
            addProperty("dayCompletion", dayCompletion)
            addProperty("workContent", workContent)
            addProperty("taskId", taskId)
            addProperty("taskWorkhourId", taskWorkhourId)
            addProperty("updateUser", activeSession.userId)
        }
        requireBusinessSuccess(postJson("/devops-server/config/v3/task/modify/modifyWorkHour", payload, activeSession, false))
    }

    fun fetchDevProjects(): List<DevProject> = querySimpleList(
        "/devops-server/config/commonQuery/query/devPro/list",
        mapOf("appId" to DEV_TASK_TOP_MENU_ID, "pageId" to DEV_TASK_PAGE_ID, "userId" to getSession().userId),
        "devprojId", "devprojCname",
    ).map { DevProject(it.first, it.second) }.also {
        logger("[region] fetchDevProjects completed count=${it.size}")
    }

    fun fetchProductsByProject(devProjectId: String): List<Product> {
        logger("[region] fetchProductsByProject start")
        return querySimpleList(
            "/devops-server/config/commonQuery/query/product/list",
            mapOf("appId" to DEV_TASK_TOP_MENU_ID, "proId" to devProjectId, "pageId" to DEV_TASK_PAGE_ID, "userId" to getSession().userId),
            "prodId", "prodCname",
        ).map { Product(it.first, it.second) }.also {
            logger("[region] fetchProductsByProject completed count=${it.size}")
        }
    }

    fun fetchRegions(): List<Region> = querySimpleList(
        "/devops-server/config/commonQuery/query/region/list",
        mapOf("appId" to DEV_TASK_TOP_MENU_ID, "pageId" to DEV_TASK_PAGE_ID, "userId" to getSession().userId),
        "regionId", "regionName",
    ).map { Region(it.first, it.second) }

    fun fetchOpsProjectsByRegion(regionId: String): List<OpsProject> {
        val activeSession = getSession()
        val payload = JsonArray().also { it.add(regionId) }
        return dataArray(postJsonElement(
            "/devops-server/config/commonQuery/query/opsProByRegion/list?appId=$DEV_TASK_TOP_MENU_ID",
            payload,
            activeSession,
            DEV_TASK_PAGE_ID,
        )).mapNotNull { item -> item.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let { obj ->
            val id = obj.string("opsprojId") ?: return@let null
            val name = obj.string("opsprojCname") ?: return@let null
            OpsProject(id, name)
        } }
    }

    fun fetchExecuteUsers(productId: String? = null): List<ExecuteUser> {
        val activeSession = getSession()
        val response = getJson(
            "/devops-server/config/v3/task/query/executeUser/list",
            mapOf("type" to DEV_TASK_TOP_MENU_ID, "taskProductId" to "", "productId" to productId.orEmpty()),
            activeSession,
            DEV_TASK_PAGE_ID,
        )
        return dataArray(response).mapNotNull { item -> item.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let { obj ->
            val id = obj.string("id") ?: return@let null
            val name = obj.string("name") ?: return@let null
            ExecuteUser(id, obj.string("code").orEmpty(), name)
        } }
    }

    fun fetchProductVersions(productId: String): List<ProductVersion> = querySimpleList(
        "/devops-server/config/v3/task/query/proVersion/list",
        mapOf("productId" to productId), "id", "name",
    ).map { ProductVersion(it.first, it.second) }

    fun fetchModules(productId: String): List<ProductModule> {
        val activeSession = getSession()
        val response = postJson(
            "/devops-server/config/v3/task/query/moduleList?prodId=${urlEncode(productId)}",
            JsonObject(), activeSession, false, DEV_TASK_PAGE_ID,
        )
        return dataArray(response).mapNotNull { item -> item.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let { obj ->
            val id = obj.string("moduleId") ?: return@let null
            val name = obj.string("moduleName") ?: return@let null
            ProductModule(id, name)
        } }
    }

    fun fetchDictValues(catalogCode: String): List<DictValue> {
        val activeSession = getSession()
        val response = getJson(
            "/devops-server/run/dictValue/query/queryDictValueByCode",
            mapOf("eleCatalogCode" to catalogCode), activeSession, DEV_TASK_PAGE_ID,
        )
        return dataArray(response).mapNotNull { item -> item.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let { obj ->
            val code = obj.string("eleCode") ?: return@let null
            val name = obj.string("eleName") ?: return@let null
            DictValue(obj.string("eleId").orEmpty(), code, name)
        } }
    }

    fun createTask(input: CreateTaskInput): CreateTaskResult {
        val activeSession = getSession()
        val today = LocalDate.now(SHANGHAI_ZONE).format(DateTimeFormatter.ISO_DATE)
        val versionId = input.productVersionId ?: runCatching { fetchProductVersions(input.productId).firstOrNull()?.id }.getOrNull().orEmpty()
        val payload = JsonObject().apply {
            addProperty("workSource", input.workSource)
            addProperty("taskWorkItemCatalog", input.workItemCatalog ?: "3")
            addProperty("importance", input.importance)
            addProperty("priority", input.priority)
            addProperty("ecDate", input.expectedCompletionDate.ifBlank { today })
            addProperty("devprojId", input.devProjectId)
            addProperty("prodId", input.productId)
            addProperty("regionId", input.regionId)
            addProperty("opsprojId", input.opsProjectId)
            addProperty("executeUser", input.executeUserId)
            addProperty("planStartTime", input.planStartDate.ifBlank { today })
            addProperty("planEndTime", input.planEndDate.ifBlank { today })
            addProperty("planTaskTime", input.plannedHours)
            addProperty("executeTaskTime", "0")
            addProperty("presenter", activeSession.userId)
            addProperty("projectId", DEV_TASK_TOP_MENU_ID)
            addProperty("bugLevel", "1")
            addProperty("tasktypeId", "asbdbfkwef")
            add("planDetailIds", JsonArray())
            addProperty("isChildrenWork", false)
            addProperty("taskName", input.taskName)
            addProperty("taskRemark", input.remark.orEmpty())
            addProperty("tasktypeCode", "Task")
            addProperty("tasktypeName", "任务")
            add("attachIdList", JsonArray())
            addProperty("taskRemarkIsChange", true)
            addProperty("createUser", activeSession.userId)
            addProperty("updateUser", activeSession.userId)
            addProperty("topMenuId", DEV_TASK_TOP_MENU_ID)
            add("taskAttachIds", JsonArray())
            addProperty("moduleId", input.moduleId.orEmpty())
            addProperty("prodVersionId", versionId)
        }
        val response = postJson("/devops-server/config/v3/task/add/task", payload, activeSession, false, DEV_TASK_PAGE_ID)
        requireBusinessSuccess(response)
        val raw = response.asJsonObject["data"]
        val data = when {
            raw?.isJsonArray == true && raw.asJsonArray.size() > 0 -> raw.asJsonArray[0].asJsonObject
            raw?.isJsonObject == true -> raw.asJsonObject
            else -> JsonObject()
        }
        val code = data.string("taskNo", "code").orEmpty()
        return CreateTaskResult(code, input.taskName, data.string("taskId", "id") ?: code, data.string("url"))
    }

    fun fetchTodayWork(reportDate: String): TodayWorkSummary {
        val activeSession = getSession()
        val response = getJson(
            "/devops-server/config/devopsReportNew/query/loadTodayWork",
            mapOf("userId" to activeSession.userId, "reportDate" to reportDate), activeSession, DAILY_PAGE_ID,
        )
        val raw = dataArray(response)
        val sumText = raw.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .firstOrNull { it.string("id") == "sumTime" }?.string("text")
        val total = HOURS_PATTERN.find(sumText.orEmpty())?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val rawTree = raw.map { Gson().fromJson(it, Any::class.java) }
        return TodayWorkSummary(total, sumText ?: "当日工时合计：${formatNumber(total)}h", rawTree)
    }

    fun fetchTomorrowPlan(): String {
        val activeSession = getSession()
        return getJson(
            "/devops-server/config/devopsReportNew/query/loadTomorrowWork",
            mapOf("userId" to activeSession.userId), activeSession, DAILY_PAGE_ID,
        ).asJsonObject["data"]?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    }

    fun checkTodayWorkHourEnough(reportDate: String): String {
        val activeSession = getSession()
        return getJson(
            "/devops-server/config/devopsReportNew/query/checkTodayWorkHourEnough",
            mapOf("userId" to activeSession.userId, "reportDate" to reportDate), activeSession, DAILY_PAGE_ID,
        ).asJsonObject["data"]?.takeIf { it.isJsonPrimitive }?.asString ?: "0"
    }

    fun checkOverdueTasks(): Pair<Int, String> {
        val activeSession = getSession()
        val data = getJson(
            "/devops-server/config/devopsReportNew/query/checkOverdueTask",
            mapOf("userId" to activeSession.userId), activeSession, DAILY_PAGE_ID,
        ).asJsonObject["data"]?.takeIf(JsonElement::isJsonObject)?.asJsonObject ?: JsonObject()
        return (data.number("overdueTotal")?.toInt() ?: 0) to data.string("overdueTitle").orEmpty()
    }

    fun submitDailyReport(input: DailyReportInput) {
        val activeSession = getSession()
        val payload = JsonObject().apply {
            addProperty("nextPlan", input.nextPlanHtml)
            addProperty("nowWork", input.nowWorkHtml)
            addProperty("otherMatters", input.otherMattersHtml)
            addProperty("reportType", "1")
            add("toUserIds", JsonArray().also { arr -> input.recipientUserIds.forEach(arr::add) })
            addProperty("createUser", activeSession.userId)
            addProperty("reportDate", input.reportDate)
            add("fileIds", JsonArray())
        }
        requireBusinessSuccess(postJson("/devops-server/config/devopsReportNew/add", payload, activeSession, false, DAILY_PAGE_ID))
    }

    private fun getSession(): Session {
        session?.let { return it }
        if (credentials.username.isBlank() || credentials.encryptedPassword.isBlank()) {
            throw DevOpsException("请先配置 DevOps 账号。", ErrorKind.AUTH)
        }

        logger("[auth] login start credentialsPresent=true")

        val boundary = "----IssueLinkPush${UUID.randomUUID()}"
        val fields = linkedMapOf(
            "version" to "3.0",
            "loginType" to "password",
            "username" to credentials.username,
            "password" to credentials.encryptedPassword,
            "region" to "",
            "year" to Year.now(SHANGHAI_ZONE).value.toString(),
        )
        val body = buildMultipart(boundary, fields)
        val response = execute(
            TransportRequest(
                method = "POST",
                uri = uri(LOGIN_PATH),
                headers = mapOf(
                    "accept" to "application/json, text/plain, */*",
                    "content-type" to "multipart/form-data; boundary=$boundary",
                    "origin" to BASE_URL,
                    "user-context" to Gson().toJson(emptyUserContext()),
                ),
                body = body,
                timeoutMillis = timeoutMillis,
            ),
        )
        requireSuccess(response, authRequest = true)
        val parsed = parser.parse(response.body)
        val cookie = response.headers.entries
            .filter { it.key.equals("set-cookie", ignoreCase = true) }
            .flatMap { it.value }
            .mapNotNull { it.substringBefore(';').trim().takeIf(String::isNotBlank) }
            .joinToString("; ")
        val userId = findDeepString(parsed, "userId")
        if (cookie.isBlank() || userId.isNullOrBlank()) {
            throw DevOpsException("DevOps 登录响应缺少 Cookie 或 userId。", ErrorKind.AUTH)
        }
        return Session(cookie, userId).also {
            session = it
            logger("[auth] login completed cookiePresent=${cookie.isNotBlank()} userIdPresent=${userId.isNotBlank()}")
        }
    }

    private fun postJson(
        path: String,
        body: JsonObject,
        session: Session,
        allowLegacyIife: Boolean,
        pageId: String = TASK_PAGE_ID,
    ): JsonElement = postJsonElement(path, body, session, pageId, allowLegacyIife)

    private fun postJsonElement(
        path: String,
        body: JsonElement,
        session: Session,
        pageId: String,
        allowLegacyIife: Boolean = false,
    ): JsonElement {
        val response = execute(
            TransportRequest(
                method = "POST",
                uri = URI.create(BASE_URL + path),
                headers = sessionHeaders(session, pageId) + mapOf(
                    "content-type" to "application/json",
                    "origin" to BASE_URL,
                ),
                body = Gson().toJson(body).toByteArray(StandardCharsets.UTF_8),
                timeoutMillis = timeoutMillis,
            ),
        )
        requireSuccess(response)
        return parser.parse(response.body, allowLegacyIife)
    }

    private fun getJson(
        path: String,
        query: Map<String, String>,
        session: Session,
        pageId: String = TASK_PAGE_ID,
        allowLegacyIife: Boolean = false,
    ): JsonElement {
        val response = execute(
            TransportRequest(
                method = "GET",
                uri = uri(path, query),
                headers = sessionHeaders(session, pageId),
                timeoutMillis = timeoutMillis,
            ),
        )
        requireSuccess(response)
        return parser.parse(response.body, allowLegacyIife)
    }

    private fun querySimpleList(
        path: String,
        query: Map<String, String>,
        idField: String,
        nameField: String,
    ): List<Pair<String, String>> {
        val activeSession = getSession()
        logger("[api] list start method=GET path=$path")
        val result = dataArray(getJson(path, query, activeSession, DEV_TASK_PAGE_ID)).mapNotNull { element ->
            element.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let { obj ->
                val id = obj.string(idField) ?: return@let null
                val name = obj.string(nameField) ?: return@let null
                id to name
            }
        }
        logger("[api] list completed path=$path count=${result.size}")
        return result
    }

    private fun requireBusinessSuccess(response: JsonElement) {
        val obj = response.takeIf(JsonElement::isJsonObject)?.asJsonObject
            ?: throw DevOpsException("DevOps 返回了无效的业务响应。", ErrorKind.PARSE)
        if (obj.string("status_code") != "0000") {
            throw DevOpsException(obj.string("reason") ?: "DevOps 操作失败。", ErrorKind.NETWORK)
        }
    }

    private fun collectObjects(element: JsonElement?, collector: (JsonObject) -> Unit) {
        if (element == null || element.isJsonNull) return
        when {
            element.isJsonObject -> {
                collector(element.asJsonObject)
                element.asJsonObject.entrySet().forEach { (_, child) -> collectObjects(child, collector) }
            }
            element.isJsonArray -> element.asJsonArray.forEach { collectObjects(it, collector) }
        }
    }

    private fun execute(request: TransportRequest): TransportResponse = transport.execute(request)

    private fun requireSuccess(response: TransportResponse, authRequest: Boolean = false) {
        if (response.statusCode in 200..299) return
        val kind = if (authRequest || response.statusCode == 401 || response.statusCode == 403) ErrorKind.AUTH else ErrorKind.NETWORK
        val message = when (response.statusCode) {
            400 -> "DevOps 拒绝了请求。"
            401 -> "DevOps 认证失败，请检查账号配置。"
            403 -> "DevOps 拒绝访问，请检查账号权限。"
            404 -> "DevOps 接口不存在。"
            429 -> "DevOps 请求过于频繁，请稍后重试。"
            else -> if (response.statusCode >= 500) "DevOps 服务暂时异常。" else "DevOps 请求失败。"
        }
        throw DevOpsException(message, kind, response.statusCode)
    }

    private fun sessionHeaders(session: Session, pageId: String = TASK_PAGE_ID): Map<String, String> = mapOf(
        "accept" to "application/json",
        "cookie" to session.cookie,
        "user-context" to Gson().toJson(JsonObject().apply {
            addProperty("userId", session.userId)
            addProperty("pageId", pageId)
        }),
    )

    private fun emptyUserContext(): JsonObject = JsonObject().apply {
        listOf("userId", "userCode", "appId", "appCode", "busiYear", "tenantId", "pageId").forEach { addProperty(it, "") }
        addProperty("userName", "undefined")
    }

    private fun buildMultipart(boundary: String, fields: Map<String, String>): ByteArray = buildString {
        fields.forEach { (name, value) ->
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
            append(value).append("\r\n")
        }
        append("--").append(boundary).append("--\r\n")
    }.toByteArray(StandardCharsets.UTF_8)

    private fun dataArray(element: JsonElement): JsonArray = when {
        element.isJsonArray -> element.asJsonArray
        element.isJsonObject && element.asJsonObject["data"]?.isJsonArray == true -> element.asJsonObject["data"].asJsonArray
        else -> JsonArray()
    }

    private fun findDeepString(element: JsonElement?, key: String): String? {
        if (element == null || element is JsonNull) return null
        if (element.isJsonObject) {
            val direct = element.asJsonObject[key]
            if (direct != null && direct.isJsonPrimitive) return direct.asString.takeIf(String::isNotBlank)
            element.asJsonObject.entrySet().forEach { (_, child) -> findDeepString(child, key)?.let { return it } }
        } else if (element.isJsonArray) {
            element.asJsonArray.forEach { child -> findDeepString(child, key)?.let { return it } }
        }
        return null
    }

    private fun JsonObject.toTask(type: DevOpsTaskType): DevOpsTask {
        val code = string("taskNo", "problemNo", "taskId").orEmpty()
        val title = string("taskName", "title", "name", "taskNo") ?: code
        return DevOpsTask(
            code = code,
            title = title,
            type = type,
            status = string("implementStatus", "status", "progressStatus") ?: "incomplete",
            projectCode = string("prodId", "projectCode").orEmpty(),
            projectName = string("prodName"),
            estimatedHours = optionalNumberString("planTaskTime"),
            usedHours = optionalNumberString("devWorkload", "proWorkload", "executeTaskTime"),
            currentProgress = optionalNumberString("completion", "groupTaskSumCompletion")?.removeSuffix("%"),
            url = string("url"),
            id = string("taskId", "id") ?: code,
            regionId = string("regionId"),
            regionName = string("regionName"),
            opsProjectId = string("opsprojId"),
            opsProjectName = string("opsprojName"),
            createTime = string("createTime"),
            executeUserName = string("executeUserName"),
        )
    }

    private fun JsonObject.string(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)
    }

    private fun JsonObject.number(key: String): Number? = get(key)?.takeIf { it.isJsonPrimitive }?.asNumber

    private fun JsonObject.optionalNumberString(vararg keys: String): String? = string(*keys)?.takeIf(String::isNotBlank)

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private data class Session(val cookie: String, val userId: String)

    companion object {
        const val BASE_HOST = "devops.ctjsoft.com"
        const val BASE_URL = "https://$BASE_HOST"
        const val TASK_PAGE_ID = "h7BdNkJ"
        const val DEV_TASK_PAGE_ID = "AbY8d4R"
        const val DAILY_PAGE_ID = "wlrFlaF"
        const val TOP_MENU_ID = "OA"
        const val DEV_TASK_TOP_MENU_ID = "DevPro"
        const val NORMAL_TASK_GROUP_ID = "6"
        const val NORMAL_TASK_GROUP_FIELD = "executeUser"
        const val LOGIN_PATH = "/login"
        const val TASK_LIST_PATH = "/devops-server/config/v3/task/query/loadTaskListWithGroup"

        private val SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai")
        private val EXECUTE_USER_GROUP = Regex("^executeUser\\d{4}\\$")
        private val DATE_PREFIX = Regex("^(\\d{4}-\\d{2}-\\d{2})")
        private val HOURS_PATTERN = Regex("([\\d.]+)\\s*h", RegexOption.IGNORE_CASE)

        fun uri(path: String, query: Map<String, String> = emptyMap()): URI {
            require(path.startsWith('/'))
            val encoded = query.entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
            }
            return URI.create(BASE_URL + path + if (encoded.isBlank()) "" else "?$encoded")
        }
    }
}

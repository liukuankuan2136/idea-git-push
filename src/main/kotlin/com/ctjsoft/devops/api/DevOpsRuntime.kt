package com.ctjsoft.devops.api

import com.ctjsoft.devops.core.ExpiringCache
import com.ctjsoft.devops.settings.DevOpsCredentialStore
import com.ctjsoft.devops.settings.IssueLinkPushSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.security.MessageDigest

@Service(Service.Level.APP)
class DevOpsRuntime {
    private val log = Logger.getInstance("com.ctjsoft.devops")
    private val lookupCache = ExpiringCache()
    private var fingerprint: String? = null
    private var current: DevOpsApi? = null

    /** All plugin diagnostics go through the user-controlled setting. */
    fun diagnostic(message: String) {
        val enabled = ApplicationManager.getApplication()
            .getService(IssueLinkPushSettings::class.java).state.debugMode
        if (enabled) log.info(message)
    }

    @Synchronized
    fun api(): DevOpsApi {
        val app = ApplicationManager.getApplication()
        val credentials = app.getService(DevOpsCredentialStore::class.java).load()
            ?: error("请先在 Settings | Tools | Issue Link Push 中配置 DevOps 账号。")
        val settings = app.getService(IssueLinkPushSettings::class.java).state
        val nextFingerprint = sha256("${credentials.username}\u0000${credentials.encryptedPassword}\u0000${settings.requestTimeoutMillis}")
        if (current == null || fingerprint != nextFingerprint) {
            current?.clearSession()
            lookupCache.clear()
            current = DevOpsApi(
                credentials = credentials,
                timeoutMillis = settings.requestTimeoutMillis.toLong(),
                transport = JavaHttpDevOpsTransport(logger = ::diagnostic),
                logger = ::diagnostic,
            )
            fingerprint = nextFingerprint
        }
        return requireNotNull(current)
    }

    fun <T : Any> cached(key: String, loader: () -> T): T {
        val ttl = ApplicationManager.getApplication()
            .getService(IssueLinkPushSettings::class.java).state.cacheTtlMillis.toLong()
        return lookupCache.getOrLoad(key, ttl, loader)
    }

    @Synchronized
    fun clear() {
        current?.clearSession()
        lookupCache.clear()
        current = null
        fingerprint = null
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

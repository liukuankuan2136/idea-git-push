package com.ctjsoft.devops.core

/** Small process-local cache used for DevOps lookup lists. */
class ExpiringCache(private val nowMillis: () -> Long = System::currentTimeMillis) {
    private data class Entry(val value: Any, val expiresAt: Long)

    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun <T : Any> getOrLoad(key: String, ttlMillis: Long, loader: () -> T): T {
        if (ttlMillis <= 0) return loader()
        val now = nowMillis()
        val existing = entries[key]
        if (existing != null && existing.expiresAt > now) {
            @Suppress("UNCHECKED_CAST")
            return existing.value as T
        }
        val loaded = loader()
        entries[key] = Entry(loaded, now + ttlMillis)
        return loaded
    }

    @Synchronized
    fun clear() = entries.clear()
}

package com.ctjsoft.devops.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ExpiringCacheTest {
    @Test
    fun `reuses values until ttl expires and clear invalidates`() {
        var now = 1_000L
        var loads = 0
        val cache = ExpiringCache { now }
        fun load() = cache.getOrLoad("projects", 100) { ++loads }

        assertEquals(1, load())
        assertEquals(1, load())
        now = 1_100L
        assertEquals(2, load())
        cache.clear()
        assertEquals(3, load())
    }

    @Test
    fun `zero ttl disables caching`() {
        var loads = 0
        val cache = ExpiringCache()

        assertEquals(1, cache.getOrLoad("key", 0) { ++loads })
        assertEquals(2, cache.getOrLoad("key", 0) { ++loads })
    }
}

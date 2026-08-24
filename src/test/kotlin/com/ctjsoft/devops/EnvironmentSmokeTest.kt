package com.ctjsoft.devops

import kotlin.test.Test
import kotlin.test.assertTrue

class EnvironmentSmokeTest {
    @Test
    fun `test runtime can load Java 21 bytecode`() {
        assertTrue(Runtime.version().feature() >= 21)
    }
}

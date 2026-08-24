package com.ctjsoft.devops.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegionComplianceTest {
    @Test fun `same province locations are accepted`() {
        assertFalse(RegionCompliance.check("广西柳州功能开发", "柳州现场联调", "广西", "柳州法院").hasViolation)
    }

    @Test fun `other province location is reported`() {
        val result = RegionCompliance.check("柳州功能开发", "北京现场联调", "广西", "柳州法院")
        assertTrue(result.hasViolation)
        assertTrue(result.regionViolations.any { it.name.startsWith("北京") })
        assertTrue(result.opsProjectViolations.any { it.name.startsWith("北京") })
    }

    @Test fun `common region rejects every concrete location`() {
        assertTrue(RegionCompliance.check("上海需求处理", "", "共同区域", "").hasViolation)
    }
}

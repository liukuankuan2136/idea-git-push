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

    @Test fun `public region is an alias of common region`() {
        val result = RegionCompliance.check("bug修改", "重庆现场支持", "公共区域", "公共项目")
        assertTrue(result.hasViolation)
        assertTrue(result.regionViolations.any { it.name == "重庆" })
    }

    @Test fun `multiple concrete locations in one task are reported even in one province`() {
        val result = RegionCompliance.check("功能开发", "柳州现场联调；南宁客户沟通", "广西", "公共项目")
        assertTrue(result.hasViolation)
        assertTrue(result.regionViolations.map { it.name }.containsAll(listOf("柳州", "南宁")))
    }

    @Test fun `project subject outside selected project is reported`() {
        val result = RegionCompliance.check("bug修改", "中棉接口支持", "公共区域", "公共项目")
        assertTrue(result.hasViolation)
        assertTrue(result.projectSubjectViolations.contains("中棉"))
    }

    @Test fun `short project subject alias matches full project name`() {
        val result = RegionCompliance.check("bug修改", "中棉接口支持", "广西", "中华棉花集团电子凭证综合服务平台技术开发服务项目")
        assertTrue(result.projectSubjectViolations.isEmpty())
    }
}

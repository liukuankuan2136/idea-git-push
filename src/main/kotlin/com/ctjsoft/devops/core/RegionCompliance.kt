package com.ctjsoft.devops.core

data class LocationMatch(val name: String, val province: String)
data class RegionCheckResult(
    val regionViolations: List<LocationMatch>,
    val opsProjectViolations: List<LocationMatch>,
) {
    val hasViolation: Boolean get() = regionViolations.isNotEmpty() || opsProjectViolations.isNotEmpty()
}

object RegionCompliance {
    private val locationMap: Map<String, String> by lazy {
        val text = requireNotNull(javaClass.getResourceAsStream("/data/locations.ts"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        LOCATION_ENTRY.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
    }
    private val sortedNames: List<String> by lazy { locationMap.keys.sortedByDescending(String::length) }

    fun extractLocations(text: String): List<LocationMatch> {
        if (text.isBlank()) return emptyList()
        val matches = mutableListOf<LocationMatch>()
        val covered = mutableSetOf<Int>()
        sortedNames.forEach { name ->
            var index = 0
            while (index < text.length) {
                index = text.indexOf(name, index)
                if (index < 0) break
                val end = index + name.length
                if ((index until end).none(covered::contains)) {
                    matches += LocationMatch(name, locationMap.getValue(name))
                    covered += index until end
                }
                index = end
            }
        }
        return matches
    }

    fun primaryLocation(opsProjectName: String): LocationMatch? =
        extractLocations(opsProjectName).maxByOrNull { it.name.length }

    fun check(taskName: String, workContents: String, regionName: String, opsProjectName: String): RegionCheckResult {
        val locations = extractLocations("$taskName $workContents")
        val regionViolations = when {
            regionName.isBlank() || locations.isEmpty() -> emptyList()
            regionName == "共同区域" -> locations
            else -> locationMap[regionName]?.let { province -> locations.filter { it.province != province } }.orEmpty()
        }
        val primary = primaryLocation(opsProjectName)
        val opsViolations = if (primary == null) emptyList() else locations.filter { location ->
            !sameLocation(location.name, primary.name) &&
                !(location.province == primary.province && locationMap[location.name] == location.name)
        }
        return RegionCheckResult(regionViolations, opsViolations)
    }

    private fun sameLocation(a: String, b: String): Boolean = a.replace(Regex("[市省]$"), "") == b.replace(Regex("[市省]$"), "")
    private val LOCATION_ENTRY = Regex("(?m)^\\s*'([^']+)'\\s*:\\s*'([^']+)'\\s*,")
}


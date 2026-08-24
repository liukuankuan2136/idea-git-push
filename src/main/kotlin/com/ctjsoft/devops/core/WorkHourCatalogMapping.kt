package com.ctjsoft.devops.core

object WorkHourCatalogMapping {
    private val mapping = mapOf(
        "24" to "3", "47" to "23", "35" to "3", "5" to "12", "13" to "3", "48" to "3",
        "3" to "2", "11" to "5", "37" to "10", "36" to "1", "38" to "4", "4" to "12",
        "39" to "1", "26" to "1", "45" to "1", "46" to "1", "42" to "1", "40" to "1",
        "41" to "1", "17" to "7", "43" to "2", "12" to "6", "18" to "7", "27" to "3",
        "28" to "12", "32" to "12", "29" to "12", "50" to "29", "31" to "13",
        "61" to "31", "62" to "31", "63" to "31", "64" to "31", "65" to "31", "66" to "31", "67" to "31",
        "71" to "32", "72" to "32", "73" to "32", "74" to "32", "75" to "32", "76" to "32",
        "81" to "33", "82" to "33", "83" to "33", "84" to "33", "85" to "2", "33" to "14",
        "34" to "15", "44" to "4", "49" to "26", "51" to "30",
    )

    fun taskCatalog(workHourTypeCode: String): String = mapping[workHourTypeCode] ?: "3"
}

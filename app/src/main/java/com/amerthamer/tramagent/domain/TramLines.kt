package com.amerthamer.tramagent.domain

/** Egy budapesti villamosvonal: emberbarát jelzés + BKK route azonosító. */
data class TramLine(val name: String, val routeId: String)

/** A budapesti villamosvonalak egy helyen (korábban DK1-ben és DK2-ben külön listában szerepelt). */
object TramLines {

    val all: List<TramLine> = listOf(
        "1" to "3010", "1A" to "3011", "2" to "3020", "2B" to "3022", "3" to "3030",
        "4" to "3040", "4-6" to "3046", "6" to "3060", "12" to "3120", "14" to "3140", "17" to "3170",
        "19" to "3190", "23" to "3230", "24" to "3240", "28" to "3280", "28A" to "3281", "28E" to "3285",
        "37" to "3370", "37A" to "3371", "37E" to "3375", "41" to "3410", "42" to "3420",
        "47" to "3470", "48" to "3480", "49" to "3490", "50" to "3500", "51" to "3510",
        "51A" to "3511", "52" to "3520", "56" to "3560", "56A" to "3561", "59" to "3590",
        "59A" to "3591", "59B" to "3592", "60" to "3600", "61" to "3610", "61B" to "3612",
        "62" to "3620", "62A" to "3621", "69" to "3690",
        // éjszakai villamosok
        "N2" to "N3020", "N18" to "N3180",
    ).map { (name, id) -> TramLine(name, "BKK_$id") }

    private val byId = all.associateBy { it.routeId.removePrefix("BKK_") }

    /** Vonaljelzés route ID-ból („BKK_3040” vagy „3040” → „4”); ismeretlennél null. */
    fun nameFor(routeId: String): String? = byId[routeId.removePrefix("BKK_")]?.name

    /** Villamos-e (a BKK-nál a villamos route ID-k 3-mal, az éjszakaiak N3-mal kezdődnek). */
    fun isTram(routeId: String): Boolean = routeId.removePrefix("BKK_").removePrefix("N").startsWith("3")
}

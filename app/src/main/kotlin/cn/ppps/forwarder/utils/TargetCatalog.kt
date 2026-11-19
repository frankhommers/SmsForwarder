package cn.ppps.forwarder.utils

/** UI availability only. Stored provider IDs and existing forwarding are never remapped. */
object TargetCatalog {
    val availableTypes = listOf(TYPE_WEBHOOK, TYPE_EMAIL, TYPE_TELEGRAM, TYPE_GOTIFY, TYPE_SMS, TYPE_BARK, TYPE_SOCKET, TYPE_URL_SCHEME)
    fun isAvailable(type: Int) = type in availableTypes
}

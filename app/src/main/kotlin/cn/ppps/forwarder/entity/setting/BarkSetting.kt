package cn.ppps.forwarder.entity.setting

import java.io.Serializable

data class BarkSetting(
    //Send Address
    var server: String = "",
    //Group Name
    val group: String = "",
    //Message Icon
    val icon: String = "",
    //Message Sound
    val sound: String = "",
    //Badge count
    val badge: String = "",
    //Message Link
    val url: String = "",
    //Level of notification
    val level: String = "active",
    //Title Template
    val title: String = "",
    //Encryption algorithm
    val transformation: String = "none",
    //Encryption Key
    val key: String = "",
    //Initialization vector
    var iv: String = "",
    //Repeat notification
    val call: String = "",
    //Auto Copy Templates
    val autoCopy: String = "",
) : Serializable

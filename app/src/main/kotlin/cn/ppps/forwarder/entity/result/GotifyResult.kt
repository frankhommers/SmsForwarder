package cn.ppps.forwarder.entity.result

data class GotifyResult(
    //Failed to return
    var errorCode: Long?,
    var error: String?,
    var errorDescription: String?,
    //Successfully returned
    var id: Long?,
    var appid: Long?,
    var title: String?,
    var message: String?,
    var priority: Long?,
    var date: String?,
)
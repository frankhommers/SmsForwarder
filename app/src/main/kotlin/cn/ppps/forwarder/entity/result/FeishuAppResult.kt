package cn.ppps.forwarder.entity.result

@Suppress("PropertyName")
data class FeishuAppResult(
    var code: Long,
    var msg: String,
    //Accessaccess_tokenBack
    var tenant_access_token: String?,
    var expire: Long?,
    //Send interface back
    var content: String?,
)
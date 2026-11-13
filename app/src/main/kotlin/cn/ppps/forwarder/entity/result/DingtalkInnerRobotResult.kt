package cn.ppps.forwarder.entity.result

data class DingtalkInnerRobotResult(
    //Accessaccess_tokenBack
    var accessToken: String?,
    var expireIn: Long?,
    //Messageid
    var processQueryKey: String?,
    //Invalid UseruseridList
    //var invalidStaffIdList: String[],
    //Rate-limiteduseridList
    //var flowControlledStaffIdList: String[],
)
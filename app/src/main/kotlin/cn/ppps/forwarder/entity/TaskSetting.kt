package cn.ppps.forwarder.entity

import cn.ppps.forwarder.utils.task.TaskUtils
import java.io.Serializable

data class TaskSetting(
    val type: Int, // TASK_CONDITION_FRAGMENT_LIST Index plus KEY_BACK_CODE_CONDITION or TASK_ACTION_FRAGMENT_LIST Index plus KEY_BACK_CODE_ACTION
    val title: String, //Title
    val description: String, //Description
    var setting: String = "", //Settings
    var position: Int = -1 //Location
) : Serializable {

    val iconId: Int
        get() = TaskUtils.getTypeImageId(type)

    val greyIconId: Int
        get() = TaskUtils.getTypeGreyImageId(type)
}

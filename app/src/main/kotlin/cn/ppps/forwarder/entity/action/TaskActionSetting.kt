package cn.ppps.forwarder.entity.action

import cn.ppps.forwarder.database.entity.Task
import java.io.Serializable

data class TaskActionSetting(
    var description: String = "", //Description
    var status: Int = 1, //Status：0-Disable；1-Enable
    var taskList: List<Task>, //Automated task list
) : Serializable

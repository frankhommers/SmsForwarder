package cn.ppps.forwarder.database.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import cn.ppps.forwarder.R
import cn.ppps.forwarder.utils.STATUS_OFF
import cn.ppps.forwarder.utils.task.TaskUtils
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
@Entity(tableName = "Task")
data class Task(
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    @ColumnInfo(name = "type", defaultValue = "1") var type: Int = 1, // Task Type：＜1000as task template，>=1000Custom Tasks
    @ColumnInfo(name = "name", defaultValue = "") val name: String = "", // Task Name
    @ColumnInfo(name = "description", defaultValue = "") val description: String = "", // Task description
    @ColumnInfo(name = "conditions", defaultValue = "") val conditions: String = "", // Trigger condition
    @ColumnInfo(name = "actions", defaultValue = "") val actions: String = "", // Execute Action
    @ColumnInfo(name = "status", defaultValue = "1") var status: Int = 1, // Task Status
    @ColumnInfo(name = "last_exec_time") var lastExecTime: Date = Date(), // Last execution time
    @ColumnInfo(name = "next_exec_time") var nextExecTime: Date = Date(), // Next execution time
) : Parcelable {

    val imageId: Int
        get() = TaskUtils.getTypeImageId(type)

    val greyImageId: Int
        get() = TaskUtils.getTypeGreyImageId(type)

    val statusImageId: Int
        get() = when (status) {
            STATUS_OFF -> R.drawable.ic_stop
            else -> R.drawable.ic_start
        }

}
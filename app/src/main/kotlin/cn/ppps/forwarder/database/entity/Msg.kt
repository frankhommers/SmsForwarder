package cn.ppps.forwarder.database.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import cn.ppps.forwarder.R
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
@Entity(
    tableName = "Msg",
    indices = [
        Index(value = ["id"], unique = true)
    ]
)
data class Msg(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") var id: Long,
    @ColumnInfo(name = "type", defaultValue = "sms") var type: String,
    @ColumnInfo(name = "from", defaultValue = "") var from: String,
    @ColumnInfo(name = "content", defaultValue = "") var content: String,
    @ColumnInfo(name = "sim_slot", defaultValue = "-1") var simSlot: Int = -1, //SIM slotid：-1=retrieval failed,0=SIM slot1,1=SIM slot2
    @ColumnInfo(name = "sim_info", defaultValue = "") var simInfo: String = "",
    @ColumnInfo(name = "sub_id", defaultValue = "0") var subId: Int = 0,
    //Call type：1.Incoming call ended 2.Outgoing call ended 3.Missed call 4.Incoming call alert 5.Incoming call answered 6.Outgoing call dialed
    @ColumnInfo(name = "call_type", defaultValue = "0") var callType: Int = 0,
    @ColumnInfo(name = "time") var time: Date = Date(),
) : Parcelable {

    val simImageId: Int
        get() {
            return when {
                type == "app" -> R.drawable.ic_app
                simSlot == 0 -> R.drawable.ic_sim1
                simSlot == 1 -> R.drawable.ic_sim2
                simInfo.isNotEmpty() && simInfo.replace("-", "").startsWith("SIM2") -> R.drawable.ic_sim2
                simInfo.isNotEmpty() && simInfo.replace("-", "").startsWith("SIM1") -> R.drawable.ic_sim1
                else -> R.drawable.ic_sim
            }
        }

}
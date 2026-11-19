package cn.ppps.forwarder.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import cn.ppps.forwarder.App
import cn.ppps.forwarder.R
import cn.ppps.forwarder.adapter.TaskPagingAdapter.MyViewHolder
import cn.ppps.forwarder.database.entity.Task
import cn.ppps.forwarder.databinding.AdapterTasksCardViewListItemBinding
import cn.ppps.forwarder.entity.TaskSetting
import cn.ppps.forwarder.entity.condition.LocationConditionRules
import com.xuexiang.xutil.data.DateUtils

@Suppress("EmptyMethod")
class TaskPagingAdapter(private val itemClickListener: OnItemClickListener) : PagingDataAdapter<Task, MyViewHolder>(diffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val binding = AdapterTasksCardViewListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val item = getItem(position)
        if (item != null) {
            // Task Type：＜1000as task template，>=1000Custom Tasks
            if (item.type >= 1000) {
                holder.binding.layoutImage.visibility = View.GONE

                holder.binding.tvTime.text = DateUtils.getFriendlyTimeSpanByNow(item.lastExecTime.time)

                //IterateconditionsShow Icon
                holder.binding.layoutConditionsIcons.removeAllViews()
                if (item.conditions.isNotEmpty()) {
                    val conditionList = Gson().fromJson(item.conditions, Array<TaskSetting>::class.java).toMutableList()
                    for (condition in conditionList) {
                        val layoutConditionItem = View.inflate(App.context, R.layout.item_setting, null) as LinearLayout
                        val ivConditionIcon = layoutConditionItem.findViewById<ImageView>(R.id.iv_setting_icon)
                        if (item.status == 0) {
                            ivConditionIcon.setImageResource(condition.greyIconId)
                        } else {
                            ivConditionIcon.setImageResource(condition.iconId)
                        }
                        holder.binding.layoutConditionsIcons.addView(layoutConditionItem)
                    }
                }

                //IterateactionsShow Icon
                holder.binding.layoutActionsIcons.removeAllViews()
                if (item.actions.isNotEmpty()) {
                    val actionList = Gson().fromJson(item.actions, Array<TaskSetting>::class.java).toMutableList()
                    for (action in actionList) {
                        val layoutActionItem = View.inflate(App.context, R.layout.item_setting, null) as LinearLayout
                        val ivActionIcon = layoutActionItem.findViewById<ImageView>(R.id.iv_setting_icon)
                        if (item.status == 0) {
                            ivActionIcon.setImageResource(action.greyIconId)
                        } else {
                            ivActionIcon.setImageResource(action.iconId)
                        }
                        holder.binding.layoutActionsIcons.addView(layoutActionItem)
                    }
                }

                holder.binding.ivEdit.setOnClickListener { view: View? ->
                    itemClickListener.onItemClicked(view, item)
                }
                holder.binding.ivDelete.setOnClickListener { view: View? ->
                    itemClickListener.onItemClicked(view, item)
                }

                if (item.status == 0) {
                    holder.binding.ivArrow.setImageResource(R.drawable.auto_task_icon_left_arrow_grey)
                    holder.binding.sbEnable.isChecked = false
                } else {
                    holder.binding.ivArrow.setImageResource(R.drawable.auto_task_icon_left_arrow)
                    holder.binding.sbEnable.isChecked = true
                }
                holder.binding.sbEnable.setOnClickListener { view: View? ->
                    itemClickListener.onItemClicked(view, item)
                }
                //Do not use setOnCheckedChangeListener，Otherwise switching can produce inconsistent state
                /*holder.binding.sbEnable.setOnCheckedChangeListener { view: View, isChecked ->
                    item.status = if (isChecked) 1 else 0
                    itemClickListener.onItemClicked(view, item)
                }*/
            } else {
                holder.binding.layoutImage.visibility = View.VISIBLE
                holder.binding.layoutIcons.visibility = View.GONE
                if (item.status == 0) {
                    holder.binding.ivArrow.setImageResource(R.drawable.auto_task_icon_left_arrow_grey)
                    holder.binding.ivImage.setImageResource(item.greyImageId)
                } else {
                    holder.binding.ivArrow.setImageResource(R.drawable.auto_task_icon_left_arrow)
                    holder.binding.ivImage.setImageResource(item.imageId)
                }
                holder.binding.ivStatus.setImageResource(item.statusImageId)
                holder.binding.ivEdit.visibility = View.GONE
                holder.binding.ivDelete.visibility = View.GONE
                holder.binding.sbEnable.visibility = View.GONE
            }
            holder.binding.tvName.text = item.name
            // Display-only marker when a task still has an unsupported address-keyword location condition.
            val legacyLocation = item.type >= 1000 && item.conditions.isNotEmpty() && try {
                Gson().fromJson(item.conditions, Array<TaskSetting>::class.java).any { LocationConditionRules.isLegacyAddressCondition(it.type, it.setting) }
            } catch (e: Exception) { false }
            holder.binding.tvDescription.text = if (legacyLocation) {
                holder.binding.root.context.getString(R.string.location_validation_legacy_row, item.description)
            } else item.description
            holder.binding.ivCopy.setOnClickListener { view: View? ->
                itemClickListener.onItemClicked(view, item)
            }
        }
    }

    class MyViewHolder(val binding: AdapterTasksCardViewListItemBinding) : RecyclerView.ViewHolder(binding.root)
    interface OnItemClickListener {
        fun onItemClicked(view: View?, item: Task)
        fun onItemRemove(view: View?, id: Int)
    }

    companion object {
        var diffCallback: DiffUtil.ItemCallback<Task> = object : DiffUtil.ItemCallback<Task>() {
            override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
                return oldItem.id == newItem.id
            }

            @SuppressLint("DiffUtilEquals")
            override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
                return oldItem === newItem
            }
        }
    }
}
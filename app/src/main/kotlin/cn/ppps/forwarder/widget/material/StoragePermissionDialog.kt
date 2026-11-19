package cn.ppps.forwarder.widget.material

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import cn.ppps.forwarder.R

/** Scroll the explanation, reserving measured space for both actions. */
object StoragePermissionDialog {
 fun show(context: Context, message: Int, proceed: () -> Unit) {
  val builder=MaterialAlertDialogBuilder(context)
  val themed=builder.context
  fun dp(n:Int)=(n*context.resources.displayMetrics.density).toInt()
  val body=object:LinearLayout(themed) {
   override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
    val desired=(resources.configuration.screenHeightDp*resources.displayMetrics.density*0.70f).toInt()
    val available=View.MeasureSpec.getSize(heightMeasureSpec)
    val height=when(View.MeasureSpec.getMode(heightMeasureSpec)) {
     View.MeasureSpec.EXACTLY -> available
     View.MeasureSpec.AT_MOST -> minOf(desired,available)
     else -> desired
    }
    super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
   }
  }.apply {orientation=LinearLayout.VERTICAL;setPadding(dp(24),0,dp(24),dp(8))}
  val text=MaterialTextView(themed).apply {setText(message);setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)}
  val content=LinearLayout(themed).apply {orientation=LinearLayout.VERTICAL}
  val title=MaterialTextView(themed).apply {setText(R.string.infra_form_storage_title);setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);setPadding(0,dp(12),0,dp(12))}
  content.addView(title);content.addView(text)
  body.addView(NestedScrollView(themed).apply{addView(content)},LinearLayout.LayoutParams(-1,0,1f))
  fun button(label:Int)=MaterialButton(themed,null,com.google.android.material.R.attr.borderlessButtonStyle).apply{setText(label);minHeight=dp(48);maxLines=2}
  val accept=button(R.string.infra_form_storage_continue)
  val cancel=button(R.string.cancel)
  body.addView(accept,LinearLayout.LayoutParams(-1,-2));body.addView(cancel,LinearLayout.LayoutParams(-1,-2))
  val dialog=builder.setView(body).create()
  accept.setOnClickListener{dialog.dismiss();proceed()}
  cancel.setOnClickListener{dialog.dismiss()}
  dialog.show()
 }
}

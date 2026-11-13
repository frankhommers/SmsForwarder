package cn.ppps.forwarder.entity

import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable.Creator
import cn.ppps.forwarder.R
import com.xuexiang.xui.widget.imageview.preview.enitity.IPreviewInfo
import com.xuexiang.xutil.resource.ResUtils.getString

/**
 * Image preview entity
 *
 * @author xuexiang
 * @since 2018/12/7 PM5:34
 */
@Suppress("unused", "DEPRECATION")
data class ImageInfo(
    //Photo Address
    var mUrl: String,
    //Record coordinates
    var mBounds: Rect? = null,
    var mVideoUrl: String? = null,
    var description: String? = getString(R.string.description),
) : IPreviewInfo {

    constructor(url: String) : this(mUrl = url)

    constructor(url: String, bounds: Rect?) : this(mUrl = url, mBounds = bounds)

    constructor(videoUrl: String?, url: String) : this(mUrl = url, mVideoUrl = videoUrl)

    override fun getUrl(): String { //Return your photo address field
        return mUrl
    }

    fun setUrl(url: String) {
        mUrl = url
    }

    override fun getBounds(): Rect? { //Return your picture to the coordinates field
        return mBounds
    }

    override fun getVideoUrl(): String? {
        return mVideoUrl
    }

    fun setBounds(bounds: Rect) {
        mBounds = bounds
    }

    fun setVideoUrl(videoUrl: String) {
        mVideoUrl = videoUrl
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(mUrl)
        dest.writeParcelable(mBounds, flags)
        dest.writeString(description)
        dest.writeString(mVideoUrl)
    }

    constructor(`in`: Parcel) : this(
        mUrl = `in`.readString()!!,
        mBounds = `in`.readParcelable(Rect::class.java.classLoader),
        description = `in`.readString(),
        mVideoUrl = `in`.readString()
    )

    companion object CREATOR : Creator<ImageInfo> {

        fun newInstance(url: String, bounds: Rect): List<ImageInfo> {
            return listOf(ImageInfo(url, bounds))
        }

        override fun createFromParcel(parcel: Parcel): ImageInfo {
            return ImageInfo(parcel)
        }

        override fun newArray(size: Int): Array<ImageInfo?> {
            return arrayOfNulls(size)
        }
    }
}
// Modified for English diagnostics and labels by SmsForwarder Private contributors.
package com.xuexiang.xui.widget.textview.marqueen;

import androidx.annotation.NonNull;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public class ComplexItemEntity {

    
    private String title;
    
    private String subTitle;
    
    private String time;

    public ComplexItemEntity(String title, String subTitle, String time) {
        this.title = title;
        this.subTitle = subTitle;
        this.time = time;
    }

    public String getTitle() {
        return title;
    }

    public ComplexItemEntity setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getSubTitle() {
        return subTitle;
    }

    public ComplexItemEntity setSubTitle(String subTitle) {
        this.subTitle = subTitle;
        return this;
    }

    public String getTime() {
        return time;
    }

    public ComplexItemEntity setTime(String time) {
        this.time = time;
        return this;
    }

    @NonNull
    @Override
    public String toString() {
        return "Title:" + title + ", subtitle:" + subTitle + ", time:" + time;
    }
}

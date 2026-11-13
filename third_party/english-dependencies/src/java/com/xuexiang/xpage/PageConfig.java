// Modified for English diagnostics and labels by SmsForwarder Private contributors.
package com.xuexiang.xpage;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.xuexiang.xpage.annotation.Page;
import com.xuexiang.xpage.base.XPageActivity;
import com.xuexiang.xpage.core.CoreConfig;
import com.xuexiang.xpage.logger.PageLog;
import com.xuexiang.xpage.model.PageInfo;
import com.xuexiang.xpage.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/* Upstream explanatory comments omitted; see pinned source receipt.
 * @author xuexiang
 */
public class PageConfig {

    
    private PageConfiguration mPageConfiguration;

    
    private List<PageInfo> mPages = new ArrayList<>();

    
    private String mContainActivityClassName = XPageActivity.class.getCanonicalName();

    private static PageConfig gInstance;

    public static PageConfig getInstance() {
        if (gInstance == null) {
            synchronized (PageConfig.class) {
                if (gInstance == null) {
                    gInstance = new PageConfig();
                }
            }
        }
        return gInstance;
    }

    public PageConfig setPageConfiguration(PageConfiguration pageConfiguration) {
        mPageConfiguration = pageConfiguration;
        return this;
    }

    
    public void init(Application application) {
        initPages(application);
    }

    
    public PageConfig debug(boolean isDebug) {
        PageLog.debug(isDebug);
        return this;
    }

    
    public PageConfig debug(String tag) {
        PageLog.debug(tag);
        return this;
    }

    
    private void initPages(Context context) {
        if (mPageConfiguration == null) {
            
            mPageConfiguration = new AutoPageConfiguration();
        }
        registerPageInfos(mPageConfiguration.registerPages(context));
        CoreConfig.init(context, getPages());
    }

    
    public PageConfig setContainActivityClazz(@NonNull Class<? extends XPageActivity> containActivityClazz) {
        mContainActivityClassName = containActivityClazz.getCanonicalName();
        return this;
    }

    public static String getContainActivityClassName() {
        return getInstance().mContainActivityClassName;
    }

    
    public PageConfig registerPageInfo(Class<?> clazz) {
        mPages.add(getPageInfo(clazz));
        return this;
    }

    
    public PageConfig registerPageInfos(Class... clazz) {
        for (int i = 0; i < clazz.length; i++) {
            registerPageInfo(clazz[i]);
        }
        return this;
    }

    
    private PageConfig registerPageInfos(List<PageInfo> pageInfos) {
        if (pageInfos != null && pageInfos.size() > 0) {
            mPages.clear();
            mPages.addAll(pageInfos);
        }
        return this;
    }

    public List<PageInfo> getPages() {
        return mPages;
    }

    public static PageInfo getPageInfo(Class<?> clazz) {
        Page page = getPage(clazz);
        PageInfo pageInfo = new PageInfo(TextUtils.isEmpty(page.name()) ? clazz.getSimpleName() : page.name(), clazz);
        if (!TextUtils.isEmpty(page.params()[0])) {
            pageInfo.setParams(page.params());
        }
        pageInfo.setAnim(page.anim());
        return pageInfo;
    }

    
    public static Page getPage(Class<?> clazz) {
        return Utils.checkNotNull(clazz.getAnnotation(Page.class), "Page == null; check that the page has a @Page annotation!");
    }
}

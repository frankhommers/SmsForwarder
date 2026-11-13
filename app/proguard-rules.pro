#=========================================Base obfuscation configuration=========================================##
#Specify the compression level of the code
-optimizationpasses 5
#Package name does not mix case
-dontusemixedcaseclassnames
#Do not skip non-public library classes
#-dontskipnonpubliclibraryclasses
# Specify members who do not ignore unpublic libraries
#-dontskipnonpubliclibraryclassmembers
#Optimization: do not optimize input class files
-dontoptimize
#Prevalidation
#-dontpreverify
#Log obfuscation
-verbose
# Obfuscation algorithms
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
#Keep annotations
-keepattributes *Annotation*
#Ignore Warning
-ignorewarnings

##Record generated log data,gradle build, and then output in the root directory of this item##
#apk All classes in package class Internal structure
#-dump class_files.txt
#Unobfuscated classes and members
-printseeds seeds.txt
#List from apk Removed Code
-printusage unused.txt
#Mapping before and after obfuscation
-printmapping mapping.txt
# , and keep the source name"Proguard"Strings, not original class names and keep line numbers
-keepattributes SourceFile,LineNumberTable
########Record generated log data，gradle buildtime to output in the root of this item-end#####

#Keep rules
# Keep these classes unobfuscated
-keep public class * extends android.app.Fragment
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class * extends android.support.v4.**
#-keep public class com.android.vending.licensing.ILicensingService

#If there is a referencev4The package can add the following line
#-keep public class * extends android.support.v4.app.Fragment

##########JSKeep interface classes unobfuscated so they remain callable
-dontwarn com.android.JsInterface.**
-keep class com.android.JsInterface.** {*; }

#JPush and Baidulbs android sdkUse Togetherproguard Obfuscation issues#httpObfuscating these classes causesapklocation failures; keepapache ofhttpclasses unobfuscated
-dontwarn org.apache.**
-keep class org.apache.**{ *; }

-keep public class * extends android.view.View {
  public <init>(android.content.Context);
  public <init>(android.content.Context, android.util.AttributeSet);
  public <init>(android.content.Context, android.util.AttributeSet, int);
  public void set*(...);
 }

#Keep native methods unobfuscated
-keepclasseswithmembernames class * {
  native <methods>;
}

#Keep custom view classes unobfuscated
-keepclasseswithmembers class * {
  public <init>(android.content.Context, android.util.AttributeSet);
}

#Keep custom view classes unobfuscated
-keepclassmembers class * extends android.app.Activity {
  public void *(android.view.View);
}

#Keep Parcelable unobfuscated
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

#Keep Serializable unobfuscated
-keepnames class * implements java.io.Serializable

#Keep Serializable unobfuscated andenum keep classes unobfuscated too
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    !private <fields>;
    !private <methods>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

#Keep enumeration enum classes unobfuscated; if obfuscation fails, use the above -keepclassmembers class * implements java.io.Serializableas needed
-keepclassmembers enum * {
      public static **[] values();
      public static ** valueOf(java.lang.String);
}

-keepclassmembers class * {
      public void *ButtonClicked(android.view.View);
}

#Do not obfuscate resource classes
-keep class **.R$* {*;}

#===================================Protect application code and referenced third-partyjarPackagelibrary=============================#######
#If you quote it,v4orv7Package
-dontwarn android.support.**


# AndroidX Prevent obfuscation
-dontwarn com.google.android.material.**
-dontnote com.google.android.material.**
-dontwarn androidx.**
-keep class com.google.android.material.** {*;}
-keep class androidx.** {*;}
-keep public class * extends androidx.**
-keep interface androidx.** {*;}
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# zxing
-dontwarn com.google.zxing.**
-keep class com.google.zxing.**{*;}

#SignalRSend
-keep class microsoft.aspnet.signalr.** { *; }

# JPush obfuscation
#-dontoptimize
#-dontpreverify
#-dontwarn cn.jpush.**
#-keep class cn.jpush.** { *; }
#-dontwarn cn.jiguang.**
#-keep class cn.jiguang.** { *; }

# Database frameworkOrmLite
-keepattributes *DatabaseField*
-keepattributes *DatabaseTable*
-keepattributes *SerializedName*
-keep class com.j256.**
-keepclassmembers class com.j256.** { *; }
-keep enum com.j256.**
-keepclassmembers enum com.j256.** { *; }
-keep interface com.j256.**
-keepclassmembers interface com.j256.** { *; }

#XHttp2
-keep class com.xuexiang.xhttp2.model.** { *; }
-keep class com.xuexiang.xhttp2.cache.model.** { *; }
-keep class com.xuexiang.xhttp2.cache.stategy.**{*;}
-keep class com.xuexiang.xhttp2.annotation.** { *; }

#okhttp
-dontwarn com.squareup.okhttp3.**
-keep class com.squareup.okhttp3.** { *;}
-dontwarn okio.**
-dontwarn javax.annotation.**

#If you need it,Gsonparser package: add the following rules to avoid errors
-keepattributes Signature
-keep class com.google.gson.stream.** { *; }
-keepattributes EnclosingMethod
-keep class org.xz_sale.entity.**{*;}
-keep class com.google.gson.** {*;}
-keep class com.google.**{*;}
#-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.examples.android.model.** { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions

# RxJava RxAndroid
-dontwarn sun.misc.**
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
    long producerIndex;
    long consumerIndex;
}
#-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
#    rx.internal.util.atomic.LinkedQueueNode producerNode;
#}
#-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
#    rx.internal.util.atomic.LinkedQueueNode consumerNode;
#}

-dontwarn okio.**
-dontwarn javax.annotation.**

# fastjson
-dontwarn com.alibaba.fastjson.**
-keep class com.alibaba.fastjson.** { *; }
-keepattributes Signature

# xpage
-keep class com.xuexiang.xpage.annotation.** { *; }
-keep class com.xuexiang.xpage.config.** { *; }

# xaop
-keep @com.xuexiang.xaop.annotation.* class * {*;}
-keep @org.aspectj.lang.annotation.* class * {*;}
-keep class * {
    @com.xuexiang.xaop.annotation.* <fields>;
    @org.aspectj.lang.annotation.* <fields>;
}
-keepclassmembers class * {
    @com.xuexiang.xaop.annotation.* <methods>;
    @org.aspectj.lang.annotation.* <methods>;
}

# xrouter
-keep public class com.xuexiang.xrouter.routes.**{*;}
-keep class * implements com.xuexiang.xrouter.facade.template.ISyringe{*;}
# If you use it, byType obtained using Service，The following rules need to be added to protect the interface
-keep interface * implements com.xuexiang.xrouter.facade.template.IProvider
# For single-class injection without an interface implementation IProvider，The following rule needs to be added to protect the realization
-keep class * implements com.xuexiang.xrouter.facade.template.IProvider


# xvideo
-keep class com.xuexiang.xvideo.jniinterface.** { *; }

# xipc
-keep @com.xuexiang.xipc.annotation.* class * {*;}
-keep class * {
    @com.xuexiang.xipc.annotation.* <fields>;
}
-keepclassmembers class * {
    @com.xuexiang.xipc.annotation.* <methods>;
}

-keepclassmembers class * {
   public <init> (org.json.JSONObject);
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class com.xuexiang.xui.widget.edittext.materialedittext.** { *; }

# Android Keep Alive(Android keep-alive)，Cactus Dual-process foreground service，JobScheduler，onePix(A pixel)，WorkManager，Silent music
-keep class com.gyf.cactus.entity.* {*;}

# Exclude entity classes
-keep class cn.ppps.forwarder.core.http.entity.** {*;}
-keep class cn.ppps.forwarder.database.entity.** {*;}
-keep class cn.ppps.forwarder.entity.** {*;}
-keep class cn.ppps.forwarder.server.model.** {*;}

# javax.mail
-dontwarn com.sun.**
-dontwarn javax.mail.**
-dontwarn javax.activation.**
-keep class com.sun.** { *;}
-keep class javax.mail.** { *;}
-keep class javax.activation.** { *;}
-keep class com.smailnet.emailkit.** { *;}
-keep class cn.ppps.forwarder.utils.mail.** {*;}
-keep class com.gitee.xuankaicat.kmnkt.** {*;}
-keep class org.eclipse.paho.client.** {*;}

-keep public class com.xuexiang.xrouter.routes.**{*;}
-keep class * implements com.xuexiang.xrouter.facade.template.ISyringe{*;}
# If you use it, byType obtained using Service，The following rules need to be added to protect the interface
-keep interface * implements com.xuexiang.xrouter.facade.template.IProvider
# For single-class injection without an interface implementation IProvider，The following rule needs to be added to protect the realization
-keep class * implements com.xuexiang.xrouter.facade.template.IProvider
# Ignore XRouter Warning
-dontwarn com.xuexiang.xrouter.**

-dontwarn com.alipay.sdk.**
-dontwarn com.android.org.conscrypt.**
-dontwarn java.awt.image.**
-dontwarn javax.lang.model.**
-dontwarn javax.naming.**
-dontwarn javax.naming.directory.**

# This is generated automatically by the Android Gradle plugin.
-dontwarn org.joda.convert.**
-dontwarn org.slf4j.impl.**

# MultiLanguages
-keep class com.hjq.language.** {*;}

# crontabParsing
-keep class gatewayapps.crondroid.** { *; }
-keep class net.redhogs.cronparser.** { *; }

# Ignore All R8 stack map warnings
-ignorewarnings
-dontwarn **

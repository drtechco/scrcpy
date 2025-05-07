package com.genymobile.scrcpy;

import com.genymobile.scrcpy.wrappers.ServiceManager;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.AttributionSource;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.IContentProvider;
import android.os.Binder;
import android.os.Process;

import java.util.Map;

public final class FakeContext extends ContextWrapper {
    
    public static final String SHELL_PACKAGE_NAME = "com.android.shell";
    public static final int ROOT_UID = 0; // Like android.os.Process.ROOT_UID, but before API 29
    
    //async map
    private static final Map<String, FakeContext> INSTANCE_MAP = new java.util.concurrent.ConcurrentHashMap<>();
    private final String packageName;
    private final int uid;
    
    
    public static FakeContext get() {
        return get(SHELL_PACKAGE_NAME, Process.SHELL_UID);
    }
    
    public static FakeContext get(String packageName) {
        return INSTANCE_MAP.get(packageName);
    }
    
    @SuppressLint("all")
    public static FakeContext get(String packageName, int uid) {
        return INSTANCE_MAP.getOrDefault(packageName, new FakeContext(packageName, uid));
    }
    
    
    private final ContentResolver contentResolver = new ContentResolver(this) {
        @SuppressWarnings({"unused", "ProtectedMemberInFinalClass"})
        // @Override (but super-class method not visible)
        protected IContentProvider acquireProvider(Context c, String name) {
            return ServiceManager.getActivityManager().getContentProviderExternal(name, new Binder());
        }
        
        @SuppressWarnings("unused")
        // @Override (but super-class method not visible)
        public boolean releaseProvider(IContentProvider icp) {
            return false;
        }
        
        @SuppressWarnings({"unused", "ProtectedMemberInFinalClass"})
        // @Override (but super-class method not visible)
        protected IContentProvider acquireUnstableProvider(Context c, String name) {
            return null;
        }
        
        @SuppressWarnings("unused")
        // @Override (but super-class method not visible)
        public boolean releaseUnstableProvider(IContentProvider icp) {
            return false;
        }
        
        @SuppressWarnings("unused")
        // @Override (but super-class method not visible)
        public void unstableProviderDied(IContentProvider icp) {
            // ignore
        }
    };
    
    private FakeContext(String packageName, int uid) {
        super(Workarounds.getSystemContext());
        this.packageName = packageName;
        this.uid = uid;
    }
    
    @Override
    public String getPackageName() {
        return packageName;
    }
    
    @Override
    public String getOpPackageName() {
        return packageName;
    }
    
    @TargetApi(AndroidVersions.API_31_ANDROID_12)
    @Override
    public AttributionSource getAttributionSource() {
        AttributionSource.Builder builder = new AttributionSource.Builder(uid);
        builder.setPackageName(packageName);
        return builder.build();
    }
    
    // @Override to be added on SDK upgrade for Android 14
    @SuppressWarnings("unused")
    public int getDeviceId() {
        return 0;
    }
    
    @Override
    public Context getApplicationContext() {
        return this;
    }
    
    @Override
    public ContentResolver getContentResolver() {
        return contentResolver;
    }
}

package com.genymobile.scrcpy.wrappers;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.FakeContext;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.util.ReflectUtils;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.IContentProvider;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class ActivityManager {
    
    private final IInterface manager;
    private Method getContentProviderExternalMethod;
    private boolean getContentProviderExternalMethodNewVersion = true;
    private Method removeContentProviderExternalMethod;
    private Method startActivityAsUserMethod;
    
    private Method startActivityMethod;
    private Method forceStopPackageMethod;
    
    private Method getRunningAppProcessesMethod;
    
    private Method getRunningTasksMethod;
    
    static ActivityManager create() {
        try {
            // On old Android versions, the ActivityManager is not exposed via AIDL,
            // so use ActivityManagerNative.getDefault()
            Class<?> cls = Class.forName("android.app.ActivityManagerNative");
            Method getDefaultMethod = cls.getDeclaredMethod("getDefault");
            IInterface am = (IInterface) getDefaultMethod.invoke(null);
            return new ActivityManager(am);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
    
    private ActivityManager(IInterface manager) {
        this.manager = manager;
    }
    
    private Method getGetContentProviderExternalMethod() throws NoSuchMethodException {
        if (getContentProviderExternalMethod == null) {
            try {
                getContentProviderExternalMethod = manager.getClass().getMethod("getContentProviderExternal", String.class, int.class, IBinder.class, String.class);
            } catch (NoSuchMethodException e) {
                // old version
                getContentProviderExternalMethod = manager.getClass().getMethod("getContentProviderExternal", String.class, int.class, IBinder.class);
                getContentProviderExternalMethodNewVersion = false;
            }
        }
        return getContentProviderExternalMethod;
    }
    
    private Method getRemoveContentProviderExternalMethod() throws NoSuchMethodException {
        if (removeContentProviderExternalMethod == null) {
            removeContentProviderExternalMethod = manager.getClass().getMethod("removeContentProviderExternal", String.class, IBinder.class);
        }
        return removeContentProviderExternalMethod;
    }
    
    @TargetApi(AndroidVersions.API_29_ANDROID_10)
    public IContentProvider getContentProviderExternal(String name, IBinder token) {
        try {
            Method method = getGetContentProviderExternalMethod();
            Object[] args;
            if (getContentProviderExternalMethodNewVersion) {
                // new version
                args = new Object[]{name, FakeContext.ROOT_UID, token, null};
            } else {
                // old version
                args = new Object[]{name, FakeContext.ROOT_UID, token};
            }
            // ContentProviderHolder providerHolder = getContentProviderExternal(...);
            Object providerHolder = method.invoke(manager, args);
            if (providerHolder == null) {
                return null;
            }
            // IContentProvider provider = providerHolder.provider;
            Field providerField = providerHolder.getClass().getDeclaredField("provider");
            providerField.setAccessible(true);
            return (IContentProvider) providerField.get(providerHolder);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
            return null;
        }
    }
    
    void removeContentProviderExternal(String name, IBinder token) {
        try {
            Method method = getRemoveContentProviderExternalMethod();
            method.invoke(manager, name, token);
        } catch (ReflectiveOperationException e) {
            Ln.e("Could not invoke method", e);
        }
    }
    
    public ContentProvider createSettingsProvider() {
        IBinder token = new Binder();
        IContentProvider provider = getContentProviderExternal("settings", token);
        if (provider == null) {
            return null;
        }
        return new ContentProvider(this, provider, "settings", token);
    }
    
    private Method getStartActivityAsUserMethod() throws NoSuchMethodException, ClassNotFoundException {
        if (startActivityAsUserMethod == null) {
            Class<?> iApplicationThreadClass = Class.forName("android.app.IApplicationThread");
            Class<?> profilerInfo = Class.forName("android.app.ProfilerInfo");
            startActivityAsUserMethod = manager.getClass().getMethod("startActivityAsUser", iApplicationThreadClass, String.class, Intent.class, String.class, IBinder.class, String.class, int.class, int.class, profilerInfo, Bundle.class, int.class);
        }
        return startActivityAsUserMethod;
    }
    
    public int startActivity(Intent intent) {
        return startActivity(intent, null);
    }
    
    @SuppressWarnings("ConstantConditions")
    public int startActivity(Intent intent, Bundle options) {
        return startActivity(intent, options, FakeContext.SHELL_PACKAGE_NAME);
    }
    
    public int startActivity(Intent intent, Bundle options, String callingPackage) {
        return startActivity(intent, options, callingPackage,/* UserHandle.USER_CURRENT */-2);
    }
    
    
    public int startActivity(Intent intent, Bundle options, String callingPackage, int uid) {
        try {
            Method method = getStartActivityAsUserMethod();
            return (int) method.invoke(
                    /* this */ manager,
                    /* caller */ null,
                    /* callingPackage */ callingPackage,
                    /* intent */ intent,
                    /* resolvedType */ null,
                    /* resultTo */ null,
                    /* resultWho */ null,
                    /* requestCode */ 0,
                    /* startFlags */ 0,
                    /* profilerInfo */ null,
                    /* bOptions */ options,
                    /* userId */  uid
            );
        } catch (Throwable e) {
            Ln.e("Could not invoke method", e);
            return 0;
        }
    }
    
    
    public int startActivityCurrent(Intent intent, Bundle options, String callingPackage) {
        try {
            Method method = getstartActivityCurrentMethod();
            return (int) method.invoke(
                    /* this */ manager,
                    /* caller */ null,
                    /* callingPackage */ callingPackage,
                    /* intent */ intent,
                    /* resolvedType */ null,
                    /* resultTo */ null,
                    /* resultWho */ null,
                    /* requestCode */ 0,
                    /* startFlags */ 0,
                    /* profilerInfo */ null,
                    /* bOptions */ options
            );
        } catch (Throwable e) {
            Ln.e("Could not invoke method", e);
            return 0;
        }
    }
    
    private Method getstartActivityCurrentMethod() {
        if (startActivityMethod == null) {
            try {
                Class<?> iApplicationThreadClass = Class.forName("android.app.IApplicationThread");
                Class<?> profilerInfo = Class.forName("android.app.ProfilerInfo");
                //IApplicationThread var1,
                // String var2,
                // Intent var3,
                // String var4,
                // IBinder var5,
                // String var6,
                // int var7,
                // int var8,
                // ProfilerInfo var9,
                // Bundle var10
                startActivityMethod = manager.getClass().getMethod("startActivity", iApplicationThreadClass, String.class, Intent.class, String.class, IBinder.class, String.class, int.class, int.class, profilerInfo, Bundle.class);
            } catch (Exception e) {
                Ln.e("Could not invoke method", e);
            }
        }
        return startActivityMethod;
    }
    
    
    private Method getForceStopPackageMethod() throws NoSuchMethodException {
        if (forceStopPackageMethod == null) {
            forceStopPackageMethod = manager.getClass().getMethod("forceStopPackage", String.class, int.class);
        }
        return forceStopPackageMethod;
    }
    
    public void forceStopPackage(String packageName) {
        try {
            Method method = getForceStopPackageMethod();
            method.invoke(manager, packageName, /* userId */ /* UserHandle.USER_CURRENT */ -2);
        } catch (Throwable e) {
            Ln.e("Could not invoke method", e);
        }
    }
    
    private Method getGetRunningAppProcessesMethod() throws NoSuchMethodException {
        if (getRunningAppProcessesMethod == null) {
            getRunningAppProcessesMethod = manager.getClass().getMethod("getRunningAppProcesses");
        }
        return getRunningAppProcessesMethod;
    }
    
    @SuppressWarnings("unchecked")
    public List<android.app.ActivityManager.RunningAppProcessInfo> getRunningAppProcesses() throws RemoteException {
        try {
            Method method = getGetRunningAppProcessesMethod();
            return (List<android.app.ActivityManager.RunningAppProcessInfo>) method.invoke(manager);
        } catch (Exception e) {
            Ln.e("Could not invoke getRunningAppProcesses", e);
            throw new RemoteException("Could not get running processes");
        }
    }
    
    
    private Method getGetRunningTasksMethod() throws NoSuchMethodException {
        //ReflectUtils.printAllMethods( manager.getClass());
        if (getRunningTasksMethod == null) {
            getRunningTasksMethod = manager.getClass().getMethod("getTasks", int.class);
        }
        return getRunningTasksMethod;
    }
    @SuppressWarnings("unchecked")
    public List<android.app.ActivityManager.RunningTaskInfo> getRunningTasks(int maxNum) {
        try {
            Method method = getGetRunningTasksMethod();
            return (List<android.app.ActivityManager.RunningTaskInfo>) method.invoke(manager, maxNum);
        } catch (Exception e) {
            Ln.e("Could not invoke getRunningTasks", e);
            return null;
        }
    }
    
    
}

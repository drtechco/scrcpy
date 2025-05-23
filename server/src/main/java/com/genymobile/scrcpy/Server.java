package com.genymobile.scrcpy;

import com.genymobile.scrcpy.audio.AudioCapture;
import com.genymobile.scrcpy.audio.AudioCodec;
import com.genymobile.scrcpy.audio.AudioDirectCapture;
import com.genymobile.scrcpy.audio.AudioEncoder;
import com.genymobile.scrcpy.audio.AudioPlaybackCapture;
import com.genymobile.scrcpy.audio.AudioRawRecorder;
import com.genymobile.scrcpy.audio.AudioSource;
import com.genymobile.scrcpy.control.ControlChannel;
import com.genymobile.scrcpy.control.Controller;
import com.genymobile.scrcpy.device.ConfigurationException;
import com.genymobile.scrcpy.device.DesktopConnection;
import com.genymobile.scrcpy.device.Device;
import com.genymobile.scrcpy.device.NewDisplay;
import com.genymobile.scrcpy.device.Streamer;
import com.genymobile.scrcpy.opengl.OpenGLRunner;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.util.LogUtils;
import com.genymobile.scrcpy.util.ReflectUtils;
import com.genymobile.scrcpy.util.StringUtils;
import com.genymobile.scrcpy.video.CameraCapture;
import com.genymobile.scrcpy.video.NewDisplayCapture;
import com.genymobile.scrcpy.video.ScreenCapture;
import com.genymobile.scrcpy.video.SurfaceCapture;
import com.genymobile.scrcpy.video.SurfaceEncoder;
import com.genymobile.scrcpy.video.VideoSource;
import com.genymobile.scrcpy.wrappers.ActivityManager;
import com.genymobile.scrcpy.wrappers.ServiceManager;

import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Server {
    
    public static final String SERVER_PATH;
    
    static {
        String[] classPaths = System.getProperty("java.class.path").split(File.pathSeparator);
        // By convention, scrcpy is always executed with the absolute path of scrcpy-server.jar as the first item in the classpath
        SERVER_PATH = classPaths[0];
    }
    
    private static class Completion {
        private int running;
        private boolean fatalError;
        
        Completion(int running) {
            this.running = running;
        }
        
        synchronized void addCompleted(boolean fatalError) {
            --running;
            if (fatalError) {
                this.fatalError = true;
            }
            if (running == 0 || this.fatalError) {
                notify();
            }
        }
        
        synchronized void await() {
            try {
                while (running > 0 && !fatalError) {
                    wait();
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }
    
    private Server() {
        // not instantiable
    }
    
    private static void scrcpy(Options options) throws IOException, ConfigurationException {
        if (Build.VERSION.SDK_INT < AndroidVersions.API_31_ANDROID_12 && options.getVideoSource() == VideoSource.CAMERA) {
            Ln.e("Camera mirroring is not supported before Android 12");
            throw new ConfigurationException("Camera mirroring is not supported");
        }
        
        if (Build.VERSION.SDK_INT < AndroidVersions.API_29_ANDROID_10) {
            if (options.getNewDisplay() != null) {
                Ln.e("New virtual display is not supported before Android 10");
                throw new ConfigurationException("New virtual display is not supported");
            }
            if (options.getDisplayImePolicy() != -1) {
                Ln.e("Display IME policy is not supported before Android 10");
                throw new ConfigurationException("Display IME policy is not supported");
            }
        }
        
        CleanUp cleanUp = null;
        
        if (options.getCleanup()) {
            cleanUp = CleanUp.start(options);
        }
        
        int scid = options.getScid();
        boolean tunnelForward = options.isTunnelForward();
        boolean control = options.getControl();
        boolean video = options.getVideo();
        boolean audio = options.getAudio();
        boolean sendDummyByte = options.getSendDummyByte();
        boolean stdout = options.getStdout();
        
        Workarounds.apply(FakeContext.SHELL_PACKAGE_NAME);
        
        List<AsyncProcessor> asyncProcessors = new ArrayList<>();
        
        DesktopConnection connection = DesktopConnection.open(scid, stdout, tunnelForward, video, audio, control, sendDummyByte);
        try {
            if (options.getSendDeviceMeta()) {
                connection.sendDeviceMeta(Device.getDeviceName());
            }
            
            Controller controller = null;
            
            if (control) {
                ControlChannel controlChannel = connection.getControlChannel();
                controller = new Controller(controlChannel, cleanUp, options);
                asyncProcessors.add(controller);
            }
            
            if (audio) {
                AudioCodec audioCodec = options.getAudioCodec();
                AudioSource audioSource = options.getAudioSource();
                AudioCapture audioCapture;
                if (audioSource.isDirect()) {
                    audioCapture = new AudioDirectCapture(audioSource);
                } else {
                    audioCapture = new AudioPlaybackCapture(options.getAudioDup());
                }
                
                Streamer audioStreamer = new Streamer(connection.getAudioFd(), audioCodec, options.getSendCodecMeta(), options.getSendFrameMeta());
                AsyncProcessor audioRecorder;
                if (audioCodec == AudioCodec.RAW) {
                    audioRecorder = new AudioRawRecorder(audioCapture, audioStreamer);
                } else {
                    audioRecorder = new AudioEncoder(audioCapture, audioStreamer, options);
                }
                asyncProcessors.add(audioRecorder);
            }
            
            if (video) {
                Streamer videoStreamer = new Streamer(connection.getVideoFd(), options.getVideoCodec(), options.getSendCodecMeta(), options.getSendFrameMeta());
                SurfaceCapture surfaceCapture;
                if (options.getVideoSource() == VideoSource.DISPLAY) {
                    NewDisplay newDisplay = options.getNewDisplay();
                    if (newDisplay != null) {
                        surfaceCapture = new NewDisplayCapture(controller, options);
                    } else {
                        assert options.getDisplayId() != Device.DISPLAY_ID_NONE;
                        surfaceCapture = new ScreenCapture(controller, options);
                    }
                } else {
                    surfaceCapture = new CameraCapture(options);
                }
                SurfaceEncoder surfaceEncoder = new SurfaceEncoder(surfaceCapture, videoStreamer, options);
                asyncProcessors.add(surfaceEncoder);
                
                if (controller != null) {
                    controller.setSurfaceCapture(surfaceCapture);
                }
            }
            
            Completion completion = new Completion(asyncProcessors.size());
            for (AsyncProcessor asyncProcessor : asyncProcessors) {
                asyncProcessor.start((fatalError) -> {
                    completion.addCompleted(fatalError);
                });
            }
            
            completion.await();
        } finally {
            if (cleanUp != null) {
                cleanUp.interrupt();
            }
            for (AsyncProcessor asyncProcessor : asyncProcessors) {
                asyncProcessor.stop();
            }
            
            OpenGLRunner.quit(); // quit the OpenGL thread, if any
            
            connection.shutdown();
            
            try {
                if (cleanUp != null) {
                    cleanUp.join();
                }
                for (AsyncProcessor asyncProcessor : asyncProcessors) {
                    asyncProcessor.join();
                }
                OpenGLRunner.join();
            } catch (InterruptedException e) {
                // ignore
            }
            
            connection.close();
        }
    }
    
    public static void main(String... args) {
        int status = 0;
        try {
            internalMain(args);
        } catch (Throwable t) {
            Ln.e(t.getMessage(), t);
            status = 1;
        } finally {
            // By default, the Java process exits when all non-daemon threads are terminated.
            // The Android SDK might start some non-daemon threads internally, preventing the scrcpy server to exit.
            // So force the process to exit explicitly.
            System.exit(status);
        }
    }
    
    private static void internalMain(String... args) throws Exception {
        Options options = Options.parse(args);
        Ln.disableSystemStreams();
        if (!options.getRawStream()) {
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                Ln.e("Exception on thread " + t, e);
            });
            Ln.i("Starting...");
            Ln.i("Options: " + options);
            Ln.i("Device: [" + Build.MANUFACTURER + "] " + Build.BRAND + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");
            Ln.initLogLevel(options.getLogLevel());
        }
        
        if (options.getList()) {
            if (options.getCleanup()) {
                CleanUp.unlinkSelf();
            }
            
            if (options.getListEncoders()) {
                Ln.i(LogUtils.buildVideoEncoderListMessage());
                Ln.i(LogUtils.buildAudioEncoderListMessage());
            }
            if (options.getListDisplays()) {
                Ln.i(LogUtils.buildDisplayListMessage());
            }
            if (options.getListCameras() || options.getListCameraSizes()) {
                Workarounds.apply(FakeContext.SHELL_PACKAGE_NAME);
                Ln.i(LogUtils.buildCameraListMessage(options.getListCameraSizes()));
            }
            if (options.getListApps()) {
                Workarounds.apply(FakeContext.SHELL_PACKAGE_NAME);
                Ln.i("Processing Android apps... (this may take some time)");
                Ln.i(LogUtils.buildAppListMessage());
            }
            // Just print the requested data, do not mirror
            return;
        }
        if (!StringUtils.isEmpty(options.getPidFile())) {
            try {
                writePid(options);
            } catch (IOException e) {
                Ln.e("Failed to write pid to file: " + e.getMessage());
            }
        }
        if (options.getPrintTopApp()) {
            try {
                printForegroundActivity();
            } catch (Exception e) {
                Ln.e("Failed to printForegroundActivity: " + e.getMessage());
                System.exit(1);
            }
            return;
        }
        if (options.getWakeupStatus()) {
            try {
                printWakeupStatus();
            } catch (Exception e) {
                Ln.e("Failed to printForegroundActivity: " + e.getMessage());
                System.exit(1);
            }
            return;
        }
        if (!StringUtils.isEmpty(options.getClipboard())) {
            String clipboardText = options.getClipboard();
            setClipboard(clipboardText);
            System.exit(0);
            return;
        }
        try {
            scrcpy(options);
        } catch (ConfigurationException e) {
            // Do not print stack trace, a user-friendly error-message has already been logged
        }
    }
    
    private static void printWakeupStatus() {
        Workarounds.apply(FakeContext.SHELL_PACKAGE_NAME);
        
        boolean lastAvailable = false;
        while (true) {
            FileOutputStream Stdout = new FileOutputStream(FileDescriptor.out);
            KeyguardManager km = (KeyguardManager) FakeContext.get().getSystemService(Context.KEYGUARD_SERVICE);
            com.genymobile.scrcpy.wrappers.PowerManager pm = ServiceManager.getPowerManager();
            boolean isLocked = km.isKeyguardLocked();
            boolean isScreenOn = pm.isScreenOn(0);
            boolean available = !isLocked && isScreenOn;
            try {
                if (available != lastAvailable) {
                    if (available) {
                        Stdout.write(("ON" + "\n").getBytes());
                    } else {
                        Stdout.write(("OFF" + "\n").getBytes());
                    }
                    lastAvailable = available;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    private static void setClipboard(String clipboardText) {
        Workarounds.apply(FakeContext.SHELL_PACKAGE_NAME);
        ServiceManager.getClipboardManager().setText(clipboardText);
    }
    
    private static void writePid(Options options) throws IOException {
        if (!StringUtils.isEmpty(options.getPidFile())) {
            String pidFile = options.getPidFile();
            Log.d("scrcpy", "pid file: " + pidFile);
            File file = new File(pidFile);
            if (!file.exists()) {
                int pid = android.os.Process.myPid();
                String pidStr = String.valueOf(pid);
                if (file.createNewFile()) {
                    Log.i("scrcpy", "Create pid file: " + pidFile);
                    Log.i("scrcpy", "Current process id: " + pidStr);
                    // Write the current process id to the file
                    try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                        writer.write(pidStr);
                    } catch (Exception e) {
                        throw new IOException("Failed to write pid to file: " + e.getMessage());
                    }
                } else {
                    throw new IOException("Failed to create pid file");
                }
            } else {
                throw new IOException("Pid file exists, exit process now");
            }
        }
    }
    
    private static String getApplicationName(PackageManager pm, String packageName) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            e.printStackTrace();
            Log.w("scrcpy", "Failed getApplicationName: " + e.getMessage());
            return "未知应用";
        }
    }
    
    public static void printForegroundActivity() throws IOException {
        String lastPkgName = "";
        String lastClsName = "";
        Workarounds.apply(FakeContext.SHELL_PACKAGE_NAME);
        ActivityManager am = ServiceManager.getActivityManager();
        PackageManager pm = FakeContext.get().getPackageManager();
        FileOutputStream Stdout = new FileOutputStream(FileDescriptor.out);
//        Stdout.write("Foreground activity:\n".getBytes());
        Stdout.flush();
        while (true) {
            List<android.app.ActivityManager.RunningTaskInfo> apps = am.getRunningTasks(1);
            if (apps != null && !apps.isEmpty()) {
                ComponentName component = apps.get(0).topActivity;
                if (component != null) {
                    String pkgName = component.getPackageName();
                    String clsName = component.getClassName();
                    if (pkgName.equals(lastPkgName) && clsName.equals(lastClsName)) {
                        continue;
                    }
                    lastPkgName = pkgName;
                    lastClsName = clsName;
                    String appName = getApplicationName(pm, pkgName);
                    Stdout.write((appName + ":" + pkgName + ":" + clsName + "\n").getBytes());
                }
            }
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                Log.w("scrcpy", "Failed to sleep: " + e.getMessage());
            }
        }
        
    }
}

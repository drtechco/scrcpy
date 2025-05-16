#CLASSPATH=/data/local/tmp/scrcpysvc app_process /  \
#com.genymobile.scrcpy.Server  3.2 \
#cleanup=false log_level=debug audio=false \
#video=false control=false send_device_meta=false \
#new_display=1080x2280
jenv shell 17
rm -rf server/build/outputs/apk/debug/server-debug.apk
./gradlew  server:assemble
adb -s R5CR60F701M shell rm -rf /data/local/tmp/scrcpy-server-v3.2
adb -s R5CR60F701M  push server/build/outputs/apk/debug/server-debug.apk /data/local/tmp/scrcpy-server-v3.2
#adb shell CLASSPATH=/data/local/tmp/scrcpy-server-v3.2 \
#    app_process / com.genymobile.scrcpy.Server 3.2 \
#    tunnel_forward=false audio=false control=false cleanup=false \
#    max_fps=29 raw_stream=true  video_codec=h264 stdout=true
#
#adb exec-out CLASSPATH=/data/local/tmp/scrcpy-server-v3.2 \
#    app_process / com.genymobile.scrcpy.Server 3.2 \
#    tunnel_forward=false audio=false control=false cleanup=false \
#    max_fps=60 raw_stream=true max_size=1920 video_codec=h264 stdout=true | \
#vlc - \
#  --demux h264 \
#  --h264-fps=60 \
#  --codec avcodec \
#  --avcodec-codec=h264 \
#  --network-caching=100 \
#  --no-drop-late-frames \
#  --no-skip-frames \
#  --video-filter=canvas \
#  --canvas-width=1080 \
#  --canvas-height=1920 \
#  --canvas-aspect=9:16
#


adb -s R5CR60F701M shell CLASSPATH=/data/local/tmp/scrcpy-server-v3.2 \
    app_process / com.genymobile.scrcpy.Server 3.2 \
    clipboard="我是你爹" raw_stream=true
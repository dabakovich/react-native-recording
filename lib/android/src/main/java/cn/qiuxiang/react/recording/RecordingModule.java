package cn.qiuxiang.react.recording;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

class RecordingModule extends ReactContextBaseJavaModule {
    private static AudioRecord audioRecord;
    private final ReactApplicationContext reactContext;
    private DeviceEventManagerModule.RCTDeviceEventEmitter eventEmitter;
    private boolean running;
    private int bufferSize;
    private Thread recordingThread;

    RecordingModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "Recording";
    }

    @SuppressLint("MissingPermission")
    @ReactMethod
    public void init(ReadableMap options) {
        if (eventEmitter == null) {
            eventEmitter = reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
        }

        if (running || (recordingThread != null && recordingThread.isAlive())) {
            return;
        }

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }

        // for parameter description, see
        // https://developer.android.com/reference/android/media/AudioRecord.html

        int sampleRateInHz = 44100;
        if (options.hasKey("sampleRate")) {
            sampleRateInHz = options.getInt("sampleRate");
        }

        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        if (options.hasKey("channelsPerFrame")) {
            int channelsPerFrame = options.getInt("channelsPerFrame");

            // every other case --> CHANNEL_IN_MONO
            if (channelsPerFrame == 2) {
                channelConfig = AudioFormat.CHANNEL_IN_STEREO;
            }
        }

        // we support only 8-bit and 16-bit PCM
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        if (options.hasKey("bitsPerChannel")) {
            int bitsPerChannel = options.getInt("bitsPerChannel");

            if (bitsPerChannel == 8) {
                audioFormat = AudioFormat.ENCODING_PCM_8BIT;
            }
        }

        if (options.hasKey("bufferSize")) {
            this.bufferSize = options.getInt("bufferSize");
        } else {
            this.bufferSize = 8192;
        }

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRateInHz,
                channelConfig,
                audioFormat,
                this.bufferSize * 2);

        recordingThread = new Thread(new Runnable() {
            public void run() {
                recording();
            }
        }, "RecordingThread");

        recordingThread.setPriority(Thread.MAX_PRIORITY);
    }

    @ReactMethod
    public void start(final Promise promise) {
        long recordingStartTimestamp = System.currentTimeMillis();
        long recordingStartBootTime = SystemClock.elapsedRealtime();

        if (!running && audioRecord != null && recordingThread != null) {
            running = true;
            audioRecord.startRecording();
            recordingThread.start();
        }

        // Resolve { recordingStartTimestamp: number, recordingStartBootTime: number }
        WritableMap recordingStartInfo = Arguments.createMap();
        recordingStartInfo.putDouble("recordingStartTimestamp", recordingStartTimestamp);
        recordingStartInfo.putDouble("recordingStartBootTime", recordingStartBootTime);
        promise.resolve(recordingStartInfo);
    }

    @ReactMethod
    public void stop() {
        if (audioRecord != null) {
            running = false;
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void recording() {
        short[] buffer = new short[bufferSize];
        while (running && !reactContext.getCatalystInstance().isDestroyed()) {
            long startTimestamp = System.currentTimeMillis();
            long startBootTime = SystemClock.elapsedRealtime();

            audioRecord.read(buffer, 0, bufferSize);

            long endTimestamp = System.currentTimeMillis();
            long endBootTime = SystemClock.elapsedRealtime();

            WritableArray data = Arguments.createArray();
            for (float value : buffer) {
                data.pushInt((int) value);
            }

            WritableMap record = Arguments.createMap();
            record.putArray("data", data);

            record.putDouble("startTimestamp", startTimestamp);
            record.putDouble("startBootTime", startBootTime);
            record.putDouble("endTimestamp", endTimestamp);
            record.putDouble("endBootTime", endBootTime);

            eventEmitter.emit("recording", record);
        }
    }

    // Required for rn built in EventEmitter Calls.
    @ReactMethod
    public void addListener(String eventName) {
    }

    @ReactMethod
    public void removeListeners(Integer count) {
    }
}

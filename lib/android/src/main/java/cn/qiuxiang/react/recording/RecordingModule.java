package cn.qiuxiang.react.recording;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
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
    private int sampleRate = 44100;
    private int bufferSize;
    private Thread recordingThread;
    private long recordingStartTimestamp;
    private long recordingStartBootTime;

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

        if (options.hasKey("sampleRate")) {
            sampleRate = options.getInt("sampleRate");
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
            bufferSize = options.getInt("bufferSize");
        } else {
            bufferSize = 8192;
        }

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2);

        recordingThread = new Thread(new Runnable() {
            public void run() {
                recording();
            }
        }, "RecordingThread");

        recordingThread.setPriority(Thread.MAX_PRIORITY);
    }

    @ReactMethod
    public void start() {
        if (!running && audioRecord != null && recordingThread != null) {
            recordingStartTimestamp = System.currentTimeMillis();
            recordingStartBootTime = getSystemUptime();

            running = true;
            audioRecord.startRecording();
            recordingThread.start();
        }
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

    @ReactMethod
    public void getUptime(final Promise promise) {
        long uptime = getSystemUptime();
        long timestamp = System.currentTimeMillis();

        WritableMap response = Arguments.createMap();
        response.putDouble("uptime", uptime);
        response.putDouble("timestamp", timestamp);
        promise.resolve(response);
    }

    // Required for rn built in EventEmitter Calls.
    @ReactMethod
    public void addListener(String eventName) {
    }

    @ReactMethod
    public void removeListeners(Integer count) {
    }

    private void recording() {
        short[] buffer = new short[bufferSize];

        AudioTimestamp audioTimestamp = new AudioTimestamp();
        int totalSamplesRead = 0;

        while (running && !reactContext.getCatalystInstance().isDestroyed()) {
            long durationFromStart = 0;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && audioRecord.getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS) {
                durationFromStart = 1000L * audioTimestamp.framePosition / sampleRate;
            } else {
                durationFromStart = 1000L * totalSamplesRead / sampleRate;
            }

            long startTimestamp = System.currentTimeMillis();
            long startBootTime = getSystemUptime();
            long calculatedStartTimestamp = recordingStartTimestamp + durationFromStart;
            long calculatedStartBootTime = recordingStartBootTime + durationFromStart;

            int samplesRead = audioRecord.read(buffer, 0, bufferSize);

            long endTimestamp = System.currentTimeMillis();
            long endBootTime = getSystemUptime();

            WritableArray data = Arguments.createArray();
            for (float value : buffer) {
                data.pushInt((int) value);
            }

            WritableMap record = Arguments.createMap();
            record.putArray("data", data);

            record.putDouble("startTimestamp", startTimestamp);
            record.putDouble("startBootTime", startBootTime);
            record.putDouble("calculatedStartTimestamp", calculatedStartTimestamp);
            record.putDouble("calculatedStartBootTime", calculatedStartBootTime);
            record.putDouble("endTimestamp", endTimestamp);
            record.putDouble("endBootTime", endBootTime);

            eventEmitter.emit("recording", record);

            totalSamplesRead += samplesRead;
        }
    }

    private long getSystemUptime() {
        return SystemClock.elapsedRealtime();
    }
}

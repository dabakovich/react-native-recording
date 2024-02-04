interface options {
    bufferSize: number;
    sampleRate: number;
    bitsPerChannel: 8 | 16;
    channelsPerFrame: 1 | 2;
}
interface IAudioBuffer {
    data: number[],
    timestamp: number,
}
declare const _default: {
    init: (options: options) => any;
    start: () => any;
    stop: () => any;
    addRecordingEventListener: (listener: (buffer: IAudioBuffer) => void) => import("react-native").EmitterSubscription;
};
export default _default;

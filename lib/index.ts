import { NativeModules, NativeEventEmitter, EmitterSubscription } from "react-native";

const { Recording } = NativeModules;
const eventEmitter = new NativeEventEmitter(Recording);

interface IInitParams {
  bufferSize: number,
  sampleRate: number,
  bitsPerChannel: 8 | 16,
  channelsPerFrame: 1 | 2,
}

export interface IAudioBuffer {
  data: number[],
  startTimestamp: number,
  startBootTime: number,
  calculatedStartTimestamp: number,
  calculatedStartBootTime: number,
  endTimestamp: number,
  endBootTime: number,
}

interface IRecording {
  init: (params: IInitParams) => Promise<void>;
  start: () => void;
  stop: () => void;
  getUptime: () => Promise<{ uptime: number }>;
  addRecordingEventListener: (listener: (buffer: IAudioBuffer) => void) => EmitterSubscription;
}

export default {
  init: Recording.init,
  start: Recording.start,
  stop: Recording.stop,
  getUptime: Recording.getUptime,
  addRecordingEventListener: (listener) =>
    eventEmitter.addListener("recording", listener),
} as IRecording;

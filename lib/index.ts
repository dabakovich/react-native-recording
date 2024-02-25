import { NativeModules, NativeEventEmitter } from "react-native";
const { Recording } = NativeModules;
const eventEmitter = new NativeEventEmitter(Recording);

interface IInitParams {
  bufferSize: number,
  sampleRate: number,
  bitsPerChannel: 8 | 16,
  channelsPerFrame: 1 | 2,
}

export interface IRecordingStartInfo {
  recordingStartTimestamp: number;
  recordingStartBootTime: number;
}

export interface IAudioBuffer {
  data: number[],
  startTimestamp: number,
  endTimestamp: number,
  bootTimestamp: number,
}

export default {
  init: (params: IInitParams) => Recording.init(params),
  start: (): Promise<IRecordingStartInfo> => Recording.start(),
  stop: Recording.stop,
  addRecordingEventListener: (listener: (buffer: IAudioBuffer) => void) =>
    eventEmitter.addListener("recording", listener),
};

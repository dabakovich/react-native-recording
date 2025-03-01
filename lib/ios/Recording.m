#import "Recording.h"

@implementation Recording {
    AudioQueueRef _queue;
    AudioQueueBufferRef _buffer;
    NSNumber *_audioData[65536];
    UInt32 _bufferSize;
    double _sampleRate;
    NSTimeInterval _recordingStartTimestamp;
    NSTimeInterval _recordingStartBootTime;
}

void inputCallback(
        void *inUserData,
        AudioQueueRef inAQ,
        AudioQueueBufferRef inBuffer,
        const AudioTimeStamp *inStartTime,
        UInt32 inNumberPacketDescriptions,
        const AudioStreamPacketDescription *inPacketDescs) {
    [(__bridge Recording *) inUserData processInputBuffer:inBuffer queue:inAQ sampleTime:inStartTime->mSampleTime];
}

RCT_EXPORT_MODULE()

RCT_EXPORT_METHOD(init:(NSDictionary *) options) {
    UInt32 bufferSize = options[@"bufferSize"] == nil ? 8192 : [options[@"bufferSize"] unsignedIntegerValue];
    _bufferSize = bufferSize;

    AudioStreamBasicDescription description;
    description.mReserved = 0;
    description.mSampleRate = options[@"sampleRate"] == nil ? 44100 : [options[@"sampleRate"] doubleValue];
    description.mBitsPerChannel = options[@"bitsPerChannel"] == nil ? 16 : [options[@"bitsPerChannel"] unsignedIntegerValue];
    description.mChannelsPerFrame = options[@"channelsPerFrame"] == nil ? 1 : [options[@"channelsPerFrame"] unsignedIntegerValue];
    description.mFramesPerPacket = options[@"framesPerPacket"] == nil ? 1 : [options[@"framesPerPacket"] unsignedIntegerValue];
    description.mBytesPerFrame = options[@"bytesPerFrame"] == nil ? 2 : [options[@"bytesPerFrame"] unsignedIntegerValue];
    description.mBytesPerPacket = options[@"bytesPerPacket"] == nil ? 2 : [options[@"bytesPerPacket"] unsignedIntegerValue];
    description.mFormatID = kAudioFormatLinearPCM;
    description.mFormatFlags = kAudioFormatFlagIsSignedInteger;

    _sampleRate = description.mSampleRate;

    AudioQueueNewInput(&description, inputCallback, (__bridge void *) self, NULL, NULL, 0, &_queue);
    AudioQueueAllocateBuffer(_queue, (UInt32) (bufferSize * 2), &_buffer);
    AudioQueueEnqueueBuffer(_queue, _buffer, 0, NULL);
}

RCT_EXPORT_METHOD(start:(RCTPromiseResolveBlock)resolve
                  rejecter:(RCTPromiseRejectBlock)reject)
{
    AudioQueueStart(_queue, NULL);

    _recordingStartTimestamp = [[NSDate date] timeIntervalSince1970] * 1000; // milliseconds since 1970
    _recordingStartBootTime = [[NSProcessInfo processInfo] systemUptime] * 1000;

    resolve(@{@"recordingStartTimestamp": @(round(_recordingStartTimestamp)), @"recordingStartBootTime": @(round(_recordingStartBootTime))});
}

RCT_EXPORT_METHOD(stop) {
    AudioQueueStop(_queue, YES);
    _recordingStartTimestamp = 0;
    _recordingStartBootTime = 0;
}

- (void)processInputBuffer:(AudioQueueBufferRef)inBuffer queue:(AudioQueueRef)queue sampleTime:(Float64)sampleTime {
    Float64 durationFromStart = 1000 * sampleTime / _sampleRate; // milliseconds

    NSTimeInterval startTimestamp = _recordingStartTimestamp + durationFromStart;
    NSTimeInterval startBootTime = _recordingStartBootTime + durationFromStart;

    SInt16 *audioData = inBuffer->mAudioData;
    UInt32 count = inBuffer->mAudioDataByteSize / sizeof(SInt16);
    NSMutableArray *audioArray = [NSMutableArray arrayWithCapacity:_bufferSize];
    for (int i = 0; i < count; i++) {
        [audioArray addObject:@(audioData[i])];
    }

    NSTimeInterval endTimestamp = [[NSDate date] timeIntervalSince1970] * 1000; // milliseconds since 1970
    NSTimeInterval endBootTime = [[NSProcessInfo processInfo] systemUptime] * 1000;

    // Creating the event payload
    NSDictionary *eventPayload = @{
        @"data": audioArray,
        @"startTimestamp": @(round(startTimestamp)),
        @"startBootTime": @(round(startBootTime)),
        @"calculatedStartTimestamp": @(round(startTimestamp)),
        @"calculatedStartBootTime": @(round(startBootTime)),
        @"endTimestamp": @(round(endTimestamp)),
        @"endBootTime": @(round(endBootTime))
    };

    // Sending the event
    [self sendEventWithName:@"recording" body:eventPayload];

    AudioQueueEnqueueBuffer(queue, inBuffer, 0, NULL);
}

RCT_EXPORT_METHOD(getUptime:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    NSTimeInterval uptime = [[NSProcessInfo processInfo] systemUptime] * 1000;
    NSTimeInterval timestamp = [[NSDate date] timeIntervalSince1970] * 1000; // milliseconds since 1970

    resolve(@{@"uptime": @(round(uptime)), @"timestamp": @(round(timestamp))});
}

- (NSArray<NSString *> *)supportedEvents {
    return @[@"recording"];
}

- (void)dealloc {
    AudioQueueStop(_queue, YES);
}

@end

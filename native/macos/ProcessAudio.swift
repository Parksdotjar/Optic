import Foundation
import ScreenCaptureKit
import CoreMedia
import CoreGraphics
import AVFoundation
import Darwin

// stdout is a binary-only pipe: OPM1 + 48 kHz + stereo, followed by
// (host-clock nanoseconds u64, frame count u32, interleaved s16le samples).
// All integers are little endian. No desktop-wide fallback is permitted.
private func fail(_ message: String) -> Never {
    FileHandle.standardError.write(Data((message + "\n").utf8))
    exit(1)
}

private func append<T: FixedWidthInteger>(_ value: T, to data: inout Data) {
    var little = value.littleEndian
    withUnsafeBytes(of: &little) { data.append(contentsOf: $0) }
}

@available(macOS 13.0, *)
final class Capture: NSObject, SCStreamOutput, SCStreamDelegate {
    let queue = DispatchQueue(label: "dev.optic.audio.samples")
    private var ready = false // accessed only on queue
    private var stream: SCStream?
    private var lastTimestamp: UInt64 = 0

    func start(pid: pid_t) async throws {
        let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: false)
        guard let application = content.applications.first(where: { $0.processID == pid }) else {
            fail("Minecraft process is not available to ScreenCaptureKit. Open a Minecraft window and retry.")
        }
        guard let display = content.displays.first else { fail("No capture display is available.") }
        let filter = SCContentFilter(display: display, including: [application], exceptingWindows: [])
        let config = SCStreamConfiguration()
        config.capturesAudio = true
        config.excludesCurrentProcessAudio = true // helper is separate from the target JVM
        config.sampleRate = 48_000
        config.channelCount = 2
        // ScreenCaptureKit owns a tiny display stream. Video is discarded; Optic's
        // existing Minecraft renderer remains the source of the recorded video.
        config.width = 2
        config.height = 2
        config.minimumFrameInterval = CMTime(value: 1, timescale: 1)
        config.queueDepth = 3
        config.showsCursor = false
        let capture = SCStream(filter: filter, configuration: config, delegate: self)
        try capture.addStreamOutput(self, type: .audio, sampleHandlerQueue: queue)
        try capture.addStreamOutput(self, type: .screen, sampleHandlerQueue: queue)
        stream = capture
        try await capture.startCapture()
        queue.sync {
            var header = Data()
            append(UInt32(0x314d504f), to: &header)
            append(UInt32(48_000), to: &header)
            append(UInt32(2), to: &header)
            do { try FileHandle.standardOutput.write(contentsOf: header) }
            catch { fail("Audio reader disconnected.") }
            ready = true
        }
    }

    func stop() async {
        try? await stream?.stopCapture()
        queue.sync { ready = false }
    }

    func stream(_ stream: SCStream, didStopWithError error: Error) {
        fail("Minecraft audio capture stopped: \(error.localizedDescription)")
    }

    func stream(_ stream: SCStream, didOutputSampleBuffer buffer: CMSampleBuffer, of type: SCStreamOutputType) {
        guard ready, type == .audio, buffer.isValid, CMSampleBufferDataIsReady(buffer) else { return }
        let frames = CMSampleBufferGetNumSamples(buffer)
        guard frames > 0, frames <= 48_000,
              let description = CMSampleBufferGetFormatDescription(buffer),
              let pointer = CMAudioFormatDescriptionGetStreamBasicDescription(description) else {
            fail("Invalid Minecraft audio format.")
        }
        let format = pointer.pointee
        let planar = format.mFormatFlags & kAudioFormatFlagIsNonInterleaved != 0
        // ScreenCaptureKit delivers float32 PCM. Refuse unexpected formats instead
        // of interpreting them as samples, changing channels, or changing speed.
        guard format.mFormatID == kAudioFormatLinearPCM, format.mSampleRate == 48_000,
              format.mChannelsPerFrame == 2, format.mBitsPerChannel == 32,
              format.mBytesPerFrame == (planar ? 4 : 8),
              format.mFormatFlags & kAudioFormatFlagIsFloat != 0,
              format.mFormatFlags & kAudioFormatFlagIsBigEndian == 0 else {
            fail("ScreenCaptureKit did not supply 48 kHz stereo float audio.")
        }
        let pts = CMSampleBufferGetPresentationTimeStamp(buffer)
        let nanos = CMTimeConvertScale(pts, timescale: 1_000_000_000, method: .default)
        guard nanos.isNumeric, nanos.value >= 0 else { fail("Invalid audio timestamp.") }
        let hostNow = CMClockGetTime(CMClockGetHostTimeClock()).seconds
        guard abs(pts.seconds - hostNow) < 5 else { fail("Audio is not on the host clock.") }
        let timestamp = UInt64(nanos.value)
        guard timestamp >= lastTimestamp else { fail("Audio clock moved backwards.") }
        lastTimestamp = timestamp

        // CoreMedia owns the samples; keep its retained block alive during conversion.
        let byteCount = MemoryLayout<AudioBufferList>.size + MemoryLayout<AudioBuffer>.size
        let raw = UnsafeMutableRawPointer.allocate(byteCount: byteCount, alignment: MemoryLayout<AudioBufferList>.alignment)
        defer { raw.deallocate() }
        let list = raw.bindMemory(to: AudioBufferList.self, capacity: 1)
        var retained: CMBlockBuffer?
        let status = CMSampleBufferGetAudioBufferListWithRetainedBlockBuffer(buffer,
            bufferListSizeNeededOut: nil, bufferListOut: list, bufferListSize: byteCount,
            blockBufferAllocator: nil, blockBufferMemoryAllocator: nil,
            flags: UInt32(kCMSampleBufferFlag_AudioBufferList_Assure16ByteAlignment), blockBufferOut: &retained)
        guard status == noErr else { fail("Could not read Minecraft audio samples.") }
        let buffers = UnsafeMutableAudioBufferListPointer(list)
        guard buffers.count == (planar ? 2 : 1) else { fail("Unexpected audio buffer count.") }
        for channel in buffers {
            guard channel.mData != nil, channel.mNumberChannels == (planar ? 1 : 2),
                  Int(channel.mDataByteSize) >= frames * (planar ? 4 : 8) else { fail("Truncated audio buffer.") }
        }
        withExtendedLifetime(retained) {
            for offset in stride(from: 0, to: frames, by: 480) {
                let count = min(480, frames - offset)
                var packet = Data(capacity: 12 + count * 4)
                append(timestamp + UInt64(offset) * 1_000_000_000 / 48_000, to: &packet)
                append(UInt32(count), to: &packet)
                for frame in offset..<(offset + count) {
                    for channel in 0..<2 {
                        let samples = buffers[planar ? channel : 0].mData!.assumingMemoryBound(to: Float.self)
                        let value = samples[planar ? frame : frame * 2 + channel]
                        let finite = value.isFinite ? value : 0
                        let sample = Int16(max(-32768, min(32767, finite * 32768)))
                        append(sample, to: &packet)
                    }
                }
                do { try FileHandle.standardOutput.write(contentsOf: packet) }
                catch { fail("Audio reader disconnected.") }
            }
        }
    }
}

@main
struct ProcessAudio {
    static func main() async {
        signal(SIGPIPE, SIG_IGN)
        guard #available(macOS 13.0, *) else { fail("Minecraft audio requires macOS 13 Ventura or newer.") }
        if CommandLine.arguments.dropFirst().first == "--version" {
            print("Optic Mac audio helper 1; OPM1; macOS 13+; 48000 Hz stereo")
            return
        }
        if CommandLine.arguments.dropFirst().first == "--request-permission" {
            let granted = CGPreflightScreenCaptureAccess() || CGRequestScreenCaptureAccess()
            FileHandle.standardError.write(Data((granted ? "Permission granted. Restart Minecraft if capture is still unavailable.\n" : "Allow Screen & System Audio Recording in System Settings, then restart Minecraft.\n").utf8))
            exit(granted ? 0 : 2)
        }
        guard CommandLine.arguments.count == 2, let pid = Int32(CommandLine.arguments[1]), pid > 0 else {
            fail("Usage: optic-process-audio <Minecraft PID> | --request-permission")
        }
        guard CGPreflightScreenCaptureAccess() else {
            fail("Permission required. Use Optic Settings > Audio > Allow Minecraft audio on Mac, then restart Minecraft.")
        }
        let capture = Capture()
        do { try await capture.start(pid: pid) }
        catch { fail("Minecraft audio could not start: \(error.localizedDescription)") }
        // EOF also stops capture if the JVM exits unexpectedly.
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            DispatchQueue.global().async {
                _ = FileHandle.standardInput.readData(ofLength: 1)
                continuation.resume()
            }
        }
        await capture.stop()
    }
}

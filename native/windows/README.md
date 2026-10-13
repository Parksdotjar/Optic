# Windows process-audio helper

`ProcessAudio.cs` is Optic-owned source. Gradle compiles it with the installed Windows .NET Framework 4.x C# compiler, targeting x64. Generated output lives in `build/generated/audio-resources/native/windows/`; only that executable is included as a resource. No prebuilt helper is downloaded. No Visual Studio install is required on the current development machine.

Runtime requires Windows build 20348+ and .NET Framework 4.x. The helper accepts exactly a process ID, captures that process tree through WASAPI, emits a fixed binary PCM protocol on stdout and logs errors/overruns to stderr. Stdin EOF or any line requests shutdown. It does not enumerate unrelated applications or capture the whole desktop.

Protocol: little-endian magic 0x4f505433, then repeated signed 64-bit QPC timestamps in 100 ns units, signed 32-bit frame counts (1–480), and stereo signed 16-bit little-endian PCM at 48 kHz. Ten reusable packet objects bound the native-side queue to 100 ms; overflow skips packets and logs dropped samples. Timestamps allow Java to preserve skipped time as silence. Windows/pipe buffering is additional.

Verify with `gradlew.bat audioIntegrationTest`. This plays a quiet test tone and never opens a microphone. Non-Windows builds omit the helper, and the game-audio capability remains unavailable there. Windows ARM64 is not currently supported.

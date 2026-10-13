# Startup stability investigation

## Evidence

The two Windows crash dumps from the migration were inspected locally; no dumps were uploaded. One Java 21 failure occurred in `lwjgl.dll + 0x10c5`, dereferencing a null JVM pointer in the library's thread-detach callback. The disassembly matches LWJGL 3.3.3's `DllMain` calling `getThreadEnv()` before `JNI_OnLoad` assigns the global JVM pointer. Reference: https://raw.githubusercontent.com/LWJGL/lwjgl3/3.3.3/modules/lwjgl/core/src/main/c/common_tools.c

Optic previously started Java Sound device enumeration from its initializer, concurrent with Minecraft loading its native libraries. The dump contains Java Sound, DirectSound, and injected audio-enhancement modules. Enumeration can create short-lived native threads. This is a plausible trigger for the observed initialization race, not proof that every native crash came from Optic.

The separate Java 25 dump faults inside `jvm.dll` on a thread with shader-compiler frames. It also contains external audio and graphics hooks. Their presence alone does not establish fault. That signature remains unresolved; no system services, drivers, overlays, or global JVM settings were modified.

## Change

Microphone discovery is now lazy: opening Audio settings requests it on the existing control worker. Concurrent requests coalesce, refresh is limited to once per five seconds, and failure retains the last device list. Initializing the mod no longer invokes Java Sound. Recording still opens the selected input during worker-side session preparation. The smoke harness requests device discovery only after Minecraft has reached a ready screen.

The change removes Optic's eager native-audio startup trigger. It is not a patch to LWJGL itself and cannot guarantee protection from other mods or native hooks creating threads during library loading.

## Reproduction and validation

Run `:1.21.11:productionSmoke`, `:26.2:productionSmoke`, and `:26.3:productionSmoke` sequentially. Each must log a successful saved recording and exit normally. The task fails if the client merely exits without completing the recording. Inspect decoded output and Windows Application events as well as Gradle exit codes.

Production smoke uses a 2 GiB heap and four active processors for bounded automated testing. Also test normal `runClient` launches without those limits; success under smoke settings alone is not evidence of a universal fix. Continue physical microphone/reconnect, long-session, driver, and optional-mod validation before production certification.

Final checks: 68 unit tests passed per target (204 total). One complete repeat round passed on all three targets with four microphone choices discovered after startup, and the saved files decoded successfully. The second round completed 1.21.11 and 26.2; the 26.3 client was closed during preparation when testing was stopped, so that smoke correctly failed its completion check. No new native crash was observed in these checks. Final JAR collection succeeded; no test client was left running.

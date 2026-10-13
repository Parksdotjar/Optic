# Optic test builds

| Current file | Minecraft | Java | Fabric API tested |
| --- | --- | --- | --- |
| optic-0.10.2-mc1.21.11.jar | Exactly 1.21.11 | 21+ | 0.141.6+1.21.11 |
| optic-0.10.2-mc26.2.jar | Exactly 26.2 | 25+ | 0.161.0+26.2 |
| optic-0.10.2-mc26.3.jar | Exactly 26.3 | 25+ | 0.162.0+26.3 |

Install one matching Optic JAR with Fabric Loader 0.19.5 or newer and the Fabric API for that exact Minecraft version. Older artifacts remain for reference; use the 0.10.2 files above. No server installation is needed.

All three artifacts use the shared Stonecutter project and passed 68 unit tests each. 26.2 passed packaged recording/pause/resume/save and an actual world recording with HUD exclusion. Saved H.264/AAC output decoded correctly. Short checks do not certify long sessions, Vulkan, all GPUs, microphone reconnection, or every mod combination.

0.10.2 removes eager Java Sound enumeration during native-library startup; Audio settings discovers devices on demand. This removes a plausible trigger for the observed LWJGL initialization race. A separate historical Java VM crash remains unconfirmed, so this is not a blanket crash-free claim. See ../STARTUP_STABILITY.md.

26.1 and other 1.21.x releases remain unsupported. Stonecutter shares source code; it does not make binaries compatible across releases. No performance advantage over OBS has been established.

Rebuild from the project root with `gradlew.bat collectJars` using Java 25 and Java 21/25 toolchains. Checksums are in `SHA256SUMS.txt`.

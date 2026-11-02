# NDI Bridge

NDI Bridge is a Java 22+ Foreign Function & Memory (FFM) library for a caller-supplied NDI runtime. It does not use JNI or bundle native libraries.

Maven coordinates: `org.praisenter:ndi-bridge`.

## Requirements

- JDK 22 or newer and Maven 3.9+ installed globally and available as `mvn` on `PATH`.
- An authorized NDI SDK installation to generate bindings.
- The OpenJDK `jextract` tool for Windows x64.
- An NDI runtime file installed separately by the application.

For a modular consumer, enable native access to the NDI Bridge module in the Java launcher:

```text
--enable-native-access=org.praisenter.ndibridge
```

For a classpath-based consumer, use `--enable-native-access=ALL-UNNAMED` instead. A dependency JAR cannot enable native access for its consumer.

## Build

On Windows, Linux or macOS:

```sh
mvn clean verify
mvn install
```

Check the installed tools with `java -version` and `mvn -version`. Maven may download required build plugins and dependencies from your configured repositories. The JAR, sources and Javadoc are written to `target/`. `install` adds the snapshot to the local Maven repository.

## GitHub Actions

CI runs `mvn clean verify` on Java 22 and 25. CD publishes to Maven Central when a `v*.*.*` tag matches the POM version; for example, tag `v0.1.0` when the POM version is `0.1.0`.

Configure these repository secrets for CD:

- `gpg_private_key`: ASCII-armored GPG private key (not just the key ID).
- `gpg_passphrase`: passphrase for that key.
- `nexus_username` and `nexus_password`: Maven Central Portal user-token credentials.

The private key is imported into a temporary GPG home by `actions/setup-java`; it is not stored in the repository.

```xml
<dependency>
  <groupId>org.praisenter</groupId>
  <artifactId>ndi-bridge</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Install the NDI SDK

Download the SDK from the [official NDI developer site](https://ndi.video/for-developers/ndi-sdk/download/) and review its license agreement and third-party notices. Do not commit SDK installers, headers or NDI runtime binaries.

The Windows SDK include directory must contain `Processing.NDI.Lib.h` and `Processing.NDI.DynamicLoad.h`. For the installed SDK, the default path is:

```text
C:\Program Files\NDI\NDI 6 SDK\Include
```

The NDI 6.3 SDK declares the `NDIlib_v6_3` function table and exports `NDIlib_v6_3_load`. The table name comes from the installed header; table offsets are generated, not hard-coded.

## Install jextract

Install the Windows x64 binary from the [OpenJDK jextract downloads](https://jdk.java.net/jextract/) and note the path to `jextract.bat`. The prebuilt tool includes its Java runtime. LLVM/libclang is only required if building jextract from source. The checked-in bindings were generated with jextract 25 on Windows and compile to Java 22 bytecode.

## Generate FFM Bindings

From the project root, run:

```powershell
.\scripts\generate-ffm.ps1 `
  -SdkInclude "C:\Program Files\NDI\NDI 6 SDK\Include" `
  -Jextract "C:\tools\jextract\bin\jextract.bat"
```

The PowerShell script currently runs on Windows, but writes to the shared package `src/main/java/org/praisenter/ndibridge/internal/generated`; there is no OS or architecture subfolder. It removes jextract's unused `C_LONG` helper after verifying that none of the selected generated types depend on it. The selected NDI structs use fixed-width integers and pointers, so the generated layouts are intended to be shared across the requested 64-bit targets. Linux and macOS ABI/runtime validation is still required. Review the changes, then compile and test:

```powershell
  mvn clean verify
```

The script uses the installed SDK headers and jextract; it does not create platform-specific Java code. The other platforms still need validation before being declared supported, and their future GitHub workflow is not set up yet.

## Runtime Loading

The application supplies the absolute path to the actual NDI runtime file, not its containing directory:

```java
Path runtime = Path.of("C:/my-application/native/Processing.NDI.Lib.x64.dll");
int result = NdiBridge.loadLibraries(runtime);
if (result != 0) {
    throw new IllegalStateException(NdiBridge.getLastLoadError().orElse("NDI loading failed"));
}
```

NDI Bridge does not bundle, search for or download the runtime. The caller must also provide the runtime's operating-system dependencies.

### Manual NdiSender Test

`SenderColorManualTest` sends a full-frame 1920x1080 color cycle for NDI Tools to receive. It is skipped during normal test runs. From PowerShell, start it with the absolute path to the NDI runtime; press Enter in the terminal to stop:

```powershell
mvn -Dtest=SenderColorManualTest -DndibridgeManualSender=true `
  "-Dndibridge.runtime=C:\Program Files\NDI\NDI 6 SDK\Bin\x64\Processing.NDI.Lib.x64.dll" test
```

Frames, finders, senders, receivers, frame-sync objects, routers, sources and performance data implement `AutoCloseable`; use try-with-resources and close captured frames before their owning receiver/frame-sync. Async video sends retain the supplied direct buffer until the next async/sync send, a null flush, or sender close. The same `NdiVideoFrame` object may be reused with alternating direct buffers. Declare the frame before the sender in try-with-resources so the sender closes and flushes before the frame.

## Status

The shared FFM bindings and renamed desktop API surface are implemented and compile with Java 22. The installed Windows NDI 6.3.2 runtime has been smoke-tested for initialization, discovery, sender/receiver creation, same-host video/audio/metadata round trips, frame sync, routing, performance queries and audio conversion. Linux and macOS ABI/runtime validation and a compile/run migration against Praisenter remain outstanding, so this is not yet a drop-in replacement.

## License

NdiBridge is BSD-3-Clause licensed; see [LICENSE](LICENSE). SDK use and runtime distribution are governed separately by NDI's terms.

NDI is a registered trademark of Vizrt NDI AB. Proprietary NDI SDKs and runtime
libraries are not included. Their agreements and third-party rights are separate
from this project's BSD 3-Clause license and must be reviewed by users/maintainers.
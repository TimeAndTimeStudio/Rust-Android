# Android architectures

The same ABI selection supports `x86` and `x86_64`, adapted from the Android
fork's four-ABI build. Their native launchers, C++ libraries and updater APK
selection match the selected ABI. CI builds each ABI separately.
No architecture prepares the game files on the phone: the app ships no Java
runtime and downloads none. The resources horizon-prepare makes on a computer
are imported as a zip, on every ABI alike. The x86 architectures have not been
tested on a physical device.

ARM64, ARMv7, x86 and x86_64 share the same build/sign/release matrix. Each is
published as a separate APK; release publication waits for all four builds.
A failed build of any ABI deliberately blocks that release batch. All of them
import the prepared resources the same way.
ARMv7 builds use `armeabi-v7a` and require Android
8 or newer. CPU ABI support alone does not guarantee that a device's graphics
driver can run the engine. Older PowerVR/Mali/Adreno hardware needs separate
on-device validation; a successful run on a modern tablet is not evidence for it.

## Build

Install the Android SDK/NDK pinned in `packaging/android/build_apk.py`, a JDK,
`cargo-ndk` and Rust's `armv7-linux-androideabi` target. Then:

```sh
python packaging/android/build_apk.py --abi armeabi-v7a --dev
```

`--abi arm64-v8a` preserves the default build. Each APK contains only its selected
ABI. Every architecture follows the same Preview/debug/release signing rules and
profile selection. Tagged releases are published once, with all four APKs and
one shared Starling Visuals archive, only in the existing release repository.
PR and fork builds never publish releases. ARM64 is only the local command's
default ABI. Manual native-mod APK builds also receive the selected ABI and
variant, with separate build caches for each architecture; the separate native-mod
prebuilt-library service retains its existing scope.

## Graphics and address space

The renderer still selects available backends and checks their capabilities.
Legacy GLES driver workarounds are adapted from the Android fork: conditional
sprite selection, unrolled RGSS/overlay/emissive lookups, packed terrain varyings
and conditional HDR texture selection. Starling's tiled-face RGSS behavior, HDR
scene varying and all 128 emissive entries are preserved. These transformations
apply only to GLES; the Vulkan WGSL remains byte-for-byte unchanged.
The Android fork's fallback for adapters without `BASE_VERTEX` rebases the
vertex-buffer binding and draws with a zero base vertex. It applies to both
opaque and translucent terrain, retaining Starling's bounded arena bindings.
Vulkan is optional in the additional-ABI manifests so a GLES-only device can attempt to
start; this does not guarantee support for every GLES driver. Actual API,
texture-array and shader limits remain authoritative.

Existing terrain texture-array splitting follows the adapter's layer limit,
with at most eight arrays. A pack that needs more layers than those arrays hold
is rejected rather than silently losing textures.

The shared GUI/entity atlas follows the GPU's 2D texture limit. Android uses
Horizon Android's skyline packing for small atlases so gaps below shorter
images remain usable. Its fitting policy preserves GUI, fonts and dynamic
slots, reducing eligible non-UI sprites only when packing still fails.
Android OpenGL panoramas use the fork's 256-pixel box-filtered faces and an
explicit cube vertex buffer. Vulkan retains full-resolution panorama faces.

Block-sprite budgets retain Starling's configured values and its 256 MiB default,
as in the Android fork; there is no extra 32-bit 64/128 MiB override. These
budgets do not bound total game memory.
Normal GPU arena growth on Android ARM32 uses steps of at most 8 MiB, following
the Android fork's policy while retaining Starling's newer paged arenas. A single
larger allocation request can still require a larger step.

The updater selects the APK matching the process ABI. A release with only an
ARM64 APK is not offered as an ARMv7 update. Native-mod libraries must also match
the process ABI; existing ARM64 prebuilt native mods are not ARMv7 builds.
The build service and prebuilt selection are offered only in ARM64 processes.
Other architectures explain this limitation on imported native mods and do not
submit builds or download incompatible libraries.

APKs carry no exporter bundle: the phone runs no Java and compiles nothing for
the game. A host JDK is needed only to build the activity's dex (javac, d8), as
every Android app is built. Pinned Minecraft jars/libraries stay in the ignored
build cache and are never packaged.

## Relation to diagnostics

Android preparation-lock fixes and renderer-independent diagnostic recovery from
PR #272 are included from the target branch. Never commit Minecraft files,
generated resource tables or prepared resources.

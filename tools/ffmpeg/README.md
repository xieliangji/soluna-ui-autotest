# Bundled FFmpeg

Place platform-specific FFmpeg executables here so managed Appium and screen-recording frame analysis do not depend on a host-level FFmpeg installation.

Current bundled binaries come from `eugeneware/ffmpeg-static` release `b6.1.1`:

```text
https://github.com/eugeneware/ffmpeg-static/releases/tag/b6.1.1
```

The upstream package is licensed as `GPL-3.0-or-later`. Each platform directory keeps the upstream README and LICENSE files as `README.upstream.txt` and `LICENSE.upstream.txt`.

Expected paths:

```text
tools/ffmpeg/macos-arm64/ffmpeg
tools/ffmpeg/macos-x64/ffmpeg
tools/ffmpeg/linux-arm64/ffmpeg
tools/ffmpeg/linux-x64/ffmpeg
tools/ffmpeg/windows-x64/ffmpeg.exe
```

Resolution order:

1. `-Dsoluna.ffmpeg.path=/absolute/path/to/ffmpeg`
2. `SOLUNA_FFMPEG=/absolute/path/to/ffmpeg`
3. `-Dsoluna.tools.dir=/absolute/path/to/tools` or `SOLUNA_TOOLS_DIR=/absolute/path/to/tools`
4. Bundled `tools/ffmpeg/<os>-<arch>/ffmpeg`
5. `ffmpeg` from `PATH`

For managed Appium server startup, only explicit and bundled FFmpeg paths are prepended to `PATH`. This is required by Appium XCUITest screen recording, which launches the command named `ffmpeg` from the Appium server process environment.

Bundled executable SHA-256:

```text
a90e3db6a3fd35f6074b013f948b1aa45b31c6375489d39e572bea3f18336584  macos-arm64/ffmpeg
ebdddc936f61e14049a2d4b549a412b8a40deeff6540e58a9f2a2da9e6b18894  macos-x64/ffmpeg
6bb182d0d75d23028db82e9e4f723ca69b853d055698486e6984ddb2c06fb8ce  linux-arm64/ffmpeg
e7e7fb30477f717e6f55f9180a70386c62677ef8a4d4d1a5d948f4098aa3eb99  linux-x64/ffmpeg
04e1307997530f9cf2fe35cba2ca7e8875ca91da02f89d6c7243df819c94ad00  windows-x64/ffmpeg.exe
```

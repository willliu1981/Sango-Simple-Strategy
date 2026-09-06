# Third-party notices

## LibGDX 1.13.1

Sango 透過 Gradle 使用 LibGDX core、FreeType、Android backend 與 LWJGL3 backend。正式散布 binary 時，應依 LibGDX 與其 runtime dependencies 的適用授權保留必要 notice／license。

## SimpleUI 1.1.2

Binary location:

```text
libs/simpleui-1.1.2.jar
```

提供的 StudyRoutine snapshot 沒有包含 SimpleUI 授權檔。將此 binary 散布到個人專案範圍之外前，必須先向權利人確認適用授權與再散布條件。

Gradle build 會建立 `simpleui-1.1.2-no-bundled-gdx.jar`，並排除 `com/badlogic/**`，避免 JAR 內 bundled LibGDX classes 與專案宣告的 LibGDX 版本衝突。

## Source Han Sans

Sango 預期開發機在執行 `tools/prepare-local-fonts.ps1` 後具有：

```text
assets/font/SourceHanSansCN-Regular.otf
assets/font/SourceHanSansCN-Heavy.otf
```

字型 binary 從開發者自己的 StudyRoutine source 複製，不重複放入本 Source ZIP。隨附授權文字位於：

```text
assets/font/SIL Open Font License 1.1.md
```

## Prototype Audio

`assets/audio/music/*.ogg` 與 `assets/audio/sfx/*.ogg` 由：

```text
tools/generate-prototype-audio.py
```

以基本波形、程序節奏與五聲音階自行合成。這些檔案沒有取樣或改編既有遊戲、影視或商業曲目。

生成腳本只使用 Python standard library，但輸出 OGG 時會呼叫開發機上的 `ffmpeg`／Vorbis encoder。Sango repository 不包含 `ffmpeg` binary；若重新產生或再散布工具鏈，應自行確認所使用 ffmpeg build 的授權設定與義務。

## LWJGL3 StartupHelper

`lwjgl3/src/main/java/idv/kuan/studio/sango/lwjgl3/StartupHelper.java` 保留原有 Apache License 2.0 copyright 與 license header。

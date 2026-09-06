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

## 預計加入的 Suno BGM

本 notice 發布時，`assets/audio/music/*.ogg` 仍是上述程序化 Prototype Audio；repository 尚未包含 Suno 生成的曲目。

未來若將 Suno 生成 BGM 納入公開 source、release 或可下載遊戲，維護者必須先完成以下確認：

- 曲目是在 Suno Pro 或 Premier 訂閱有效期間生成，且經 Suno 核准的下載管道取得；保留曲目 ID、生成日期、訂閱層級與下載日期等內部佐證。
- 不使用免費／Basic 方案生成的曲目。Suno 將該方案輸出限制為合法、個人且非商業使用；免費公開 Sango 不會自動解除這項限制。
- 所有提交至 Suno 的音訊、歌詞、人聲與其他素材均具有足夠權利；不使用他人曲目、人聲或未經授權素材的延伸／Remix。
- 公開說明可標示「Music generated with Suno」，但不得暗示 Suno 背書、合作或保證該曲目的權利狀態。

Suno 的授權與付費權利不等於著作權保證、排他性保證或第三方不主張權利的保證。Sango 不主張對 AI 生成音訊中純由 AI 產出的部分具有當然的排他著作權；任何 AI BGM 都不因併入 Sango 而自動納入 Sango 原始碼的授權範圍。收到具體權利爭議、侵權通知或來源無法佐證時，維護者應優先下架或替換相關音檔。

本聲明僅說明來源與風險管理政策，不構成法律意見、權利擔保、授權保證或對第三方權利主張的豁免。使用或再散布者仍須遵守加入曲目當時有效的 Suno 條款、所在地法律及發行平台規範。

## LWJGL3 StartupHelper

`lwjgl3/src/main/java/idv/kuan/studio/sango/lwjgl3/StartupHelper.java` 保留原有 Apache License 2.0 copyright 與 license header。

# Third-party notices

## LibGDX 1.13.1

Sango 透過 Gradle 使用 LibGDX core、FreeType、Android backend 與 LWJGL3 backend。正式散布 binary 時，應依 LibGDX 與其 runtime dependencies 的適用授權保留必要 notice／license。

## SimpleUI 1.1.2

Binary location:

```text
libs/simpleui-1.1.2.jar
```

目前 repository 未附 SimpleUI 1.1.2 的專用授權檔。將此 binary 散布到個人專案範圍之外前，必須先向權利人確認適用授權與再散布條件。

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

`assets/audio/sfx/*.ogg` 由：

```text
tools/generate-prototype-audio.py
```

以基本波形、程序節奏與五聲音階自行合成。這些檔案沒有取樣或改編既有遊戲、影視或商業曲目。

生成腳本只使用 Python standard library，但輸出 OGG 時會呼叫開發機上的 `ffmpeg`／Vorbis encoder。Sango repository 不包含 `ffmpeg` binary；若重新產生或再散布工具鏈，應自行確認所使用 ffmpeg build 的授權設定與義務。

## Suno BGM

依開發者提供的來源資訊，本機目前使用的 BGM 為 Suno 免費方案生成並下載的音樂（Music generated with Suno），不是上述程序合成音樂。這些音檔尚未因此取得公開遊戲散布的額外授權確認。

遊戲以免費分享為目的，預計提供完整 APK 與 Windows ZIP，不另外提供獨立素材包。音檔若包含在成品裡，仍屬於隨遊戲散布。

- Suno 免費方案限合法、個人及非商業用途。官方資料未明確確認將免費方案音檔隨免費遊戲提供下載的情況；不將此聲明解讀為已取得該用途的授權。
- 保留曲目 ID、生成日期、方案與下載日期等來源紀錄；個別歌曲連結尚待補齊，不以 Suno 首頁代替歌曲來源證明。
- 所有提交至 Suno 的音訊、歌詞、人聲與其他素材均具有足夠權利；不使用他人曲目、人聲或未經授權素材的延伸／Remix。
- 公開說明可標示「Music generated with Suno」，但不得暗示 Suno 背書、合作或保證該曲目的權利狀態。

官方來源：[Suno](https://suno.com/)、[服務條款](https://about.suno.com/terms)、[免費方案權利說明](https://help.suno.com/en/articles/9601601)。查閱日期：2026-09-08。

Suno 的授權與付費權利不等於著作權保證、排他性保證或第三方不主張權利的保證。Sango 不主張對 AI 生成音訊中純由 AI 產出的部分具有當然的排他著作權；任何 AI BGM 都不因併入 Sango 而自動納入 Sango 原始碼的授權範圍。收到具體權利爭議、侵權通知或來源無法佐證時，維護者應優先下架或替換相關音檔。

本聲明僅說明來源與風險管理政策，不構成法律意見、權利擔保、授權保證或對第三方權利主張的豁免。使用或再散布者仍須遵守加入曲目當時有效的 Suno 條款、所在地法律及發行平台規範。

## LWJGL3 StartupHelper

`lwjgl3/src/main/java/idv/kuan/studio/sango/lwjgl3/StartupHelper.java` 保留原有 Apache License 2.0 copyright 與 license header。

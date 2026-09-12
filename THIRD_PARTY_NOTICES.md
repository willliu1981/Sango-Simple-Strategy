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

作者提供的 Suno 個人頁：[willliu1981](https://suno.com/@willliu1981)。此連結作為作者來源入口，不是逐曲權利證明，亦不將該頁其他作品納入本專案授權。

依作者於 2026-09-13 的確認，0.6.3 使用的六首 BGM 為 Suno Pro 方案正式下載（Music generated with Suno），不是上述程序合成音樂。曲名、檔案與曲長見 [音樂清單](assets/audio/README.md)。此來源聲明依作者提供的資訊記錄，並非本專案已獨立稽核其帳戶或下載收據。

作者在有權授予的範圍內，同意他人下載、使用、修改、再散布與商用這六首音樂，包括其他遊戲或影音用途，詳見 [使用授權](LICENSE.md)。不追溯授權舊版音樂或其他未列明音檔；歷史版本文件中的免費方案聲明保留為當時紀錄。

### 條款核對

查閱日期：2026-09-13；[Suno 現行條款](https://suno.com/terms) 標示於 2026-09-03 生效。

- 付費方案下經官方允許管道、依下載配額取得的音樂可商用；付費說明明列遊戲用途及獨立販售。單憑模型版本或資料夾名稱不能證明取得許可。
- 條款保留第三方權利、平台規範及特定 Remix 限制，並未提供著作權或不侵權保證。
- 本專案允許下游下載、再利用及商用，是依作者可授權範圍作出的許可；不是聲稱 Suno 明文保證所有下游素材庫或再授權情境。對有疑義的獨立素材散布用途，宜向 Suno 取得書面確認。
- 不得為隱瞞來源或方案而移除 Suno 標記，不應用於條款禁止的 AI 訓練或競爭服務，也不得暗示 Suno 背書。

維護者應保存歌曲連結／ID、生成與下載日期、方案及下載憑證；目前未附完整逐曲憑證。不得將他人未授權音訊、人聲或 Remix 混入本許可。既有舊驗證報告不作為新曲權利證明。

官方補充：[付費方案權利](https://help.suno.com/en/articles/9601665)、[2026 年下載與條款更新](https://suno.com/blog/suno-updates-tos)。較舊說明仍有以生成時方案為準的文字；本次核對採現行生效條款，不能把新說明當作所有舊音檔自動取得許可的證明。

Sango 不主張對純 AI 產出部分具有當然的排他著作權。收到具體權利爭議、侵權通知或來源無法佐證時，維護者應優先評估下架或替換相關音檔。

本聲明僅說明來源與風險管理政策，不構成法律意見、權利擔保、授權保證或對第三方權利主張的豁免。使用或再散布者仍須遵守加入曲目當時有效的 Suno 條款、所在地法律及發行平台規範。

## LWJGL3 StartupHelper

`lwjgl3/src/main/java/idv/kuan/studio/sango/lwjgl3/StartupHelper.java` 保留原有 Apache License 2.0 copyright 與 license header。

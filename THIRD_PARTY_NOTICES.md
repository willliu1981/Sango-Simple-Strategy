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

本測試版所附六首 BGM，僅供測試其在本遊戲中的播放、音量平衡與場景搭配效果。此用途說明僅針對這六首音樂，不變更原始碼及其他內容的授權；測試用途不取代適用的 Suno 官方條款及授權。

作者提供的 Suno 個人頁：[willliu1981](https://suno.com/@willliu1981)。此連結作為作者來源入口，不是逐曲權利證明，亦不將該頁其他作品納入本專案授權。

依作者於 2026-09-13 的確認，0.6.3 使用的六首 BGM 為 Suno Pro 方案正式下載（Music generated with Suno），不是上述程序合成音樂。曲名、檔案與曲長見 [音樂清單](assets/audio/README.md)。此來源聲明依作者提供的資訊記錄，並非本專案已獨立稽核其帳戶或下載收據。

作者於 2026-09-17 補充確認，相關原曲由自己的帳號於 Pro 期間生成，涉及 Remix 的版本亦由自己於 Pro 期間製作，並非取用其他使用者的歌曲進行 Remix。此補充仍屬作者提供的來源資訊，未附完整逐曲憑證。

**2026-09-17 說明更正：**本專案保留六首音樂作為遊戲 BGM，但暫不另行提供其第三方商用、獨立再散布或再授權許可。先前概括允許下游再利用的文字已更正，相關範圍仍待確認；這不代表 Suno 一律禁止自製 Remix 商用。商用衍生版本請替換音樂或另行取得適用授權；已依舊版說明使用者，建議聯絡維護者釐清範圍，詳見 [使用授權](LICENSE.md)。歷史版本文件中的免費方案聲明保留為當時紀錄，不作為本次六首 BGM 的權利證明。

### 條款核對

查閱日期：2026-09-17；[Suno 現行條款](https://suno.com/terms) 標示於 2026-09-03 生效。

- [付費方案權利說明](https://help.suno.com/en/articles/9601665) 列出遊戲、影視及歌曲銷售等用途；仍須符合正式條款及官方下載條件。單憑 Pro 身分、模型版本或資料夾名稱不能證明所有使用情境均已獲許可。
- [Remix 營利說明](https://help.suno.com/en/articles/9604993) 區分原曲來源：自己在付費期間製作的原曲，再由自己於付費期間 Remix，具營利資格；Remix 他人的歌曲或從免費方案原曲衍生，不能套用此資格。正式條款的 Remixes 段落以允許其他使用者改作及共同權利的情境定義，不能僅因使用 Remix 功能，就將自製版本一概判定為不得商用。
- 作者自己使用音樂，與將音檔作為公開素材提供第三方商用或再授權，是不同的權利問題。目前查閱的官方說明未直接釐清本專案擬提供的完整下游授權範圍，因此暫不提供此項許可，後續宜向 Suno 取得書面確認；這不等同認定官方全面禁止第三方授權。
- 條款保留第三方權利及發行平台限制，並未提供著作權或不侵權保證。
- 不得為隱瞞來源或方案而移除 Suno 標記，不應用於條款禁止的 AI 訓練或競爭服務，也不得暗示 Suno 背書。

維護者應保存原曲與 Remix 的歌曲連結／ID、生成與下載日期、方案及下載憑證；目前未附完整逐曲憑證。不得將他人未授權音訊、人聲或 Remix 視為已取得使用權。既有舊驗證報告不作為新曲權利證明。

本專案摘要不擴張或取代官方授權。實際範圍以適用於各曲的 Suno 官方服務條款、相關功能規則及適用的書面授權為準；如官方頁面間的文字或版本適用有疑義，應向 Suno 確認，不自行推定取得更廣泛權利，也不將新說明視為所有舊音檔自動取得許可的證明。

Sango 不主張對純 AI 產出部分具有當然的排他著作權。收到具體權利爭議、侵權通知或來源無法佐證時，維護者應優先評估下架或替換相關音檔。

本聲明僅說明來源與風險管理政策，不構成法律意見、權利擔保、授權保證或對第三方權利主張的豁免。使用或再散布者仍須確認各曲生成、下載及使用行為適用的 Suno 條款與授權，並遵守所在地法律及發行平台規範。

## LWJGL3 StartupHelper

`lwjgl3/src/main/java/idv/kuan/studio/sango/lwjgl3/StartupHelper.java` 保留原有 Apache License 2.0 copyright 與 license header。

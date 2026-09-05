# Sango

Java 17 + LibGDX 的輕量三國策略遊戲。`0.2.0` 將原本的 Lobby 流程驗證頁替換成第一條真正可玩的 Vertical Slice。

```text
Lobby
→ 建立新局
→ 選擇劇本勢力
→ 單城內政
→ 結束回合
→ 自動存檔
→ 返回 Lobby
→ 繼續遊戲
```

## 0.2.0 已完成

- SimpleUI 1.1.2：XML 定義畫面，Java 管理事件與狀態。
- 一個資料驅動劇本：`prototype_warlords`。
- 三個可選勢力：曹操軍、劉備軍、孫策軍。
- `ScenarioDefinition`、`FactionDefinition`、`CityDefinition` 與可變 `GameState` 分離。
- `NewGameCommand` 建立並驗證新局。
- 第一張 `CityScreen`：顯示勢力、主城、資源與本回合行動力。
- 四種確定性內政命令：開墾、商業、徵兵、訓練。
- `EndTurnCommand`：推進一個月份並恢復行動力。
- 每次成功命令立即自動存檔，另保留手動存檔按鈕。
- `LocalJsonSaveGameRepository`：temp 驗證、前版備份、主要檔損壞時的復原候選讀取。
- Lobby 依存檔狀態啟用「繼續遊戲」，損壞存檔不會使畫面啟動失敗。
- 新局覆寫前確認，避免無提示取代既有進度。
- Desktop `ESC` 與 Android Back 導覽。
- 內建無 Graphics Context 的 Vertical Slice smoke test。

「讀取存檔」目前仍保持 disabled；第一版只使用 slot 1，等多槽位畫面完成後再啟用。

## 第一次執行前：準備中文字型

此 Source ZIP 不重複攜帶 StudyRoutine 已有的兩個大型 Source Han Sans 字型二進位檔。Windows PowerShell：

```powershell
.\tools\prepare-local-fonts.ps1 -StudyRoutineSource "C:\path\to\StudyRoutineCurated-source.zip"
```

參數也可指向解壓後的 StudyRoutine 專案目錄。若 `StudyRoutineCurated-source.zip` 位於 Sango 專案上一層，可直接執行：

```powershell
.\tools\prepare-local-fonts.ps1
```

腳本會準備：

```text
assets/font/SourceHanSansCN-Regular.otf
assets/font/SourceHanSansCN-Heavy.otf
```

先驗證字型：

```powershell
.\gradlew.bat verifyRequiredLocalFonts
```

## 執行

Desktop：

```powershell
.\gradlew.bat lwjgl3:run
```

Android Debug APK：

```powershell
.\gradlew.bat android:assembleDebug
```

完整驗證：

```powershell
.\gradlew.bat clean core:check core:compileJava lwjgl3:dist
```

單獨執行核心流程 smoke test：

```powershell
.\gradlew.bat core:runVerticalSliceSmokeTest
```

## 原型規則

每回合有 3 點行動力；成功命令均消耗 1 點。

| 命令 | 成本 | 結果 |
|---|---:|---:|
| 開墾 | 金 50 | 糧 +200、農業 +5 |
| 商業 | 無 | 金 +150、商業 +5 |
| 徵兵 | 金 100、糧 100、人口 200 | 兵力 +200 |
| 訓練 | 金 50 | 訓練 +5 |

結束回合會增加回合數、推進一個月份，並將行動力恢復到 3。此階段尚未加入 AI 勢力結算或被動收支。

## 存檔位置

- Desktop：使用 LibGDX external storage，預設為使用者家目錄下的 `.sango/save/slot-01.json`。
- Android：使用 App private local storage 的 `save/slot-01.json`。

同一目錄可能存在：

```text
slot-01.json
slot-01.tmp
slot-01.backup.json
slot-01.corrupt.json
```

正常完成寫入後，`.tmp` 會被移除。主要檔案損壞時，Repository 會嘗試讀取完整的 `.tmp` 或 `.backup.json`。

## 主要結構

```text
assets/
├─ data/
│  ├─ scenarios/scenarios.json
│  ├─ factions/factions.json
│  └─ cities/cities.json
├─ i18n/ui_zh_Hant.xml
└─ ui/
   ├─ lobby.xml
   ├─ new_game.xml
   └─ city.xml

core/src/main/java/idv/kuan/studio/sango/
├─ application/
│  ├─ command/
│  ├─ request/
│  └─ result/
├─ domain/
│  ├─ definition/
│  ├─ model/
│  └─ rule/
├─ repository/
│  ├─ definition/
│  └─ save/
├─ runtime/
└─ ui/
```

設計細節見 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

## 從 0.1.3 更新

`PrototypeCampaignScreen` 與 `assets/ui/prototype_campaign.xml` 已移除。若以覆蓋檔案方式更新既有 working tree，請依 [`docs/UPGRADE_0.1.3_TO_0.2.0.md`](docs/UPGRADE_0.1.3_TO_0.2.0.md) 刪除舊檔。

舊版 `Preferences` 中的 `prototypeCampaignExists` 只是假流程旗標，沒有可轉換的戰局資料，因此不遷移。第一次啟動 0.2.0 時需建立新局。

## GdxTools

目前沒有把 GdxTools 綁入 Sango。StudyRoutine snapshot 的 GdxTools 仍是 `../../api/GdxTools/core` 外部本機 module，直接引用會使 Source ZIP 與 CI 無法獨立建置。此版先以小型 `SaveGameRepository` 介面與 LibGDX `Json` adapter 完成存檔；日後 GdxTools 有可攜式 Maven artifact 或完整 module 時，可只替換 Repository adapter，不必讓 Domain 或 Screen 直接依賴第三方 CRUD API。

## 第三方與授權

詳見 `THIRD_PARTY_NOTICES.md`。SimpleUI snapshot 未附授權檔；對外散布前應確認授權。

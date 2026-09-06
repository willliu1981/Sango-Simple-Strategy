# Sango

Java 17 + LibGDX 的輕量三國策略遊戲原型。UI 沿用 StudyRoutine 已驗證的 SimpleUI 1.1.2、XML 畫面定義、i18n 與 Screen registry，但 Domain、Application、Repository 與遊戲資產維持 Sango 自己的邊界。

`0.3.0` 將 `0.2.0` 的單城內政流程擴充成第一個具備地理、時間壓力與戰爭結果的 Strategic Vertical Slice：

```text
Lobby
→ 建立新局／繼續遊戲
→ 六城區域戰略地圖
→ 選城進行內政、治水、徵兵與整備
→ 偵察／選擇戰術／向相鄰城池出征
→ 月底軍糧、季末商稅、六月汛期、九月秋收
→ 敵軍集結與進攻
→ 十二個月內攻下指定敵城，或因主城失守／逾期敗北
```

## 0.3.0 已完成

- 六城節點式 `StrategicMapScreen`，以 Definition 提供節點位置、道路與行軍月份。
- 一個 12 個月 Micro Campaign，曹操軍、劉備軍與孫策軍各有不同起始城及指定目標城。
- 農業與商業改為**投資能力值**：
  - 開墾支付金並提高農業，不再立即增加糧食。
  - 商業開發支付金並提高商業，不再立即增加金錢。
- 每季末（3、6、9、12 月）依城池商業與人口收取商稅。
- 每年 9 月依農業、人口及當年洪災影響進行秋收。
- 新增治水：降低 6 月洪災機率及洪災造成的秋收損失。
- 新增修築城防，影響簡化攻城戰的守方強度。
- 每月依勢力總兵力支出軍糧；糧食不足會造成逃兵。
- 偵察相鄰非我方城池，精確情報維持三個回合。
- 三種戰術：穩健、強攻、保守。
- 玩家與敵軍皆可建立一支野戰軍，沿相鄰道路行軍並自動結算攻城。
- 敵方最小 AI：倒數集結、兵力不足時補兵、向相鄰玩家城池出征。
- 月底集中顯示結算報告：軍糧、商稅、洪災、秋收、行軍、戰鬥與勝敗。
- 勝利條件：期限內攻下指定敵城。
- 失敗條件：玩家主城失守，或 12 個月期限到期。
- 所有成功命令均採 copy-on-write，驗證及存檔成功後才更新 `GameSession`。
- `LocalJsonSaveGameRepository` 保留 temp 驗證、backup、corrupt 保存及復原候選讀取。
- 無 Graphics Context 的 `VerticalSliceSmokeTest` 覆蓋季節經濟、洪災、偵察、出征、攻防、勝敗與存檔復原。

「讀取存檔」目前仍保持 disabled；Prototype 只使用 slot 1，等多槽位 UI 完成後再啟用。

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

完整核心驗證：

```powershell
.\gradlew.bat clean core:check core:compileJava lwjgl3:dist
```

單獨執行 Strategic Vertical Slice smoke test：

```powershell
.\gradlew.bat core:runVerticalSliceSmokeTest
```

## 原型規則

### 時間

- 1 回合＝1 個月。
- 每月 3 點行動力。
- 成功的內政、偵察與出征命令各消耗 1 點行動力。
- 結束本月後，依固定順序處理軍糧、季節事件、行軍、戰鬥、敵方 AI，再推進月份並自動存檔。

### 內政

| 命令 | 當下成本 | 當下效果 | 延後效果 |
|---|---:|---:|---:|
| 開墾 | 金 50 | 農業 +5 | 9 月秋收增加 |
| 商業開發 | 金 50 | 商業 +5 | 每季末商稅增加 |
| 治水 | 金 80 | 治水 +5 | 洪災機率與秋收損失下降 |
| 修築城防 | 金 100 | 城防 +5 | 守城強度提高 |
| 徵兵 | 金 100、糧 100、人口 200 | 兵力 +200 | 每月軍糧支出提高 |
| 訓練 | 金 50 | 訓練 +5 | 城內守軍及之後派出的軍隊更強 |

目前能力值上限為 100。固定數值仍是 Prototype Parameter，尚未移至獨立 Rule Definition JSON。

### 季節經濟與汛期

- 商稅：3、6、9、12 月月底結算。
- 汛期：6 月月底依各城治水值進行 deterministic flood roll。
- 秋收：9 月月底結算。
- 洪災可能降低人口、農業、民心與當年秋收倍率。
- 軍糧：每月依勢力所有城池守軍與行軍部隊總兵力結算。

### 地圖與戰爭

- 地圖共有 6 座城、6 條雙向道路。
- 偵察和出征只能針對與我方城池直接相鄰的非我方城池。
- 出征消耗糧 100，必須至少保留 400 守軍並至少派出 400 兵；單次最多派出 1,000 兵。
- 第一版每個勢力同時只能有一支行軍部隊。
- 戰鬥採簡化 Auto-resolve：兵力、訓練、城防及戰術共同決定結果與損失。
- 敵軍會依倒數集結並主動攻打相鄰玩家城池，不會無限等待玩家開發。

## 存檔位置與相容性

- Desktop：LibGDX external storage，預設為 `%USERPROFILE%\.sango\save\slot-01.json`。
- Android：App private local storage 的 `save/slot-01.json`。

同一目錄可能存在：

```text
slot-01.json
slot-01.tmp
slot-01.backup.json
slot-01.corrupt.json
```

正常完成寫入後，`.tmp` 會被移除。主要檔案損壞時，Repository 會嘗試讀取完整的 `.tmp` 或 `.backup.json`。

`0.3.0` 將 `GameState.schemaVersion` 提升為 2，**不直接讀取 0.2.0 schema 1 存檔**。目前尚未實作 migration；升級後請建立新局。詳見 [`docs/UPGRADE_0.2.0_TO_0.3.0.md`](docs/UPGRADE_0.2.0_TO_0.3.0.md)。

## 主要結構

```text
assets/
├─ data/
│  ├─ scenarios/scenarios.json
│  ├─ factions/factions.json
│  ├─ cities/cities.json
│  └─ maps/maps.json
├─ i18n/ui_zh_Hant.xml
└─ ui/
   ├─ lobby.xml
   ├─ new_game.xml
   ├─ strategic_map.xml
   └─ city.xml

core/src/main/java/idv/kuan/studio/sango/
├─ application/
│  ├─ command/
│  ├─ request/
│  └─ result/
├─ domain/
│  ├─ definition/
│  ├─ model/
│  ├─ rule/
│  └─ service/
├─ repository/
│  ├─ definition/
│  └─ save/
├─ runtime/
└─ ui/
   ├─ widget/
   ├─ theme/
   └─ support/
```

設計細節見 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)，驗證結果見 [`docs/VALIDATION.md`](docs/VALIDATION.md)。

## GdxTools

目前沒有把 GdxTools 綁入 Sango。StudyRoutine snapshot 的 GdxTools 仍是 `../../api/GdxTools/core` 外部本機 module，直接引用會使 Source ZIP 與 CI 無法獨立建置。此版以小型 `SaveGameRepository` 介面與 LibGDX `Json` adapter 完成存檔；日後 GdxTools 有可攜式 Maven artifact 或完整 module 時，可只替換 Repository adapter，不必讓 Domain 或 Screen 直接依賴第三方 CRUD API。

## 第三方與授權

詳見 `THIRD_PARTY_NOTICES.md`。SimpleUI snapshot 未附授權檔；對外散布前應確認授權。

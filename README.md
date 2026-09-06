# Sango

Java 17 + LibGDX 的輕量三國策略遊戲原型。UI 沿用 StudyRoutine 已驗證的 SimpleUI 1.1.2、XML 畫面定義、i18n 與 Screen registry；Domain、Application、Repository、存檔與遊戲資產維持 Sango 自己的邊界。

`0.4.0` 集中完善 `0.3.0` 的戰爭回饋與遊戲流程：戰鬥不再只是一行月底文字，讀取存檔正式可用，劇本目標失敗後也不會鎖死戰局。

```text
Lobby
→ 建立新局／繼續遊戲／讀取三個存檔槽
→ 六城區域戰略地圖
→ 選城內政、偵察、選擇戰術與出征
→ 確認結束本月
→ 軍糧、季節經濟、行軍、戰鬥與敵軍 AI 結算
→ 月報與可持久化戰報
→ 目標達成／失敗後進入自由征戰
```

## 0.4.0 已完成

### 戰報與地圖戰事提示

- 每場戰鬥建立可保存的 `BattleReport`，記錄雙方兵力、訓練、城防、戰術、傷亡、勝方與城池控制權。
- 戰報有獨立畫面，不再只依賴月底報告中的單行文字。
- 若玩家在地圖或內政畫面結束本月，且當前選取／管理的城池發生戰鬥，會先詢問是否立即觀看。
- 選擇稍後觀看時，戰報保留為未讀。
- 非當前城池的未讀戰報會顯示在戰略地圖節點上，節點以「戰事 N」與脈衝效果提示。
- 地圖可查看所選城池的未讀戰報，也可直接開啟第一份未讀戰報。
- 戰報已讀狀態會寫回目前存檔；重新啟動後不會遺失。

### 劇本目標與自由征戰

`0.3.0` 將期限失敗與主城失守視為整局終止，現在已改為分離兩種狀態：

```text
ScenarioObjectiveStatus
- IN_PROGRESS
- ACHIEVED
- FAILED

GameplayStatus
- ACTIVE
- ELIMINATED
```

- 十二個月內攻下指定城池仍是原型劇本目標。
- 期限到期只會將目標標記為失敗，玩家仍可內政、結束月份、偵察與出征。
- 達成原目標後也可繼續自由征戰。
- 原首都失守但仍擁有其他城池時，會遷移首都並繼續遊戲。
- 只有失去全部城池時才進入 `ELIMINATED`，此時停止下達新命令。

### 三槽存讀檔

- Lobby 的「讀取存檔」已正式啟用。
- 新增三個存檔槽，顯示勢力、首都、年月、回合、城池數、目標狀態與保存時間。
- 「繼續遊戲」讀取最後使用且仍有效的槽位；若該槽位失效，會尋找其他有效槽位。
- 新局可指定寫入槽位，覆寫既有進度前必須確認。
- 存讀檔畫面支援：讀取、另存、覆寫確認與刪除確認。
- 主要檔案損壞時，槽位會標示可使用 `.tmp` 或 `.backup.json` 的復原候選。

### 月底流程與設定

- 戰略地圖與城池內政都提供「結束本月」。
- 結束前會顯示確認視窗、目前年月與剩餘行動力；未使用行動力不會保留。
- 地圖與內政共用 `MonthEndFlowController`，避免兩套結算流程分歧。
- 月份結算後開啟獨立月報畫面；若當前城池發生戰鬥，先顯示觀看戰報提示。
- 地圖與內政的直接「返回 Lobby」已移除。
- 新增全域設定／暫停畫面，集中提供：
  - 背景音樂開關與音量。
  - 遊戲音效開關與音量。
  - 儲存遊戲。
  - 保存並返回 Lobby。
  - 保存並退出遊戲。
- Desktop `ESC`／Android Back：
  - 城池內政返回戰略地圖。
  - 戰略地圖開啟設定。
  - Dialog／戰報／月報優先關閉或返回上一層。

### BGM 與音效

- Lobby 與 Strategy 各有一首可循環 OGG BGM。
- 加入介面、確認、取消、命令成功／失敗、保存、月底、戰鬥、攻城與目標結果音效。
- `SangoAudioService` 集中快取、播放與釋放 LibGDX `Music`／`Sound`，Screen 不直接管理音訊生命週期。
- Android／Desktop pause、resume、dispose 已接入共用 Application lifecycle。
- 音樂與音效開關、音量會保存於 `SangoPreferences`。
- 音訊由 `tools/generate-prototype-audio.py` 以基本波形、五聲音階與程序節奏原創合成，不含第三方遊戲、影視或商業曲目取樣。
- 這批素材定位為功能驗證用 Prototype Audio，不等同正式商業配樂或專業 Foley。

## 第一次執行前：準備中文字型

Source ZIP 不重複攜帶 StudyRoutine 已有的兩個大型 Source Han Sans 字型二進位檔。Windows PowerShell：

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

## 執行與驗證

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
.\gradlew.bat --stop
.\gradlew.bat clean core:check core:compileJava lwjgl3:dist
```

單獨執行 Vertical Slice smoke test：

```powershell
.\gradlew.bat core:runVerticalSliceSmokeTest
```

## 原型規則

### 時間與月底順序

- 1 回合＝1 個月。
- 每月 3 點行動力。
- 成功的內政、偵察與出征命令各消耗 1 點行動力。
- 結束本月後依固定順序處理：

```text
1. 全勢力軍糧與缺糧逃兵
2. 六月洪災
3. 季末商稅
4. 九月秋收
5. 已在途軍隊移動與抵達戰鬥
6. 敵方 AI 集結、補兵或建立新軍隊
7. 劇本期限判定
8. 月份與回合推進
9. 行動力恢復並自動存檔
```

### 內政

| 命令 | 當下成本 | 當下效果 | 延後效果 |
|---|---:|---:|---:|
| 開墾 | 金 50 | 農業 +5 | 9 月秋收增加 |
| 商業開發 | 金 50 | 商業 +5 | 每季末商稅增加 |
| 治水 | 金 80 | 治水 +5 | 洪災機率與秋收損失下降 |
| 修築城防 | 金 100 | 城防 +5 | 守城強度提高 |
| 徵兵 | 金 100、糧 100、人口 200 | 兵力 +200 | 每月軍糧支出提高 |
| 訓練 | 金 50 | 訓練 +5 | 守軍與之後派出的軍隊更強 |

目前能力值上限為 100。固定數值仍是 Prototype Parameter，尚未移至獨立 Rule Definition JSON。

### 季節經濟與戰爭

- 商稅：3、6、9、12 月月底結算。
- 汛期：6 月月底依治水值進行 deterministic flood roll。
- 秋收：9 月月底結算。
- 每月依勢力守軍與行軍部隊總兵力支出軍糧。
- 地圖共有 6 座城與 6 條雙向道路。
- 偵察／出征只能針對與我方城池直接相鄰的非我方城池。
- 出征消耗糧 100，必須至少保留 400 守軍並至少派出 400 兵；單次最多派出 1,000 兵。
- 每個勢力目前同時只能有一支行軍部隊。
- 戰鬥採簡化 Auto-resolve，由兵力、訓練、城防與戰術共同決定結果及損失。

## 存檔位置與相容性

Desktop 預設位置：

```text
%USERPROFILE%\.sango\save\
```

Android 使用 App private local storage：

```text
save/
```

三個槽位各可能包含：

```text
slot-01.json
slot-01.tmp
slot-01.backup.json
slot-01.corrupt.json

slot-02.*
slot-03.*
```

正常完成寫入後 `.tmp` 會被移除。Repository 讀取順序為：

```text
primary → tmp → backup
```

`0.4.0` 使用：

```text
SaveGameDocument.schemaVersion = 1
GameState.schemaVersion = 3
```

`0.3.0` 的 `GameState.schemaVersion = 2` 可在讀取時自動遷移至 schema 3；下一次保存會正式寫回新格式。`0.2.0` 的 schema 1 仍不支援自動遷移。詳見 [`docs/UPGRADE_0.3.0_TO_0.4.0.md`](docs/UPGRADE_0.3.0_TO_0.4.0.md)。

## 主要結構

```text
assets/
├─ audio/
│  ├─ music/
│  └─ sfx/
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
   ├─ city.xml
   ├─ month_report.xml
   ├─ battle_report.xml
   ├─ save_load.xml
   └─ settings.xml

core/src/main/java/idv/kuan/studio/sango/
├─ application/
├─ audio/
├─ domain/
├─ repository/
├─ runtime/
└─ ui/
   ├─ flow/
   ├─ support/
   ├─ theme/
   └─ widget/
```

設計細節見 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)，驗證範圍見 [`docs/VALIDATION.md`](docs/VALIDATION.md)。

## 目前限制

- 戰鬥仍是 Auto-resolve，尚未建立 Tactical Combat。
- 同一勢力同時只能有一支野戰軍。
- 六城地圖仍是流程驗證用 Region Map，不是完整中國地圖。
- Prototype Audio 可驗證切換、音量與事件回饋，但不代表正式配樂品質。
- 存檔沒有雲端同步，也沒有跨裝置衝突解決。

## GdxTools

目前沒有把 GdxTools 綁入 Sango。StudyRoutine snapshot 的 GdxTools 仍是 `../../api/GdxTools/core` 外部本機 module，直接引用會使 Source ZIP 與 CI 無法獨立建置。此版以小型 `SaveGameRepository` 介面與 LibGDX `Json` adapter 完成存檔；日後 GdxTools 有可攜式 Maven artifact 或完整 module 時，可只替換 Repository adapter，不必讓 Domain 或 Screen 直接依賴第三方 CRUD API。

## 第三方與授權

詳見 `THIRD_PARTY_NOTICES.md`。SimpleUI snapshot 未附授權檔；對外散布前必須確認適用授權。

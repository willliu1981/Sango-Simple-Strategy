# Sango 0.5.0 Architecture

## 0.5.0 新增責任

- `FactionPlacementDefinition`：新劇本的固定初始所有權；不存在時保留舊六城建立流程。
- `CityState.morale` / `CityDefinition.initialMorale`：可變士氣與固定初值分離。
- `MilitaryRules`：純 Java 攻守戰力、士氣倍率與部隊品質加權。
- `BattleResolutionService`：有守軍戰鬥、無抵抗佔領、遷都與滅亡；保存事件快照而非 Definition 物件。
- `EnemyTurnService`：多勢力前線選擇與實際資源支出，不再只驅動單一黃巾勢力。
- `GameStateMigrator`：schema 2 → 3 → 4，保留舊地圖與歷史戰報。
- `MapCameraState`：純 Java 相機數學；`StrategicMapWidget` 處理 Scene2D 手勢與裁切，不編輯 GameState。
- `MonthReportScreen` 的 ScrollPane 只處理長月報呈現，不搬入 Domain 邏輯。

目前存檔為 GameState schema 4；下方 0.3／0.4 的敘述保留作設計沿革。當前規則以 `RULES_0.5.0.md` 為準。


## 分層與依賴方向

```text
SimpleUI XML / Screen / Widget
        ↓ Request、ID、Action Type
Application Command / UI Flow Controller
        ↓
Domain Model / Rule / Service
        ↓ interface
Repository Adapter

Application lifecycle
        ↓
SangoAudioService
```

- UI 負責呈現、選取、確認與導航，不直接修改 `GameState`。
- Application Command 負責 use case 邊界、copy-on-write、驗證與保存。
- `MonthEndFlowController` 統一地圖與城池的月底執行入口。
- Domain Rule／Service 負責可測試的經濟、行軍、AI、戰鬥與目標狀態。
- Repository interface 位於核心邊界；LibGDX `Json` 與 `FileHandle` 留在 adapter。
- `SangoAudioService` 集中管理 `Music`／`Sound`，Screen 只引用語意化 enum。

## Definition 與 State

固定內容從 `assets/data` 載入：

```text
ScenarioDefinition
FactionDefinition
CityDefinition
StrategicMapDefinition
├─ MapCityNodeDefinition
└─ CityConnectionDefinition
```

可變進度保存於：

```text
GameState
├─ FactionState[]
├─ CityState[]
├─ ArmyState[]
└─ BattleReport[]
```

存檔只保存 Definition ID 與可變數值，不嵌入整份劇本／城市／勢力 Definition。修改顯示名稱、地圖座標或說明文字時，不需要同步複製到每一份存檔。

## 劇本目標與 Gameplay 狀態

`0.3.0` 的單一 `CampaignStatus` 同時表示「目標結果」與「能否繼續遊玩」，導致逾期敗北後行動力鎖為 0。`0.4.0` 將其拆為：

```text
ScenarioObjectiveStatus
- IN_PROGRESS
- ACHIEVED
- FAILED

GameplayStatus
- ACTIVE
- ELIMINATED
```

規則：

- 指定目標達成：`ACHIEVED + ACTIVE`，進入自由征戰。
- 期限到期：`FAILED + ACTIVE`，進入自由征戰。
- 原首都失守但仍有其他城池：重新指派首都，維持 `ACTIVE`。
- 失去全部城池：`ELIMINATED`，停止下達命令與恢復行動力。

`CampaignStatus` 僅保留為 schema 2 遷移欄位；schema 3 新存檔應為 `null`。

## 命令與 copy-on-write

下列命令不直接修改傳入狀態：

```text
ExecuteDomesticActionCommand
ScoutCityCommand
LaunchExpeditionCommand
EndTurnCommand
MarkBattleReportReadCommand
```

主要流程：

```text
驗證 current GameState
→ 建立深複本
→ 套用命令／結算
→ 驗證 next GameState
→ SaveGameRepository.save(slot, nextState)
→ 保存成功後才回傳 nextState
→ UI 更新 GameSession
```

因此磁碟寫入失敗時，記憶體中的戰局不會先被改壞。`SaveCurrentGameCommand` 則用於明確保存目前狀態與另存槽位。

## 月底流程

地圖與城池都透過 `MonthEndFlowController` 呼叫同一個 `EndTurnCommand`：

```text
使用者按下結束本月
→ 畫面顯示確認 Dialog
→ MonthEndFlowController.endCurrentMonth()
→ EndTurnCommand
→ TurnResolutionService
→ 保存新的 GameState
→ GameSession 更新狀態與 TurnResolutionReport
→ 若當前城池有戰鬥，先顯示觀看提示
→ MonthReportScreen / BattleReportScreen
```

`TurnResolutionService` 使用固定順序：

```text
1. 全勢力軍糧支出與缺糧逃兵
2. 六月洪災判定
3. 季末商稅
4. 九月秋收
5. 既有軍隊移動與抵達戰鬥
6. EnemyTurnService 集結／補兵／建立新軍隊
7. 劇本期限判定
8. 月份與回合推進
9. ACTIVE 勢力恢復行動力
```

敵軍在本月底建立的軍隊不會同月立刻抵達，而是在後續月份的 Army Movement 階段前進。

## BattleReport

`BattleResolutionService` 在每次抵達戰鬥時建立 `BattleReport`：

```text
battleId
resolvedTurn / resolvedYear / resolvedMonth
originCityId / targetCityId
attackerFactionId / defenderFactionId
attackerTactic
雙方開戰兵力、訓練、守方城防
雙方損失與生還者
BattleOutcome
cityCaptured / winnerFactionId
read
```

報告加入 `GameState.battleReports`，因此可隨存檔持久化。`TurnResolutionReport` 同時保存本月底新增的 Battle Report ID，UI 可分辨「本月戰鬥」與歷史未讀戰報。

呈現流程：

- 當前選取／管理城池發生本月戰鬥：結算後先詢問是否查看。
- 其他城池戰鬥：保留未讀，`StrategicMapWidget` 在對應節點顯示 `戰事 N` 與脈衝。
- `BattleReportScreen` 開啟後透過 `MarkBattleReportReadCommand` 保存已讀狀態。
- 可從戰報直接切換至下一份未讀戰報，或返回原呼叫畫面。

## Strategic Map

`StrategicMapDefinition` 使用 `0.0～1.0` 正規化座標描述節點。`StrategicMapWidget` 依容器大小放置 Scene2D `TextButton`，並以旋轉的 `Image` drawable 繪製道路。

```text
maps.json
→ AssetJsonGameDefinitionRepository
→ StrategicMapScreen
→ StrategicMapWidget
```

Widget 不持有 `GameState`；畫面 refresh 時傳入 caption、tone、選取狀態與未讀戰報數。這可讓 Domain adjacency model 與視覺呈現保持分離。

## Runtime container 與導航狀態

SimpleUI Screen registry 使用無參數 factory，因此 `SangoServices` 提供：

- `GameDefinitionRepository`。
- `SaveGameRepository`。
- `GameSession`。
- Application Commands。
- `SangoAudioService`。

`GameSession` 保存：

- 目前 `GameState` 與對應槽位。
- 目前選取城池與戰術。
- 最近月報。
- 目前戰報 ID 與返回畫面。
- Settings、SaveLoad、MonthReport 的返回畫面。
- SaveLoad 模式（LOAD／SAVE）。

這些屬於執行期 UI 導航資訊，不混入 Domain 存檔；戰報內容與已讀狀態則屬於可持久化遊戲狀態。

## 三槽存讀檔

UI 目前公開 3 個槽位；Repository 介面仍接受一般槽位參數，adapter 允許 1～99。

`SaveLoadScreen` 支援：

```text
LOAD
- 查看三槽摘要
- 載入有效槽位
- 刪除確認

SAVE
- 保存至空槽
- 覆寫既有槽位前確認
- 刪除確認
```

`SaveSlotMetadata` 只抽取清單需要的資料，不直接把完整 `GameState` 暴露給槽位 UI。

`LocalJsonSaveGameRepository.save()`：

```text
1. 將新 SaveGameDocument 寫入 slot-NN.tmp
2. 重新解析並驗證 tmp
3. 若現有 primary 合法，複製為 slot-NN.backup.json
4. 若現有 primary 損壞，保存為 slot-NN.corrupt.json
5. 以已驗證的 tmp 取代 primary
6. 再次解析 primary
```

讀取順序：

```text
primary → tmp → backup
```

若 primary 無效但 tmp 或 backup 合法，`inspect()` 會回傳 `recoveryCandidate=true`，供 UI 標示復原候選。

## Schema migration

外層與遊戲狀態版本分開：

```text
SaveGameDocument.schemaVersion = 1
GameState.schemaVersion = 3
```

`GameStateMigrator` 支援 schema 2 → 3：

- 將舊 `CampaignStatus` 映射成 `ScenarioObjectiveStatus`。
- 依玩家勢力是否仍 active 設定 `GameplayStatus`。
- 初始化 `BattleReport[]` 與序號。
- 若舊存檔因目標結束而被鎖為 0 行動力，且玩家仍 active，恢復每回合行動力。
- 清除 deprecated `campaignStatus`。

遷移在讀取候選檔案時完成；下一次保存才會把 schema 3 正式寫回磁碟。schema 1（0.2.0）仍不支援，避免用猜測值填入地圖、敵軍與其他 0.3.0 才有的狀態。

## Audio

```text
Screen / Flow
→ MusicTrack / SoundEffect
→ SangoAudioService
→ LibGDX Music / Sound
```

- BGM 與 SFX 以 enum 對應 internal asset path。
- 首次播放時 lazy-load，之後以 `EnumMap` 快取。
- 切換畫面時只指定期望曲目；同曲目不重新建立資源。
- 音樂／音效開關與音量來自 `SangoPreferences`。
- `Main.pause()`、`resume()`、`beforeDispose()` 分別轉交 pause、resume、dispose。
- 音訊載入失敗只記錄錯誤，不讓整個遊戲因單一音檔中止。

目前沒有淡入淡出、混音 bus 或同類 SFX 併發限制；這些屬於正式 Audio Polish 階段。

## 季節與戰爭規則

`SeasonalEconomyRules` 集中保存目前 Prototype 公式：

- 季末商稅。
- 秋收與洪災倍率。
- 每月軍糧支出。
- 洪災機率與損失。

`DeterministicEventRoller` 依劇本、城池、年份與事件類型產生固定結果，使 smoke test、存檔重讀及相同輸入具有可重現性；它不是密碼學亂數。

戰爭流程：

```text
LaunchExpeditionCommand
→ ArmyState
→ TurnResolutionService.resolveArmyMovement()
→ BattleResolutionService.resolveArrival()
→ BattleReport
```

第一版戰鬥仍是 Auto-resolve：攻方兵力、訓練、戰術，對比守方兵力、訓練與城防；結果再更新傷亡、城池控制權、首都與目標狀態。

## 複雜度與瓶頸

Prototype 規模很小：

- 多數 ID 查找仍線性掃描陣列，約 `O(C + A + B)`。
- 月份結算約 `O(F × C + A + B)`。
- `StrategicMapWidget` rebuild 約 `O(C + E)`。
- 尋找未讀戰報約 `O(B)`。

其中 `C` 為城池數、`A` 為軍隊數、`B` 為戰報數、`E` 為道路數。六城地圖下可忽略；長期遊玩會持續累積 `BattleReport[]`，未來應加入歷史分頁、上限或 archive，而不是無限制保留於單一陣列。

## 下一個合理節點

- 戰報增加武將、事件與更完整的視覺演出。
- 出征兵力可選，而非自動派出可用上限。
- 固定經濟／軍事數值移至資料驅動 Rule Definition。
- 加入武將／官員指派，使內政與戰鬥受角色能力影響。
- 多軍隊、跨多段道路、補給線與撤退目的地。
- 正式 Tactical Combat 或更具決策性的 Battle Phase。
- 地圖加入地形、關隘與水路，但保留相同 adjacency model。
- 存檔 metadata 版本化、更多 migration 測試與雲端同步策略。

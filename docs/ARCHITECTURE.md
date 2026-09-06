# Sango 0.3.0 Architecture

## 分層與依賴方向

```text
SimpleUI XML / Screen / Widget
        ↓ Request、ID、Action Type
Application Command
        ↓
Domain Model / Rule / Service
        ↓ interface
Repository Adapter
```

- UI 負責呈現、選取與命令觸發，不直接修改 `GameState`。
- Application Command 負責 use case 邊界、copy-on-write、驗證與保存。
- Domain Rule／Service 負責可測試的經濟、行軍、AI 與戰鬥規則。
- Repository interface 位於核心邊界；LibGDX `Json` 與 `FileHandle` 留在 adapter。

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
└─ ArmyState[]
```

存檔只保存 Definition ID 與可變數值，不嵌入整份劇本／城市／勢力物件。這能避免單純修改顯示名稱、地圖座標或說明文字時重寫所有舊存檔。

## Strategic Map

`StrategicMapDefinition` 使用正規化座標 `0.0～1.0` 描述節點，`StrategicMapWidget` 依容器實際大小放置 Scene2D `TextButton`，並以旋轉的 `Image` drawable 繪製道路。

```text
maps.json
→ AssetJsonGameDefinitionRepository
→ StrategicMapScreen
→ StrategicMapWidget
```

地圖邏輯只使用城池 ID 與道路關係；Widget 不持有 `GameState`，畫面每次 refresh 重新提供 caption、tone 與 selected city。

## 命令與 copy-on-write

下列命令皆不直接修改傳入狀態：

```text
ExecuteDomesticActionCommand
ScoutCityCommand
LaunchExpeditionCommand
EndTurnCommand
```

流程：

```text
驗證 current GameState
→ 建立深複本
→ 套用命令／結算
→ 驗證 next GameState
→ SaveGameRepository.save()
→ 保存成功後才回傳 next GameState
→ UI 才更新 GameSession
```

因此磁碟寫入失敗時，記憶體中的戰局不會先被改壞。

## 月份結算順序

`TurnResolutionService` 使用固定順序，避免同一事件因呼叫順序變動而產生不可預期結果：

```text
1. 全勢力軍糧支出與缺糧逃兵
2. 六月洪災判定
3. 季末商稅
4. 九月秋收
5. 既有軍隊移動與抵達戰鬥
6. EnemyTurnService 集結／補兵／建立新軍隊
7. 期限判定、月份推進、行動力恢復
```

敵軍在本次月底建立的軍隊不會同月立刻抵達，而是在後續月份的 Army Movement 階段前進。

## 季節事件

`SeasonalEconomyRules` 集中保存目前 Prototype 公式：

- 季末商稅。
- 秋收基礎量與洪災倍率。
- 每月軍糧支出。
- 洪災機率與秋收損失。

`DeterministicEventRoller` 依劇本、城池、年份與事件類型產生固定結果，使 smoke test、存檔重讀及相同輸入具有可重現性；它不是密碼學亂數。

## 戰爭

```text
LaunchExpeditionCommand
→ ArmyState（起點、目標、剩餘月份、兵力、訓練、士氣、戰術）
→ TurnResolutionService.resolveArmyMovement()
→ BattleResolutionService.resolveArrival()
```

第一版戰鬥是 Auto-resolve：

- 攻方：兵力、訓練、戰術強度倍率。
- 守方：兵力、訓練、城防。
- 戰術另外調整攻方傷亡。
- 攻方勝利後轉移城池所有權、降低民心與城防，並更新失去主城勢力的首都／active 狀態。
- 玩家攻下指定目標城即勝利；玩家當時的主城遭攻陷即敗北。

## Runtime container

SimpleUI Screen registry 使用無參數 factory，因此 `SangoServices` 建立並提供：

- `GameDefinitionRepository`。
- `SaveGameRepository`。
- `GameSession`。
- Application Commands。
- `TurnResolutionService`。

Domain 與 Command 仍透過 constructor 接收介面；smoke test 可使用獨立測試目錄，不需啟動 Graphics Context。

`GameSession` 只保存目前記憶體中的 `GameState` 與不需寫入存檔的 UI 選取，例如目前城池與戰術。

## 存檔一致性

`LocalJsonSaveGameRepository.save()`：

```text
1. 將新 SaveGameDocument 寫入 slot-01.tmp
2. 重新解析並驗證 tmp
3. 若現有 primary 合法，複製為 slot-01.backup.json
4. 若現有 primary 損壞，保存為 slot-01.corrupt.json
5. 以已驗證的 tmp 取代 primary
6. 再次解析 primary
```

讀取順序：

```text
primary → tmp → backup
```

`SaveGameDocument.schemaVersion` 與 `GameState.schemaVersion` 分開。0.3.0 的外層 document schema 仍為 1，GameState schema 已升為 2。目前不做 schema 1 → 2 自動 migration，避免以猜測值填入地圖、治水、城防、敵軍與 ArmyState。

## 複雜度與目前瓶頸

Prototype 的城池與軍隊數量很小：

- 多數 ID 查找仍線性掃描陣列，時間複雜度約 `O(C + A)`。
- 月份結算約 `O(F × C + A)`。
- `StrategicMapWidget` rebuild 約 `O(C + E)`，其中 `E` 為道路數。

六城地圖下成本可忽略。若未來擴張至數十至數百城與多軍團，應在 runtime 建立 ID index、鄰接表與增量式 Widget 更新，而不是每次 refresh 全量掃描／重建。

## 下一個合理節點

- 讓出征兵力可選，而非自動派出可用上限。
- 將固定經濟與軍事數值移至資料驅動 Rule Definition。
- 加入武將／官員指派，使內政與戰鬥受角色能力影響。
- 多軍隊、跨多段道路、補給線與撤退目的地。
- 多存檔槽、存檔摘要與顯式 schema migration。
- 將節點地圖升級為具地形資訊的 Region Map，但保留相同 Domain adjacency model。

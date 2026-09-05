# Sango 0.2.0 Architecture

## 邊界

```text
UI (SimpleUI Screen)
    ↓ Request / action type
Application Command
    ↓
Domain model and rules
    ↓ interface
Repository adapter
```

UI 不直接修改 `GameState`。內政與結束回合採 copy-on-write：Command 先複製目前狀態、套用規則、驗證、寫入存檔；只有保存成功才把新狀態放回 `GameSession`。因此磁碟寫入失敗時，畫面中的戰局不會先行變更。

## Definition 與 State

`ScenarioDefinition`、`FactionDefinition`、`CityDefinition` 是固定內容，從 `assets/data` 載入。存檔只保存它們的 ID，不把整份 Definition 複製進 JSON。

`GameState` 保存：

- 劇本 ID、玩家勢力 ID。
- 回合、年、月、剩餘行動力。
- `FactionState[]`。
- `CityState[]`。

這樣更新顯示名稱或劇本說明時，不必改寫既有存檔。

## Runtime container

SimpleUI Screen registry 使用無參數 factory，因此 `SangoServices` 負責建立並提供：

- `GameDefinitionRepository`。
- `SaveGameRepository`。
- `GameSession`。
- Application Commands。

Domain 與 Command 仍透過 constructor 接收 Repository 介面；smoke test 可直接傳入獨立測試目錄，不依賴 `SangoServices`。

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

`SaveGameDocument.schemaVersion` 與 `GameState.schemaVersion` 分開，方便未來獨立遷移外層格式與遊戲狀態。

## 下一個合理節點

- 多存檔槽與存檔摘要。
- 將固定命令數值移到 Rule Definition。
- 加入官員／武將指派，讓內政結果受能力值影響。
- 加入第二座城與最小地圖，再做移動或攻擊。
- 為 Definition 與 SaveGame 增加顯式 migration 測試。

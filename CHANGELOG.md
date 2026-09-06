# Changelog

## 0.3.0

- 新增六城節點式區域戰略地圖與道路 Definition。
- 將 Lobby／新局後的 Gameplay 入口改為 `StrategicMapScreen`。
- 修正開墾與商業：兩者均為付費投資，不再立即增加糧或金。
- 新增治水、修築城防及對應城池狀態。
- 新增季末商稅、六月洪災、九月秋收與每月軍糧支出。
- 新增糧食不足造成逃兵的處理。
- 新增相鄰城池偵察及三回合精確情報。
- 新增穩健、強攻、保守三種簡化戰術。
- 新增出征、道路行軍、攻城 Auto-resolve、城池控制權轉移。
- 新增單一敵對勢力 AI：集結、補兵與進攻。
- 新增本月結算報告、戰役勝利、主城失守與期限敗北。
- `GameState.schemaVersion` 提升為 2；0.2.0 存檔目前不自動遷移。
- 擴充 Vertical Slice smoke test，覆蓋季節經濟、洪災、戰爭與存檔復原。

## 0.2.0

- 新增資料驅動的新局勢力選擇畫面。
- 新增單城內政與四種命令。
- 新增結束回合與月份推進。
- 新增正式 JSON SaveGameRepository、備份與復原候選。
- Lobby 的繼續遊戲改由實際存檔狀態決定。
- 移除 `PrototypeCampaignScreen` 與 Preferences 戰局旗標。
- 新增無 Graphics Context 的 Vertical Slice smoke test。
- 快取三個 SimpleUI Screen instance，避免反覆導航重建動態字型與 Stage。
- 補強 Definition 與 GameState 結構驗證。

## 0.1.3

- 修正 SimpleUI TextButton 中文字型被 Skin style 覆寫。

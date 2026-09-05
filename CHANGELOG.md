# Changelog

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

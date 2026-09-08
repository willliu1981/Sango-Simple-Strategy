# Changelog

## 0.4.0

- 新增可持久化的 `BattleReport`、獨立戰報畫面與未讀戰報狀態。
- 當目前管理／選取的城池發生戰鬥時，月底先詢問是否立即觀看戰報。
- 非當前城池的未讀戰事會在戰略地圖節點上顯示數量並閃爍提示。
- 將劇本目標狀態與遊戲可操作狀態分離；期限失敗、目標達成或原首都失守後均可繼續自由征戰。
- 只有失去全部城池時才進入 `GameplayStatus.ELIMINATED`。
- 新增三槽 `SaveLoadScreen`，完成載入、另存、覆寫確認、刪除確認與槽位摘要。
- Lobby 的「繼續遊戲」會使用最後使用的有效槽位，「讀取存檔」正式啟用。
- 新增 `GameState` schema 2 → 3 自動遷移，既有 0.3.0 存檔可繼續使用。
- 內政畫面新增「結束本月」，地圖與內政共用月底確認、結算與月報流程。
- 新增全域設定／暫停畫面；返回 Lobby、儲存遊戲與退出遊戲集中於此。
- 新增原創程序合成的 Lobby／戰略 BGM，以及介面、存檔、月底與戰鬥音效。
- 新增音樂／音效開關與獨立音量設定，並處理 pause、resume 與 dispose。
- Android `versionCode`／`versionName` 與 Desktop Construo 版本更新為 0.4.0。
- 擴充 smoke test，覆蓋戰報持久化、自由征戰、玩家滅亡、三槽存檔、備份復原與 schema 遷移。

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

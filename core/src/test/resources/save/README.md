# Schema 3 回歸資料

`schema3-campaign.json` 是用本次升級的 0.4.0 戰報修正版實際 serializer 產生的合成測試存檔，不含玩家資料。

故意保留一份已讀舊戰報、一支零士氣在途軍、城市低／高民心，以及目標失敗但仍 ACTIVE 的戰局。它沒有新版 City morale 與 BattleReport moraleRecorded 欄位，避免用「新版物件僅改 schema 數字」掩蓋真實缺欄位問題。

保存時間已改成固定測試時間，其他欄位保持舊格式。請不要手動填入 schema 4 新欄位。

# Sango 0.3.0 → 0.4.0 Upgrade

## 建議方式：套用 Git patch

先確認目前專案確實是 0.3.0，且工作目錄沒有未保存修改：

```powershell
git status --short
git log -1 --oneline
```

建議先建立分支：

```powershell
git switch -c feat/flow-polish-0.4.0
```

先檢查 Patch，不修改檔案：

```powershell
git apply --check "C:\下載路徑\Sango-0.3.0-to-0.4.0.patch"
```

沒有輸出代表可套用：

```powershell
git apply "C:\下載路徑\Sango-0.3.0-to-0.4.0.patch"
```

檢查變更：

```powershell
git status --short
git diff --stat
git diff --check
```

執行驗證：

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean core:check
.\gradlew.bat lwjgl3:run
```

Android：

```powershell
.\gradlew.bat android:assembleDebug
```

## Patch 無法套用

若 `git apply --check` 顯示 `patch does not apply`，常見原因是：

- 目前不是乾淨的 0.3.0 baseline。
- 0.3.0 檔案曾被手動修改。
- Patch 已經套用過。
- 先前使用 ZIP 覆蓋，留下與 baseline 不一致的檔案。

可先檢查是否已套用：

```powershell
git apply --reverse --check "C:\下載路徑\Sango-0.3.0-to-0.4.0.patch"
```

反向檢查成功通常表示 Patch 已經存在於工作目錄，不要重複套用。

不要為了強行套用而使用 `git reset --hard` 或 `git clean -fd`；這兩個指令可能刪除尚未保存的程式與資產。

## 存檔相容性

0.4.0 使用：

```text
SaveGameDocument.schemaVersion = 1
GameState.schemaVersion = 3
```

0.3.0 使用 `GameState.schemaVersion = 2`。0.4.0 讀取時會自動：

1. 將舊 `CampaignStatus` 映射至新的目標狀態。
2. 建立獨立的 `GameplayStatus`。
3. 初始化空的戰報陣列與戰報序號。
4. 對因舊版「勝敗即鎖死」而變成 0 行動力、但仍擁有城池的戰局恢復行動力。
5. 清除 deprecated `CampaignStatus`。

遷移先在記憶體完成；下一次自動或手動保存時才正式寫成 schema 3。原有 `.backup.json` 仍會依 Repository 規則保留。

0.2.0 的 `GameState.schemaVersion = 1` 仍不支援直接遷移，因其缺少 0.3.0 才建立的地圖、城池所有權、敵軍、治水、城防與軍隊資料。

## 音訊資產

0.4.0 新增：

```text
assets/audio/music/*.ogg
assets/audio/sfx/*.ogg
```

這些檔案必須與程式一起加入 Git，否則 Audio service 會記錄找不到資產，遊戲雖不一定中止，但不會播放聲音。

若要重新產生 Prototype Audio：

```powershell
python tools/generate-prototype-audio.py
```

需要本機可執行 `ffmpeg`。一般升級不需要重新產生，直接使用 Patch 內的 OGG 即可。

## 升級後手動測試

最低限度確認：

1. Lobby 可讀取原本 0.3.0 存檔。
2. 原本已逾期的戰局不再鎖死，仍可結束月份與操作。
3. 地圖與內政都會確認「結束本月」。
4. 戰鬥後可查看獨立戰報。
5. 未立即觀看的戰鬥會留在地圖節點上。
6. 三個存檔槽可讀取、另存、覆寫與刪除。
7. 設定中的 BGM／SFX 開關與音量有效。
8. 設定中的「保存並返回 Lobby」可正確保存再離開。

## 建議 Commit

```text
feat: 完善戰報、存讀檔、月末流程與音訊
```

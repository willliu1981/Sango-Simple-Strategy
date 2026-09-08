# Upgrade 0.2.0 → 0.3.0

## 建議方式

最安全的方式是使用交付的 Git patch，讓 Git 正確新增地圖／戰爭檔案並修改既有檔案：

```powershell
git status --short
git apply --check "C:\下載路徑\Sango-0.2.0-to-0.3.0.patch"
git apply "C:\下載路徑\Sango-0.2.0-to-0.3.0.patch"
```

若直接解壓完整 Source ZIP，請以完整專案目錄取代舊版；不要只用 Windows 檔案總管覆蓋後假設舊檔會自動刪除。

## 存檔不相容

0.3.0 將：

```text
GameState.schemaVersion: 1 → 2
```

新增的必要狀態包括：

- 地圖 ID、敵對／中立勢力與勝利目標。
- 戰役狀態與期限。
- 敵軍集結倒數。
- 治水、城防、洪災秋收倍率及偵察期限。
- 多城池所有權。
- 行軍中的 `ArmyState[]`。

目前沒有 schema 1 → 2 migration。0.2.0 存檔不具備足夠資料，0.3.0 會將它視為不可讀取；這是預期行為，而不是存檔 Repository 故障。

Desktop 舊存檔通常位於：

```text
%USERPROFILE%\.sango\save\
```

可先備份整個資料夾，再刪除舊 slot：

```powershell
$saveDirectory = Join-Path $HOME ".sango\save"
Copy-Item $saveDirectory "$saveDirectory-0.2.0-backup" -Recurse -Force
Remove-Item (Join-Path $saveDirectory "slot-01.json") -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $saveDirectory "slot-01.tmp") -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $saveDirectory "slot-01.backup.json") -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $saveDirectory "slot-01.corrupt.json") -Force -ErrorAction SilentlyContinue
```

也可以不手動刪除，直接在 Lobby 選擇「開始新局」並確認覆寫。舊進度不會轉換成新地圖戰局。

Android 的存檔位於 App private storage；開發階段可清除 App data 或解除安裝後重裝。這會移除所有 Sango 本機存檔與設定，操作前請確認沒有要保留的資料。

## 字型

完整 Source ZIP 不包含 developer-local Source Han Sans OTF。若改用新目錄，需重新執行：

```powershell
.\tools\prepare-local-fonts.ps1 -StudyRoutineSource "C:\path\to\StudyRoutineCurated-source.zip"
```

若在既有 working tree 套用 patch，現有未追蹤字型檔會保留，不需重複準備。

## 驗證

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean core:check
.\gradlew.bat lwjgl3:run
```

若編譯仍提到 `PrototypeCampaignScreen`，代表早期 0.1.x 的舊檔仍殘留，依 `docs/UPGRADE_0.1.3_TO_0.2.0.md` 刪除該 Java 與 XML 檔。

# 從 0.4.0 戰報修正版升級到 0.5.0

## 基準與操作範圍

本次 Patch 精確以 `Sango-0.4.0-runtime-battle-report-fix-source.zip` 為差異基準。它不是 0.3.0 → 0.5.0 的累積 Patch，也不假設你已 Commit 0.4.0。

不要只根據版本字串判斷：基準需已包含上一輪 `TurnReportTextFormatter` 的延後 City lookup，以及 `BattleResolutionService` 不再把 Faction ID 放入 `otherCityId` 的修正。

此交付只提供更新檔案，沒有修改你的 Windows 專案、沒有替你 Commit，也沒有操作真實玩家存檔。

## 先備份存檔與未提交修改

關閉正在執行的 Sango。保留目前專案或 Git 狀態，另將 `%USERPROFILE%\.sango\save` 整個資料夾複製到獨立備份位置。不要只依賴遊戲自己的 `slot-XX.backup.json`：它會在後續保存時輪替。

存檔通常不在 Source ZIP 裡；備份專案不等於備份遊戲進度。

在原專案根目錄執行以下指令。任一檢查失敗就停止，不要 `--reject`，不要強行覆蓋未提交修改。

```powershell
Set-Location "C:\Users\Kuanwei\AndroidStudioProjects\others\Sango"
$patchPath = "C:\Users\Kuanwei\Downloads\Sango-0.4.0-fixed-to-0.5.0.patch"

git status --short
git diff --stat
Get-FileHash -Algorithm SHA256 $patchPath

git apply --check $patchPath
if ($LASTEXITCODE -ne 0) {
    throw "Patch 檢查失敗，請先確認基準、是否重複套用與未提交修改。"
}
git apply $patchPath
if ($LASTEXITCODE -ne 0) {
    throw "Patch 套用失敗，請停止後續操作。"
}
git diff --check
git diff --stat
.\gradlew.bat clean core:check
if ($LASTEXITCODE -ne 0) {
    throw "Core 檢查失敗，請保留錯誤訊息，不要繼續 Commit。"
}
.\gradlew.bat lwjgl3:run
```

先在另一個存檔槽建立「六勢力・全國征戰」新局，確認新地圖與操作。再測試讀取舊存檔。最後執行 Android `assembleDebug` 及裝置 Runtime 測試。

## ZIP 的用途

ZIP 是另建副本的 Source 快照，不必再覆蓋到已套用 Patch 的專案。解壓到新的空白資料夾，依根目錄 README 從自己的原 Sango 複製字型，之後可用 Android Studio 開啟。

ZIP 不含 `.git`、`local.properties`、IDE 設定、快取與編譯結果，所以不等於可完整復原開發環境與 Git 歷史的備份。兩個 Desktop 副本預設共用使用者的 `.sango/save`，不要同時開啟兩份遊戲操作同一槽位。

## 遷移規則

- schema 2 先沿原有路徑遷移到 3，再遷移到 4。
- schema 3 → 4：城市士氣初始化為 `max(50, min(100, publicOrder))`；既有軍隊士氣原值保留，包含合法的零士氣。
- 舊戰報 ID、已讀狀態、傷亡與結果保留。當年沒有記錄士氣，不推測或倒填；顯示「未記錄」。
- schema 4 存檔的零士氣不會被當成缺值補回 50。
- 地圖 ID、劇本 ID、領地、年月與行動力保留。舊六城局不會被改成 42 城。
- schema 1 與未來不認識的 schema 明確拒絕，不要求直接刪檔；保留原檔後再處理。

讀取時先在記憶體遷移，不立刻改寫原 JSON。下一次內政、出征、月底結算、已讀標記或手動保存等有保存行為的操作，才會寫入 schema 4。保存仍採 tmp → 驗證 → backup → primary。

## 回退

0.4.0 不認識 schema 4。回退程式碼之前先關閉遊戲，再從升級前的獨立備份還原整個存檔目錄。不要拿 0.4.0 嘗試讀取已保存為 4 的主要存檔，也不要把 schema 數字手動改回 3。

## 清理內容

Patch 實際刪除：

- `core/src/main/java/idv/kuan/studio/sango/FirstScreen.java`：未被引用的初始化樣板。
- `lwjgl3/src/main/resources/libgdx16.png`、`libgdx32.png`、`libgdx64.png`、`libgdx128.png`：未使用的預設圖示。已確認 Launcher 使用 `sango*.png`。

舊 `PrototypeCampaignScreen.java` / `prototype_campaign.xml` 在基準中已不存在，不會重新加入。舊六城 Definition、`CampaignStatus` 與舊升級文件有相容性或歷史用途，不當成垃圾刪除。

`assets/assets.txt` 是建置產生的索引，保持忽略、不納入 ZIP；它在本機重新產生是正常行為。字型、SDK 設定、存檔與使用者未提供的檔案不會被 Patch 刪除。

## Commit

所有本機測試通過後再檢查差異，建議：

`feat: 擴充六勢力全國地圖並完善士氣與空城佔領`

Commit 前排除 `.idea/`、`.gradle/`、`**/build/`、`local.properties`、`assets/assets.txt`、本機 OTF/TTF、簽章金鑰、API key、token 與密碼。請保留既有 SimpleUI JAR 與 Gradle Wrapper 的專案管理方式，不要把其他專案的整套系統帶入。

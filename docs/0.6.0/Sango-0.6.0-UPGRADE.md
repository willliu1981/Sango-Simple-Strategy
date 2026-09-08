# Sango 0.6.0 升級與本機驗收

## 基準

這次 Patch 僅相對使用者這次上傳的 `Sango-source(1).zip` 製作。

原 ZIP SHA-256：`89fec7a2e248f64886b4fb20333e9ffc9b70c67a625e2df5c15423c2cad7cd30`。

上傳內容沒有 `.git`，因此不知道使用者本機 HEAD、未提交修改或先前 Commit 狀態。不能只因畫面顯示 0.5.2 就認定一定是同一份基準；以 `git apply --check` 結果為準。交付未自行 Commit。

## 首選：Patch 更新現有專案

將 Patch 存至可存取的位置，於目前 Sango 專案根目錄開啟 PowerShell。以下使用完整下載路徑作例子；資料夾不同時修改 `$patchPath`。

```powershell
$ErrorActionPreference = 'Stop'
$patchPath = Join-Path $env:USERPROFILE 'Downloads\Sango-uploaded-source-to-0.6.0.patch'
$saveDirectory = Join-Path $env:USERPROFILE '.sango\save'
$backupDirectory = Join-Path $env:USERPROFILE ('.sango\save-before-0.6.0-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))

# 先關閉執行中的遊戲，備份玩家進度；Source ZIP 不包含玩家存檔。
if (Test-Path -LiteralPath $saveDirectory -PathType Container) {
    Copy-Item -LiteralPath $saveDirectory -Destination $backupDirectory -Recurse
    Write-Host "存檔備份：$backupDirectory"
}

# 查看現有修改；此流程不執行 reset、刪檔或 Commit。
git --no-pager status --short
if ($LASTEXITCODE -ne 0) {
    throw '請在既有 Sango Git 專案根目錄操作。'
}
git apply --check -- $patchPath
if ($LASTEXITCODE -ne 0) {
    throw 'Patch 基準不一致。停止套用，保留錯誤訊息與目前工作樹。'
}
git apply -- $patchPath
if ($LASTEXITCODE -ne 0) {
    throw 'Patch 套用失敗；請檢查輸出，不要強制 --reject。'
}
git --no-pager diff --stat

# 正式依賴建置與六組回歸驗證；本次交付環境未能完成這一步。
.\gradlew.bat :core:check
if ($LASTEXITCODE -ne 0) {
    throw '正式建置／回歸未通過，請保留第一個錯誤。'
}
.\gradlew.bat :lwjgl3:run
```

Patch 刻意不包含四首 MP3 或圖資。若要測試四季 BGM，使用者須在確認權利後自行將本機 MP3 放到程式預期的資產路徑；不要將未確認授權的音檔加入公開 repository、Patch 或 release。不更動、刪除或重新散布原專案的字型及 SimpleUI JAR。`assets/assets.txt` 是既有 Gradle 產生檔，不納入 Patch，建置時會重新列出資產。

若檢查失敗，不自動排除 README、tools 或其他路徑，不使用 `--reject`，不 `reset --hard`。先確認是否在上傳後又改過本機程式；需要重新以最新 Source 製作 Patch 時，保留所有本機修改。

## 另一選擇：Source ZIP 另開副本

Source ZIP 是完整程式碼與可交付資產的快照，不是要求覆蓋原專案。解壓至新的資料夾，從新根目錄執行：

`powershell -ExecutionPolicy Bypass -File .\tools\prepare-local-assets-0.6.0.ps1 -SourceProject "C:\Users\Kuanwei\AndroidStudioProjects\others\Sango"`

請把 SourceProject 指向仍保有原始本機資產的舊 Sango 專案；不要指向尚未補資產的新副本。

包內不提供任何字型檔或含字型 glyph 的 atlas；也不重包內嵌字型資源的 `libs/simpleui-1.1.2.jar`。上述工具只在使用者本機從原專案複製必要的兩個中文 OTF、bitmap font、font／skin atlas 及原 SimpleUI JAR。來源缺失或已有目標內容不同時停止，不覆寫；複製後比對 SHA-256。不改 Source、不改存檔。

不需要、也不應把字型加入 Commit。未使用的 Charis 字型未附帶且非此版本必要資產。Gradle wrapper JAR 保留；正式依賴仍由 Gradle 解析。Patch 更新既有專案無需重複執行本機資產準備。

## 存檔相容與回退

GameState schema 2→3→4→5，Save document schema 維持 1。舊六城存檔不自動擴為 42 城；本月剩餘 AP、人口、零士氣、戰報等保留。新小數欄位初始化為零，AI 新額度欄位按既有領地初始化。

讀入舊檔後，在後續保存時會寫成 schema 5。0.5.2 不支援讀回 schema 5；需要降版時，先關閉遊戲，保留新進度副本，再恢復升級前備份。不要直接刪除 `.sango/save`，也不要只依賴會隨後續保存輪替的單份自動 backup。

Desktop 存檔為 `%USERPROFILE%\.sango\save`。Android 使用應用程式自己的存檔目錄；升級前備份應採既有可用流程，不為測試而解除安裝以致刪除應用資料。

## 必須在本機完成的驗收

1. 新建兩城高民心戰局，首月顯示 3 AP；切城不補點。讀舊 2/3 或 2/9 存檔仍保留原額度，下一月才更新。
2. 內政徵兵 modal：滑桿、鍵盤輸入、±100、最少／最多均可操作；取消不扣費；確認的金糧、人口與平均素質和預覽相符。人口 2,001 的測試狀態最多徵 1 人。
3. 訓練後素質及小數正確；大軍多次訓練可累積。存檔、離開、再讀入不歸零。零兵與雙滿值不扣資源。
4. 十二月底人口依治理增減；一月底、讀檔、切城不額外增加。觀察 AI 月報命令消耗沒有超出其 AP。
5. 三月→四月、九月→十月、十二月→一月，BGM 平滑換季；同季地圖／內政／報告切換不重播。快速推進月份、關閉再開啟音樂、切背景再恢復均測一次。
6. 從 Lobby 與遊戲 Settings 分別進入音樂鑑賞；四曲、前後首、暫停、音樂開關有效，退出恢復正確背景曲。暫停後切到背景再回前景不自行解除暫停。
7. Desktop 視窗縮放、Android 觸控與軟鍵盤、中文字型、modal 遮罩、播放器及內政資訊不被裁切。音訊實際響度、循環接縫與 Android 解碼由實機確認。

第一版仍保留野戰軍 1,000 上限和每勢力單一軍隊；不要把「可徵兵一萬」誤認為已擴大出征系統。這版沒有新加入安民／民心恢復途徑，長期平衡仍需後續討論。

## 建議 Commit message（未執行）

`feat: add population-based recruitment, partial training and seasonal music player`

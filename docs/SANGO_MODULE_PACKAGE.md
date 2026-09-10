# Sango 策略模組包

這套工具把 Sango 的策略玩法來源整理成可稽核、可重現的 ZIP，供 Curated 的「群島紀元」同步工具讀取。它不是把 Curated 當成另一套規則維護；Sango 仍是玩法、UI、AI、存檔格式與回歸測試的唯一上游。

目前是第一階段安全邊界，契約的 `installationMode` 固定為 `preflight-only`。Curated 安裝器可以驗證、列出差異與產生報告，但必須拒絕 live overwrite。待 Curated 的主程式接線、名詞轉換、島嶼資料與存檔隔離都形成可測試的明確 adapter 後，才可另行升級契約。

## 內容與邊界

模組包包含：

- Sango `core` 內的策略玩法、UI、AI、存檔與遷移程式，但不含 `Main` 與 context provider。
- Sango 的策略回歸測試與合成存檔 fixture。
- UI XML、繁中遊戲文字及地圖／勢力／劇本資料，並逐檔標示 Curated 目標提示與必要轉換。

刻意不包含：

- Android、Desktop launcher、`META-INF/services` 與 Curated 主程式註冊流程。
- 字型、Skin、圖片、音效等由 Curated 保有的外觀與宿主資產。
- 玩家存檔、偏好設定、Gradle/build output、產生式 assets index 與任何執行期資料。

候選 managed paths 與不可觸碰的 protected paths 都寫在 `tools/sango-module/package-contract.json`，並複製進 ZIP 與 manifest。`candidate-only` 不代表已取得覆寫權。

## 使用方式

先驗證目前 commit、版本與檔案範圍，不寫任何輸出：

```powershell
python tools/sango-module/export-sango-module.py --dry-run
```

正式匯出預設要求 contract 與 exporter 都已由目前 `sourceCommit` 追蹤且沒有修改。開發這套工具本身、尚未 commit 時若只需驗證，可明確加上 `--allow-uncommitted-tooling`；產生的 manifest 會記錄這不是可發布 provenance，不能冒充正式模組包。

列出每個來源檔、角色與 Curated 目標提示：

```powershell
python tools/sango-module/export-sango-module.py --list
```

建立模組包及旁置 SHA-256：

```powershell
python tools/sango-module/export-sango-module.py
```

預設輸出為 `dist/sango-module-<gameVersion>.zip` 與同名 `.sha256`。也可用 `--output <path>` 指定位置。

匯出器只取 Git 已追蹤的契約範圍，並在這些 tracked 來源有 staged 或 unstaged 變更時拒絕匯出；契約外的 untracked 檔案不會阻擋，也不會進包。這可避免 manifest 宣稱某個 commit，內容卻混入尚未提交的玩法修改。

## 可重現性與驗證

ZIP 依 archive path 排序，所有 entry 使用固定時間、固定 Unix 權限與無壓縮儲存；JSON 採 UTF-8、固定縮排與 key 排序。相同 commit 與契約會得到完全相同的 ZIP bytes。

`manifest.json` 記錄：

- 契約版本、最低安裝器版本與安裝模式。
- Sango 遊戲版本、存檔文件 schema、遊戲狀態 schema 及來源 commit。
- 契約 SHA-256。
- contract／exporter 是否已由來源 commit 完整追蹤，以及是否使用開發 override。
- 每個 payload 的來源路徑、用途、目標提示、轉換方式、大小與 SHA-256。
- 候選 managed paths 與 protected paths。

執行 focused tests：

```powershell
python -m unittest tools/sango-module/test_exporter.py
```

測試只使用系統暫存目錄建立 Git fixture，不會產生或修改專案內的 ZIP、存檔或其他執行期資料。

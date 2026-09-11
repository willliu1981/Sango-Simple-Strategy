# Sango 策略模組包

這套工具把 Sango 的策略玩法來源整理成可稽核、可重現的 ZIP，供 Curated 端 Codex 整體替換「群島紀元」。它不是把 Curated 當成另一套規則維護；Sango 仍是玩法、UI、AI、資料、存檔格式與回歸測試的唯一上游。

契約的 `syncMode` 固定為 `codex-assisted`。同步包不提供固定安裝器或通用 `--apply`；匯出器會另外產生已填好完整路徑的兩階段 Codex prompt。第一階段確認精確替換範圍，第二階段刪除 Curated 舊群島策略核心、完整放入 Sango，再套用群島名詞與最薄的宿主接線。

## 內容與邊界

模組包包含：

- Sango `core` 內的策略玩法、UI、AI、存檔與遷移程式，但不含 `Main` 與 context provider。
- Sango 的策略回歸測試與合成存檔 fixture。
- UI XML、繁中遊戲文字及地圖／勢力／劇本資料，並逐檔標示 Curated 目標提示與必要轉換。

刻意不包含：

- Android、Desktop launcher、`META-INF/services` 與 Curated 主程式註冊流程。
- 字型、Skin、圖片、音效等由 Curated 保有的外觀與宿主資產。
- 玩家存檔、偏好設定、Gradle/build output、產生式 assets index 與任何執行期資料。Curated 舊群島存檔保留原檔但不遷移，新版要求開新局。

可整體替換或只複製列出檔案的 managed paths，以及不可觸碰的 protected paths，都寫在 `tools/sango-module/package-contract.json`，並複製進 ZIP 與 manifest。實際替換仍需先完成第一階段盤點並取得使用者確認。

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

建立模組包、旁置 SHA-256 及 Codex prompt：

```powershell
python tools/sango-module/export-sango-module.py
```

預設輸出為：

- `dist/sango-module-<gameVersion>.zip`
- `dist/sango-module-<gameVersion>.zip.sha256`
- `dist/sango-module-<gameVersion>-codex-prompt.md`

Prompt 內會直接寫入 ZIP 與 SHA-256 的絕對路徑，分成只盤點的 `!plan` 與確認後執行的 `!exec` 兩段。使用 `--output <path>` 指定其他 ZIP 位置時，另外兩個檔案也會產生在該 ZIP 的同一目錄。

匯出器只取 Git 已追蹤的契約範圍，並在這些 tracked 來源有 staged 或 unstaged 變更時拒絕匯出；契約外的 untracked 檔案不會阻擋，也不會進包。這可避免 manifest 宣稱某個 commit，內容卻混入尚未提交的玩法修改。

## 可重現性與驗證

ZIP 依 archive path 排序，所有 entry 使用固定時間、固定 Unix 權限與無壓縮儲存；JSON 採 UTF-8、固定縮排與 key 排序。相同 commit 與契約會得到完全相同的 ZIP bytes。

`manifest.json` 記錄：

- manifest／契約版本、Codex prompt 版本與同步模式。
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

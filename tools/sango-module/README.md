# Sango module tools

此目錄把 Sango 的策略玩法、UI、AI、資料、存檔格式與回歸測試整理成可稽核的同步來源包，供 Curated 端 Codex 整體替換「群島紀元」。Sango 是唯一玩法上游；Curated 只在替換後套用群島名詞，並接回宿主入口與獨立存檔／偏好位置。

## 目錄內容

- `export-sango-module.py`：建立 deterministic ZIP、旁置 SHA-256，以及已填好完整路徑的 Codex prompt。
- `package-contract.json`：定義來源範圍、Curated 目標提示、必要轉換、候選 managed paths 與 protected paths。
- `test_exporter.py`：驗證重現性、路徑安全、dirty source 與 release provenance。

較完整的格式與邊界說明見 [`docs/SANGO_MODULE_PACKAGE.md`](../../docs/SANGO_MODULE_PACKAGE.md)。

## 常用命令

只檢查，不產生檔案：

```powershell
python tools/sango-module/export-sango-module.py --dry-run
```

列出即將打包的檔案：

```powershell
python tools/sango-module/export-sango-module.py --list
```

正式建立 ZIP、`.sha256` 與 Codex prompt：

```powershell
python tools/sango-module/export-sango-module.py
```

預設輸出位置：

```text
dist/sango-module-<gameVersion>.zip
dist/sango-module-<gameVersion>.zip.sha256
dist/sango-module-<gameVersion>-codex-prompt.md
```

例如 Sango 版本為 `0.6.2` 時，完整路徑通常會是：

```text
C:\Users\<使用者名稱>\AndroidStudioProjects\others\Sango\dist\sango-module-0.6.2.zip
```

也可自行指定輸出位置：

```powershell
python tools/sango-module/export-sango-module.py --output "D:\指定位置\sango-module.zip"
```

執行工具測試：

```powershell
python -m unittest tools/sango-module/test_exporter.py
```

## 交給 Curated 端 Codex 同步

ZIP 是可追溯的「Sango 策略核心同步來源包」，不是可直接雙擊的安裝程式。Curated 端不合併舊玩法，而是在使用者確認後整體替換群島策略核心，再套用名詞並接回宿主。同步契約固定採 `codex-assisted`，不建立固定安裝器或通用 `--apply`。匯出後請開啟同目錄的 `-codex-prompt.md`：先貼「第一階段」，確認盤點方案後，再於同一個任務貼上「第二階段」。檔案內已填入 ZIP 與 SHA-256 的完整路徑，不需手動修改。

## 目前限制

同步包只提供來源內容、完整性、版本、schema、替換範圍與轉換提示，不負責自動覆寫 Curated。實際同步由 Curated 端 Codex 先確認精確替換範圍，再依使用者確認執行。Curated 舊群島存檔不遷移、不刪除，但新版不再載入，玩家需開新局。

正式匯出要求 exporter 與 contract 已由目前 Git commit 追蹤且沒有修改。只有開發工具本身、尚未 commit 時的端到端測試，才可明確加上 `--allow-uncommitted-tooling`；這種包會被標示為 development bundle，不能當作 release package。

`__pycache__` 是 Python 執行時產生的快取，可忽略：它不會影響上述正式匯出判定，也不會被打包進模組包。

工具不會打包或修改玩家存檔、preferences、Curated 的 Curadia／塔防／學習模組、宿主 provider、全域字型、Skin、圖片或執行期資料，也不會自動 commit。

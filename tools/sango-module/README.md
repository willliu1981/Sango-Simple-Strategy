# Sango module tools

此目錄負責把 Sango 的策略玩法、UI、AI、存檔格式與回歸測試整理成可稽核的模組 ZIP，供 Curated「群島紀元」端的安裝器驗證。Sango 是唯一玩法上游；Curated 只保留名詞、島嶼情境與素材、宿主整合，以及獨立存檔／偏好位置。

## 目錄內容

- `export-sango-module.py`：建立 deterministic ZIP、`manifest.json` 與旁置 SHA-256。
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

正式建立 ZIP 與 `.sha256`：

```powershell
python tools/sango-module/export-sango-module.py
```

執行工具測試：

```powershell
python -m unittest tools/sango-module/test_exporter.py
```

## 目前限制

目前契約固定為 `preflight-only`。Curated 安裝器可以驗證完整性、版本、schema、allowlist 與轉換需求，但必須拒絕 `--apply`。在 Curated 的 runtime profile、共同 UI 邊界、島嶼資料驗證及舊存檔 migration fixture 完成前，不得用此工具直接覆寫群島核心。

正式匯出要求 exporter 與 contract 已由目前 Git commit 追蹤且沒有修改。只有開發工具本身、尚未 commit 時的端到端測試，才可明確加上 `--allow-uncommitted-tooling`；這種包會被標示為 development bundle，不能當作 release package。

工具不會打包或修改玩家存檔、preferences、Curated 的 Curadia／塔防／學習模組、宿主 provider、全域字型、Skin、圖片或執行期資料，也不會自動 commit。

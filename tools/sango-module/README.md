# Sango module tools

此目錄把 Sango 的策略玩法、UI、AI、存檔格式與回歸測試整理成可稽核的同步來源包，供 Curated 端 Codex 協助同步「群島紀元」。Sango 是唯一玩法上游；Curated 只保留名詞、島嶼情境與素材、宿主整合，以及獨立存檔／偏好位置。

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

ZIP 是可追溯的「Sango 策略核心同步來源包」，不是可直接雙擊或無條件覆寫 Curated 的安裝程式。匯出後請開啟同目錄的 `-codex-prompt.md`：先把「第一階段」貼到 Curated 專案的 Codex，確認盤點方案後，再於同一個任務貼上「第二階段」。檔案內已填入 ZIP 與 SHA-256 的完整路徑，不需手動修改。

## 目前限制

目前同步包只提供來源內容、完整性、版本、schema、目標提示與轉換需求，不負責自動覆寫 Curated。實際同步必須由 Curated 端 Codex 先比對現況、提出方案，再依使用者確認執行。

正式匯出要求 exporter 與 contract 已由目前 Git commit 追蹤且沒有修改。只有開發工具本身、尚未 commit 時的端到端測試，才可明確加上 `--allow-uncommitted-tooling`；這種包會被標示為 development bundle，不能當作 release package。

工具不會打包或修改玩家存檔、preferences、Curated 的 Curadia／塔防／學習模組、宿主 provider、全域字型、Skin、圖片或執行期資料，也不會自動 commit。

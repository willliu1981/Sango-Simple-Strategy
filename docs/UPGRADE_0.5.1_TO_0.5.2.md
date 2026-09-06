# Sango 0.5.1 → 0.5.2 升級

## 基準

本 Patch 以已交付 `Sango-0.5.1-ui-font-training-fix-source.zip` 為基準。

基準 SHA-256：`d08431a0eeafbfcd4ace38946680a8a9427884755b8ca68a1babd96ad88264f0`。

沒有取得本機 .git，因此不宣稱知道使用者的 commit hash 或未提交修改。若本機又有其他修改，必須以 `git apply --check` 的結果為準。

本次不修改根目錄 README.md 或 tools/prepare-local-fonts.ps1，避免上一版因本機保留既有檔案而發生的新增路徑衝突；不需要 --exclude。

## 更新

先關閉遊戲並備份 `%USERPROFILE%\.sango\save`。Source ZIP 不含遊戲存檔。

在目前 Sango 專案根目錄執行 `git apply --check`，成功後再執行 `git apply`。Patch 已包含兩張 JPEG 的 Git binary patch，不必另外複製底圖。不使用 --reject、git reset --hard 或檔案總管覆蓋作為修復方案。

檢查修改時使用 `git --no-pager diff --stat`，避免停在 pager 的冒號畫面。

本機驗證：`gradlew.bat clean core:check` → `gradlew.bat lwjgl3:run` → `gradlew.bat android:assembleDebug`。

## 存檔

Schema 不變。舊存檔保留本月剩餘與額度，下一個月開始按全城平均民心計算。不必重新開局、不必刪存檔；舊六城仍是六城，並有自己的區域山河底圖。

## ZIP 副本

完整 Source ZIP 用於另建副本，請解壓到新的空白資料夾；不含 .git、本機設定、字型檔、建置產物或玩家存檔。

從新副本執行原有字型工具，將必要字型由你自己的原 Sango 複製過去：`tools/prepare-local-fonts.ps1 -SourceProject <原專案路徑>`。套用 Patch 更新既有專案時不需要這一步，原字型不會被刪除或改寫。

## 音樂

Divided Realm_2 的收尾音檔獨立交付。此次 Patch 不替換 assets/audio 的 BGM 或 SFX，也不把未確認用途的音樂自動加入遊戲。

## Commit

本機 Runtime 確認正常後建議：`feat: 加入山河底圖與全城民心行動力`。

先檢查新增檔案；不要提交 .idea、.gradle、build、local.properties、assets/assets.txt、字型檔、金鑰或玩家存檔。

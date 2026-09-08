# Sango 0.6.0 驗證紀錄

日期：2026-09-07。以下是本次實際執行結果，不沿用上一版通過紀錄。

## 實際工具鏈

環境提供 OpenJDK 21.0.11，使用 `javac --release 17 -encoding UTF-8` 編譯全部 core/main 與 core/test。輸出 class major version 為 61；這代表 Java 17 位元碼／語言 API 目標，不代表已以真正 JDK 17 Runtime 啟動遊戲。

正式 Gradle 依賴維持 LibGDX 1.13.1，未降版。由於 Gradle 8.8 下載失敗（services.gradle.org DNS UnknownHostException），本次離線 classpath 使用上傳的 SimpleUI 1.1.2 JAR 內嵌 LibGDX 1.12.1。正式 Gradle 原本就會剝除 SimpleUI 內嵌 com/badlogic，再採用宣告的 1.13.1；此流程沒有取消。

SimpleUI 原檔 SHA-256：`4948978d8ecd6fdac273b5c69033931d6bf6fc433b0666a70b7408cadd53bf95`。

## 已執行且通過

| 驗證 | 結果／範圍 |
|---|---|
| core/main + core/test 離線 Java 編譯 | 全部通過；空編譯 log 表示無輸出錯誤 |
| VerticalSliceSmokeTest | 舊六城、經濟、戰鬥／戰報、存檔槽、任務結束後自由遊玩通過 |
| NationalCampaignSmokeTest | 13,113 個檢查；18 組空城組合；288 個模擬月份 |
| NationalActionPointSmokeTest | 34,984 個檢查；雙城全部 10,201 組民心、0～4200 全部總和門檻、快照、佔領、洪災、讀檔 |
| UiResourceSmokeTest | 5,799 個檢查；XML schema、Actor ID、動態四曲 ID、i18n、glyph、地圖數學與地形資產 |
| CampaignGrowthSmokeTest | 2,130,163 個檢查；人口 0～1,050,000 全數邊界、取整、保留人口、費用、加權、覆蓋、年份、遷移、AI |
| MusicPlaybackSmokeTest | 1,053 個檢查；假 Music 測試 2 秒轉場、1000 次快速切換、暫停／恢復、關閉／開啟、重試與釋放 |
| 四首 MP3 | SHA-256 與各自這次上傳來源完全一致；ffmpeg 完整音訊解碼 exit 0、無 error 輸出 |

「檢查數」含同一性質迴圈的斷言，不是兩百萬場獨立遊戲。288 個模擬月份沿用測試夾具的強防守及軍糧供給，以驗證流程可持續，**不是自然遊玩平衡測試**。

舊 VerticalSlice 的戰鬥／戰報案例在該案例內把 AI 當月額度設為 0，隔離 AI 新內政補兵造成的預期變化；AI 的真正扣點、徵兵費用、同條件素質與零額度行為另由 CampaignGrowthSmokeTest 驗證。沒有把失敗測試直接刪除。

新增 `schema4-national-campaign.json`：以本次未修改的 0.5.2 Source 另行編譯後，實際產生 42 城曹操測試存檔，設月中 2/9 AP、主城士氣 0。再由 0.6.0 Repository 讀取，確認 schema 4→5、保留額度與零士氣、可再次安全保存。不是只改新存檔的 schemaVersion 來冒充真實舊格式。其內容為合成測試局，不是玩家進度。

音訊詳細資料位於 `validation/audio-validation.json`；四曲均 stereo / 48 kHz，長度約 209.5～209.9 秒。ffmpeg 解碼不等於已聽過、不等於 LibGDX／Android 實機解碼成功。

## 尚未完成／不可宣稱通過

正式 Gradle `:core:check`、LibGDX 1.13.1 classpath 編譯與測試、JDK 17 Runtime、LWJGL3 啟動、OpenAL 播放、Android build／install／實機、實際 SimpleUI 排版和軟鍵盤操作，均未完成。本機資產準備 PowerShell 腳本在此 Linux 環境未執行；已做靜態檢查，不能等同 Windows 驗證。

實際音樂聽感、四曲響度一致性、MP3 循環接縫、Suno 生成時方案／商用授權未核對。本次改名及整合不等同授權確認。正式發佈前需自行確認相應授權。

## 執行方法

正式環境在專案根目錄執行 `gradlew.bat :core:check`，會涵蓋六組 smoke tests。需要明確執行某一組時，可用 `:core:runCampaignGrowthSmokeTest` 或 `:core:runMusicPlaybackSmokeTest`。

離線備援可在 Bash 環境執行 `bash tools/validate-offline-0.6.0.sh`；使用本機既有 SimpleUI JAR，測試輸出放在忽略的 `build/offline-validation`。此腳本仍不代表正式 1.13.1 驗證。

## 封裝驗證

Source ZIP 排除 `.git`、本機設定、建置輸出、玩家存檔、字型／含字型 atlas 及內嵌字型的 SimpleUI 原 JAR。Patch 不改動或刪除這些既有本機資產。

Git Patch 以這次上傳解壓內容建立暫存 index 作比較基準，沒有建立 Commit。封裝時須在新解壓的基準執行 `git apply --check`、實際套用，再逐檔 SHA-256 比較。詳細結果另列於交付的 `Sango-0.6.0-PACKAGE-VALIDATION.json`，避免在壓縮檔內寫入無法自洽的自我雜湊。

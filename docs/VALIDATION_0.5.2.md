# Sango 0.5.2 驗證紀錄

## 基準與範圍

以已交付 0.5.1 Source 為基準，並非重新讀取使用者本機工作樹。GameState schema 仍為 4。

## 實際工具鏈

環境為 OpenJDK 21.0.11，以 `javac --release 17 -Xlint:all -encoding UTF-8` 編譯所有 core main／test Java，無 warning。

無法下載 Gradle 8.8 與正式依賴：實際執行 Wrapper 回報 `UnknownHostException: services.gradle.org`；對 Maven 主機的 DNS 嘗試也失敗。

因此這次離線編譯及無圖形測試使用原附 `simpleui-1.1.2.jar` 的類別。已用 javap 確認其中內嵌的 LibGDX 為 **1.12.1**，不是專案 Gradle 指定的 1.13.1。本次沒有改動專案版本，正式 Gradle 仍會排除 SimpleUI 內嵌 LibGDX、使用 1.13.1。

這些結果可驗證純 Java 規則與資產結構，但不能宣稱已通過 JDK 17 Runtime、正式 LibGDX 1.13.1 build、LWJGL3、OpenAL 或 Android Runtime。

## 已執行測試

| 測試 | 結果 |
|---|---|
| 全部 Core main／test 編譯，Java 17 target | PASS，無 warning |
| VerticalSliceSmokeTest | PASS，舊六城、戰報、存檔及自由征戰 |
| NationalCampaignSmokeTest | PASS，9818 assertions、18 組空城、288 個模擬月份 |
| UiResourceSmokeTest | PASS，5200 checks；XML、i18n、glyph、Actor ID、兩張 JPEG 解碼及相機對齊 |
| NationalActionPointSmokeTest | PASS，30783 checks；含兩城 10201 組民心、所有任務狀態、讀檔不回補、低民心新佔城、洪災後發放 |

288 月測試沿用強化防守與軍糧的結構測試，不是自然遊玩平衡測試。

UI 新增的民心摘要以實際本機字型作額外離線量測：字級 20 時，`全城民心 100.0（42 城）・下月預估 9 點` 約 361.2 px，小於 540 px 容器；這仍不取代實際 Scene2D 排版測試。測試所用字型不隨交付物提供。

## 本機必測

啟動遊戲確認地形位於道路／城池下方，平移、滾輪、全圖、定位、雙指縮放與面板點擊正常；進出設定與城池不重載背景、不重設行動力。Android 需驗證 pause／resume 與 context restore。

讀取舊存檔確認仍保留當月 2/3 等既有剩餘，結束月份後才按全城平均民心發放。查看月報是否顯示實際民心與額度。AI 節奏未在本版重平衡。

## 音訊

Divided Realm_2 獨立提供淡出與殘響收尾，未替換遊戲音訊。解碼原長 60.225306 秒，52.2 秒開始淡出，成品 63.1 秒；MP3／OGG／WAV 重新解碼皆無滿刻度樣本，最後樣本為零。沒有作真人聆聽驗收，也沒有續作旋律或製作無縫循環。

## 封裝與 Patch

已以原始 0.5.1 基準執行 `git apply --check`、實際套用與逐檔 SHA-256 比對；另以不同的根 README／字型工具內容模擬本機差異，確認兩者均原樣保留。Reverse check 也通過。

本次 Patch 變更 35 個檔案，其中新增 11 個，包含兩張真正的 JPEG binary patch。現有 BGM／SFX 全部維持原樣。Source ZIP 共 168 個檔案，不含字型、.git、建置目錄、本機設定、玩家存檔或私密測試素材。

最終 ZIP 重新解壓後再次編譯並執行相同四組無圖形測試，全部通過。這是封裝一致性驗證，不是新增的實際視窗測試。

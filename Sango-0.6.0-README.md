# Sango 0.6.0

本次已實作人口徵兵曲線、徵兵數量選擇、加權新兵素質、部分覆蓋訓練、年度人口、總民心 AP 與 AI 共用規則，以及四季 BGM／音樂鑑賞播放器。四季 MP3 與圖資不包含在此次 source 提交。

版本基準是這次上傳的 `Sango-source(1).zip`，不是先前其他交付包；沒有取得 `.git`，沒有自行 Commit。

## 從這裡開始

- [實際規則與初版平衡參數](docs/0.6.0/Sango-0.6.0-rules.md)
- [升級、備份與本機驗收](docs/0.6.0/Sango-0.6.0-UPGRADE.md)
- [本次實際驗證與未驗證界線](docs/0.6.0/Sango-0.6.0-VALIDATION.md)
- [下一個 Session 的交接](docs/0.6.0/Sango-0.6.0-HANDOFF.md)

首選用本次 Patch 更新原專案。Source ZIP 用於另開副本，不要以檔案總管直接覆蓋原專案。

Source ZIP 具有完整原始碼，但不提供字型、含字型的 atlas 或內嵌字型的本機 SimpleUI JAR。新副本請先執行 `tools/prepare-local-assets-0.6.0.ps1 -SourceProject <原 Sango 專案>`，再使用 Gradle。更新既有專案的 Patch 不改這些檔案，不必重複準備。

正式依賴仍 Java 17 / LibGDX 1.13.1 / SimpleUI 1.1.2。這次因 DNS 失敗，僅完成 JDK 21 --release 17 加上既有 SimpleUI 內嵌 GDX 1.12.1 的離線編譯、六組無圖形回歸及音訊檔案解碼；不能當成 Desktop／Android 實機或正式 Gradle 建置已通過。

新存檔 schema 5；舊 schema 2／3／4 自動遷移，當月 AP 與舊進度保留。升級前備份玩家存檔，降版前恢復升級前備份；不要刪除玩家進度。

播放器預期春《春風入城》、夏《炎日運籌》、秋《暮秋望陣》、冬《寒夜圖謀》四首本機 MP3；本次不提供音檔。換季使用兩秒 Crossfade，不等於音檔自身循環無縫。使用者自行補入前，必須先確認曲目權利與公開散布條件。

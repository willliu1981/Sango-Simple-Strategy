# Sango 音樂與音效

目前遊戲使用 MP3 背景音樂，以及程序化產生的 OGG 音效。這些檔案已納入 repository，並會封裝進 APK 與 Windows ZIP。

## 程式使用的音樂

六首皆可在音樂鑑賞中播放，曲目清單與目前播放標題同時顯示中文、英文。

| 遊戲位置 | 中文曲名 | 英文曲名 | 遊戲資產 | 曲長（秒） |
| --- | --- | --- | --- | --- |
| 主選單 | 守住這裡 | Hold This Ground | `music/lobby_theme.mp3` | 208.760 |
| 勝利／破關 | 同一片天空 | Under the Same Sky | `music/strategy_theme.mp3` | 117.600 |
| 春季 | 遠方的燈火 | Lights in the Distance | `music/spring_wind_enters_city.mp3` | 239.440 |
| 夏季 | 田間四季 | Seasons in the Fields | `music/summer_counsel.mp3` | 239.840 |
| 秋季 | 夜還很長 | A Long Night Ahead | `music/autumn_battlefield.mp3` | 239.840 |
| 冬季 | 冬日長河 | The Winter River | `music/winter_stratagem.mp3` | 233.600 |

音檔取自開發者提供的 `suno v6 (商用)` 資料夾；原始檔名為「中文曲名 _ 英文曲名.mp3」。六首均完整複製，未裁切、轉碼或修改音檔標籤。遊戲沿用既有資產路徑，`strategy_theme.mp3` 對應勝利曲；一般戰局依月份播放四季曲。

- 舊的 `music/lobby_theme.ogg` 與 `music/strategy_theme.ogg` 已從 repository 移除，目前程式改用上述 MP3。
- `sfx/*.ogg`：由 `tools/generate-prototype-audio.py` 以基本波形與程序節奏合成的音效。

## 來源與公開範圍

BGM 作者頁面：[willliu1981 · Suno](https://suno.com/@willliu1981)（作者提供）。此為個人頁連結，不代表全部曲目皆已公開，也不取代逐曲來源或下載憑證；頁面其他作品不自動納入本專案授權。

作者已確認本版六首 MP3 為 Suno Pro 方案正式下載的音樂，並在其有權授予的範圍內允許他人下載、修改、再散布與商用，包括其他遊戲用途。此許可不涵蓋舊版音樂。

完整條件請見 [使用授權](../../LICENSE.md) 與 [第三方聲明](../../THIRD_PARTY_NOTICES.md)。商用許可不是排他著作權或不侵權保證，也不是 Suno 對所有下游用途的保證；來源聲明依作者資訊記錄，建置成功不等於授權稽核。

- `seasonal_manifest.json`：四季曲目的來源檔名、遊戲路徑、中英名稱、曲長與 SHA-256 紀錄，不是授權證明。
- `docs/0.6.0/` 內的曲名與音檔檢查報告是歷史紀錄，不代表本次替換的音檔；目前曲目以本頁與 `seasonal_manifest.json` 為準。舊驗證報告不是遊戲執行必需檔，解碼檢查也不等同聽感或遊戲播放驗證。

## 重新產生程序化素材

```powershell
python tools/generate-prototype-audio.py
```

腳本需要系統可執行 `ffmpeg`。產生結果是功能驗證素材，不會產生或覆寫上述 MP3；重新產生舊 OGG 不會讓目前程式自動改回使用它們。

# Sango 音樂與音效

目前遊戲使用 MP3 背景音樂，以及程序化產生的 OGG 音效。已建置的本機 APK 與 Windows ZIP 包含這些 MP3；Git 是否追蹤音檔，不會決定建置時是否將它們封裝。

## 程式使用的音樂

- `music/lobby_theme.mp3`：大廳背景音樂，也可在音樂鑑賞中播放。
- `music/spring_wind_enters_city.mp3`、`summer_counsel.mp3`、`autumn_battlefield.mp3`、`winter_stratagem.mp3`：依月份切換的四季戰局音樂，也可在音樂鑑賞中播放。
- `music/strategy_theme.mp3`：音樂鑑賞中的曲目；一般戰局使用四季音樂。
- 舊的 `music/lobby_theme.ogg` 與 `music/strategy_theme.ogg` 已在本機移除，目前程式改用上述 MP3。
- `sfx/*.ogg`：由 `tools/generate-prototype-audio.py` 以基本波形與程序節奏合成的音效。

## 來源與公開範圍

依現有來源紀錄，MP3 為開發者提供的 Suno 生成音樂。公開原始碼與公開音樂素材是不同的散布範圍；音檔包含在 APK 或 Windows ZIP 中，也應納入發布前的素材確認。

目前 `THIRD_PARTY_NOTICES.md` 仍記錄公開散布授權待確認，不能將本機建置成功視為完成授權確認。來源與權利狀態請參閱專案根目錄的 [第三方聲明](../../THIRD_PARTY_NOTICES.md)；本文件不新增或變更授權結論。

- `seasonal_manifest.json`：四季曲目的來源檔名、遊戲路徑、顯示名稱與 SHA-256 紀錄，不是授權證明。
- `docs/0.6.0/validation/audio-validation.json`：本機產生的音檔檢查報告，不是遊戲執行必需檔。報告中的解碼檢查不等同聽感或遊戲播放驗證。

## 重新產生程序化素材

```powershell
python tools/generate-prototype-audio.py
```

腳本需要系統可執行 `ffmpeg`。產生結果是功能驗證素材，不會產生或覆寫上述 MP3；重新產生舊 OGG 不會讓目前程式自動改回使用它們。

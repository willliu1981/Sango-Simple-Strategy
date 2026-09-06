# Sango Prototype Audio

本目錄音訊由 `tools/generate-prototype-audio.py` 以基本波形、五聲音階與 48 beat 程序化循環自行合成，專供 Sango Prototype 使用；沒有取樣或改編任何既有遊戲、影視或商業樂曲。

- `music/lobby_theme.ogg`：Lobby 循環背景音樂。
- `music/strategy_theme.ogg`：戰略地圖、內政與戰報循環背景音樂。
- `sfx/*.ogg`：介面、存檔、月底、戰鬥與目標提示音效。

需要重新產生時：

```powershell
python tools/generate-prototype-audio.py
```

腳本需要系統可執行 `ffmpeg`。產生結果是功能與授權風險可控的 Prototype 素材，不等同正式商業配樂或專業 Foley。

# Sango Prototype Audio

目前本目錄音訊由 `tools/generate-prototype-audio.py` 以基本波形、五聲音階與 48 beat 程序化循環自行合成，專供 Sango Prototype 使用；沒有取樣或改編任何既有遊戲、影視或商業樂曲。

- `music/lobby_theme.ogg`：Lobby 循環背景音樂。
- `music/strategy_theme.ogg`：戰略地圖、內政與戰報循環背景音樂。
- `sfx/*.ogg`：介面、存檔、月底、戰鬥與目標提示音效。

需要重新產生時：

```powershell
python tools/generate-prototype-audio.py
```

腳本需要系統可執行 `ffmpeg`。產生結果是功能與授權風險可控的 Prototype 素材，不等同正式商業配樂或專業 Foley。

## 未來以 Suno BGM 取代音樂時

若未來以 Suno 生成的檔案取代 `music/*.ogg`，音效 `sfx/*.ogg` 仍應維持程序化來源。Suno BGM 在以下條件確認前，不得放入本目錄或公開 release：曲目在 Pro 或 Premier 訂閱有效期間生成、經 Suno 核准管道下載、所有輸入素材均具有足夠權利，且不是免費／Basic 方案或他人作品的延伸／Remix。

完整的公開散布政策、權利來源紀錄與免責邊界，請見專案根目錄的 `README.md` 與 `THIRD_PARTY_NOTICES.md`。AI 生成 BGM 不因放入本目錄而自動取得 Sango 原始碼授權、著作權保護或排他性保證。

> 這是 **0.4.0 歷史驗證紀錄**，不是本次 0.5.0 的測試結論。新版請看 [VALIDATION_0.5.0.md](VALIDATION_0.5.0.md)。

# Validation record — 0.4.0

## 交付環境已通過

### Java 17 編譯

- 所有 `core/src/main/java` 以 `javac --release 17 -Xlint:all` 編譯成功。
- 所有 `core/src/test/java` 以相同規則編譯成功。
- 編譯結果沒有 warning。

### Vertical Slice smoke test

`VerticalSliceSmokeTest` 已執行成功，輸出：

```text
Sango 0.4.0 battle report and flow smoke test: PASS
```

涵蓋：

- 三個可選勢力的新局建立。
- 開墾、商業、治水、城防、徵兵與訓練規則。
- 行動力不足拒絕。
- 每月軍糧、三月季稅、六月洪災、九月秋收。
- 偵察、出征、戰術、守城與攻城。
- 戰報建立、戰鬥 ID、傷亡、控制權與未讀狀態。
- `MarkBattleReportReadCommand` 保存已讀狀態。
- 原目標達成後仍可繼續遊玩。
- 期限失敗後仍可恢復行動力並繼續遊玩。
- 首都失守但仍有其他城池時遷都並繼續遊玩。
- 失去全部城池後進入 `ELIMINATED`。
- 三個存檔槽互相隔離。
- primary 損壞時可辨識並載入 backup 復原候選。
- schema 2 → schema 3 migration。
- 存檔讀取結果與 Repository 內部狀態隔離。
- 音訊 enum 引用的資產全部存在。

### SimpleUI XML

下列 8 份 XML 已通過 SimpleUI 1.1.2 XSD validator：

```text
assets/ui/lobby.xml
assets/ui/new_game.xml
assets/ui/strategic_map.xml
assets/ui/city.xml
assets/ui/month_report.xml
assets/ui/battle_report.xml
assets/ui/save_load.xml
assets/ui/settings.xml
```

另已檢查：

- Java Screen 引用的 Actor ID 全部存在於對應 XML。
- Button／Label 的 Java cast 與 XML 元件類型相符。
- XML、Java 與 Definition 需要的 i18n key 均存在。
- i18n key 沒有重複。
- 所有必要非 ASCII glyph 已納入 `assets/characters/default.txt`。

### 音訊資產

使用 `ffprobe` 驗證：

- 2 首 BGM 與 12 個 SFX 均可解析為 OGG Vorbis。
- 取樣率為 44.1 kHz、mono。
- Lobby BGM 約 40 秒，Strategy BGM 約 32.73 秒；兩者皆使用 48 beat 完整循環週期。
- SFX 長度約 0.14～1.55 秒。
- `MusicTrack`／`SoundEffect` 列舉中的每一個路徑都有實體檔案。

### Patch 與 Source ZIP

交付產物已完成下列可重現驗證：

- `git diff --check` 與 `git diff --cached --check` 通過。
- 對乾淨的 0.3.0 baseline commit `e90c437` 執行 `git apply --check` 與正式套用均成功。
- 套用 Patch 後重新完成 Java 17 主程式／測試編譯、smoke test、8 份 XML、i18n、Actor ID、glyph、MessageFormat placeholder 與 14 份音訊檢查。
- Source ZIP 通過 ZIP CRC 完整性檢查；解壓後重新完成與 Patch 相同的靜態編譯及資產驗證。
- Source ZIP 已確認不包含 `.git`、`.idea`、`.gradle`、Build 輸出、`local.properties`、簽署金鑰、開發機字型二進位檔或自動生成的 `assets/assets.txt`。
- Patch 與 Source ZIP 均另外提供 SHA-256 校驗檔。

最終靜態檢查統計：

```text
i18n required=335, existing=346, duplicates=0
supplemental glyph required=464, provided=483
audio enum music=2, effects=12
MessageFormat direct calls checked=205
```

## 開發機建議驗證

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean core:check core:compileJava lwjgl3:dist
.\gradlew.bat lwjgl3:run
.\gradlew.bat android:assembleDebug
```

建議手動確認：

1. Lobby BGM 會播放，設定開關與音量立即生效。
2. 「讀取存檔」可開啟三槽畫面；讀取後進入正確年月、勢力與城池狀態。
3. 新局可指定槽位，覆寫既有槽位前會確認。
4. 地圖與內政的「結束本月」都會先確認。
5. 在目前管理城池發生戰鬥時，會出現「稍後觀看／觀看戰報」。
6. 選擇稍後觀看後，地圖對應城池顯示未讀戰事數與脈衝提示。
7. 戰報畫面顯示雙方兵力、傷亡、勝負與控制權，並可切換下一份未讀戰報。
8. 關閉程式再開啟後，未讀／已讀戰報狀態、目前槽位與戰局資料都能恢復。
9. 十二個月目標失敗後仍有 `3 / 3` 行動力，可繼續內政、偵察、出征與結束月份。
10. 原首都失守但尚有城池時，自動遷都且不停止遊戲。
11. 失去全部城池後才真正停止操作。
12. 地圖／內政的設定可保存、返回 Lobby 與退出；返回前保存失敗時不應離開戰局。
13. Android Back／Desktop ESC 在各 Screen、Dialog 與 Overlay 的順序正確。
14. Android pause/resume 後 BGM 依設定恢復，退出後音訊資源釋放。

## 尚未在交付環境完成

交付環境沒有可用的 Gradle 8.8 distribution cache，也無法在該環境完成 Gradle dependency resolution，因此尚未實際：

- 啟動 LWJGL3 視窗。
- 以真實 OpenAL backend 播放音訊。
- 組裝 Android APK。
- 實機驗證 Android lifecycle 與 Back 行為。

這些項目必須在開發機完成最後 Runtime 驗證；靜態編譯與 headless smoke test 不能取代實際畫面、輸入及音訊測試。

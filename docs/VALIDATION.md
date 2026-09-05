# Validation record — 0.2.0

## 已通過

- Java 17：所有 `core/src/main/java` 以 `javac --release 17 -Xlint:all` 編譯。
- Java 17：`VerticalSliceSmokeTest` 編譯並執行成功。
- Definition JSON 載入與 ID 關聯驗證。
- 新局建立：劇本、勢力、主城與初始資源。
- 三次內政操作後的資源、行動力與城池數值。
- 行動力歸零後拒絕第四次命令。
- 結束回合：回合、月份與行動力恢復。
- JSON 保存後重新載入，數值一致；修改讀取結果不會回寫 Repository。
- 主要存檔故意損壞後，`backup` 可被識別為復原候選並讀回上一個完整狀態。
- 刪除存檔槽後回到 `EMPTY`。
- SimpleUI 1.1.2 XSD validator：
  - `assets/ui/lobby.xml`
  - `assets/ui/new_game.xml`
  - `assets/ui/city.xml`
- XML／Java／Definition 引用的 i18n key 完整性。
- i18n 值所需的非 ASCII glyph 已納入 `assets/characters/default.txt`。
- `assets/assets.txt` 已重新產生。

## 開發機建議驗證

```powershell
.\gradlew.bat clean core:check core:compileJava lwjgl3:dist
.\gradlew.bat lwjgl3:run
.\gradlew.bat android:assembleDebug
```

交付環境沒有可用的 Gradle 8.8 distribution cache，無法在容器內啟動 LWJGL3 或組裝 APK；因此實際畫面配置與 Android lifecycle 仍需在開發機完成最後 runtime 驗證。

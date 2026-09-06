# Validation record — 0.3.0

## 已通過

- Java 17：所有 `core/src/main/java` 以 `javac --release 17 -Xlint:all` 編譯成功。
- Java 17：`VerticalSliceSmokeTest` 編譯並執行成功。
- Definition JSON：劇本、五個勢力、六座城、六個地圖節點與六條道路載入及交叉引用驗證。
- 新局：三個可選勢力各自對應起始城與勝利目標。
- 延遲經濟：
  - 開墾支付金、增加農業，但不立即增加糧。
  - 商業開發支付金、增加商業，但不立即增加金。
  - 治水支付金並降低洪災風險。
- 行動力：三次命令後歸零，第四次命令遭拒絕。
- 每月軍糧：依總兵力扣除，不會在普通月份憑空增加商業收入或糧食。
- 三月季末商稅結算。
- 六月 deterministic 洪災：人口、農業、民心與秋收倍率變化。
- 九月秋收：套用洪災倍率，結算後恢復下一年度倍率。
- 偵察：相鄰目標精確情報有效期。
- 第一次出征失敗：守軍勝利、攻方生還者返回起點。
- 徵兵整備後第二次出征：攻下指定目標城並取得戰役勝利。
- 敵軍集結與建立行軍部隊。
- 玩家主城失守：即使仍擁有其他城池，Micro Campaign 仍正確判定敗北並更新替代首都。
- 十二個月期限敗北。
- JSON 保存／讀取：讀取結果與 Repository 內資料隔離。
- 主要存檔故意損壞後，`backup` 可被識別為復原候選並載入上一個完整狀態。
- SimpleUI 1.1.2 XSD validator：
  - `assets/ui/lobby.xml`
  - `assets/ui/new_game.xml`
  - `assets/ui/strategic_map.xml`
  - `assets/ui/city.xml`
- XML、Java 與 Definition 使用的 i18n key 完整性檢查。
- i18n 與 Java 顯示字串所需的非 ASCII glyph 已納入 `assets/characters/default.txt`。
- `assets/assets.txt` 重新產生並排序。
- Source ZIP 解壓後重新進行 Java 17 編譯及 smoke test。
- 0.2.0 → 0.3.0 Git patch 已進行 `git apply --check`。

## 開發機建議驗證

```powershell
.\gradlew.bat --stop
.\gradlew.bat clean core:check core:compileJava lwjgl3:dist
.\gradlew.bat lwjgl3:run
.\gradlew.bat android:assembleDebug
```

建議手動確認：

1. 建立曹操、劉備、孫策三種新局，地圖起始城與目標城正確。
2. 地圖節點、道路與 16:9 resize 後位置正常。
3. 城池內政按鈕中文字、disabled 狀態與返回導覽正常。
4. 結束月份後報告遮罩可開啟、關閉，Android Back／Desktop ESC 優先關閉報告。
5. 偵察前後敵城情報顯示不同。
6. 出征後地圖顯示行軍部隊，抵達後城池顏色與控制權更新。
7. 主城失守、期限到期與攻下目標的按鈕鎖定及文字正確。
8. 關閉程式再開啟後，「繼續遊戲」恢復相同月份、資源、城池與軍隊。

交付環境沒有可用的 Gradle 8.8 distribution cache，無法在容器內啟動 LWJGL3 視窗或組裝 APK；因此實際畫面配置、輸入行為與 Android lifecycle 仍需在開發機完成最後 runtime 驗證。

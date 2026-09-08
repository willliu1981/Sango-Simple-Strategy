# Sango 0.5.0 驗證紀錄

## 基準與交付狀態

基準為 `Sango-0.4.0-runtime-battle-report-fix-source.zip`，SHA-256：

`182e27af9e6c7f0063c7697f2e6b36d1424c03f13d6455ff018d3a36663a04e1`

本版程式與無圖形回歸已完成，但 **尚未通過使用者 Windows LWJGL3／OpenAL／Android Runtime 驗證**。不要把下列單元／整合測試或 XSD 通過，誤認成實際視窗、音效與手勢已經實測正常。

## 真實執行環境

- Linux，OpenJDK **21.0.11**。
- 編譯使用 `javac --release 17 -Xlint:all -encoding UTF-8`：限制 Java 17 語法與標準 API、產出 Java 17 class。
- 79 個 Core main Java 與 3 個 test Java 完整編譯，exit 0，沒有 warning。
- 測試實際執行於 JDK 21，不是 JDK 17 Runtime。
- 手動編譯 classpath 使用本專案既有 `libs/simpleui-1.1.2.jar` 內可用的依賴。正式 Gradle 會剝除 JAR 內的 bundled GDX，再解析設定的 LibGDX 1.13.1；兩者不等於完全相同的依賴解析驗證。
- 專案設定仍為 Java 17、LibGDX 1.13.1、SimpleUI 1.1.2、Gradle 8.8，沒有偷偷升級依賴版本。

## 已執行並通過

```text
Compile exit: 0
Sango legacy campaign regression (schema 4): PASS
Sango national campaign regression: PASS; assertions=9510; unopposed combinations=18; simulated months=288
Sango UI resources and map camera: PASS; checks=1348
```

### 舊六城回歸

`VerticalSliceSmokeTest`：六城 Definition、季節收入／洪災／軍糧、偵察與出征、戰報 ID 與損失、已讀保存、期限失敗後自由征戰、遷都、全部失城後滅亡、三槽保存與 backup 復原、schema 2 → 4、音訊資產存在及 Ogg 標頭。

因加入士氣，兩個既有戰鬥測試的確定性預期值同步更新；不是刪掉斷言來略過差異。

### 新版國家級劇本與軍事

`NationalCampaignSmokeTest`：

- 六個可選勢力各自建局、每次有 42 城／8 個勢力；固定所有權、各自兩座初始領地、目標相鄰、68 條道路且全圖連通。
- 徵兵士氣扣除、訓練恢復、上下界、滿訓練但未滿士氣、合法零士氣的出征與存讀、copy-on-write 保存失敗不改原物件。
- 士氣 0／50／100 與城防奇偶值、強攻倍率、大數中間運算、部隊品質加權；同兵力下改變士氣能改變戰鬥勝負。
- 3 種目標狀態 × 3 種戰術 × 2 種城池歸屬，共 18 組空城佔領。驗證零戰鬥傷亡、所有權、駐軍、城防與民心損耗、士氣快照、下月 3 行動力、已讀與存讀。
- 五個未選勢力與黃巾會各自行動，黃巾滅亡後其他 AI 不停止；首都失守遷都、全部失城停止、滅亡在途軍隊解散。
- 用實際 0.4.0 serializer 產生的合成 schema 3 fixture 讀入；保留六城／讀報狀態／軍隊零士氣／目標狀態與行動力，不捏造歷史士氣。唯讀 load 不改原檔；首次保存保留原始 schema 3 backup；primary 損壞可由舊 backup 遷移復原。拒絕 schema 1 與未來未知版本。
- 六個勢力各推進 48 月，合計 288 月；每月驗證狀態、事件引用與各勢力軍隊數，再保存並讀回。這項測試刻意提高玩家初始駐軍與軍糧，以完整跑滿時程，是結構壓力測試，**不是遊戲難度或自然遊玩平衡測試**。

9510 是執行中的斷言次數，包含迴圈重複檢查，不代表 9510 個獨立案例。

### XML／i18n／相機

`UiResourceSmokeTest`：8 個畫面 XML 均以專案 SimpleUI 1.1.2 JAR 的 `simpleui.xsd` 驗證；XML ID 去重、XML 與 Java 的固定 i18n 引用、Screen 與固定 Actor ID 引用、六勢力按鈕、內政設定位置、月報捲動容器、淘汰檔不存在。

相機測試包含座標往返、指標錨點縮放、平移換算、全圖包含邊界、縮放上下界、平移邊界、非有限數值與零尺寸輸入。沒有建立真正 Graphics Context，不評估文字大小、最終畫面裁切、原生觸控手勢或與視窗 DPI 的互動。

### 發佈一致性

- `git diff --check` 通過。
- 以精確基準重新解壓後執行 `git apply --check` 與 `git apply`，並逐檔比較更新內容。
- 清理 Patch 確實包含未使用樣板 Screen 與四個預設圖示的刪除。
- ZIP 排除 `.git`、快取、編譯輸出、SDK 本機設定、產生的 assets index、字型檔與其獨立 bitmap 圖片、簽章金鑰。
- 舊存檔相容性 Definition、授權文字、歷史文件與原音訊不當成無用檔案刪除。

## 受環境限制未完成

`bash gradlew core:check --no-daemon` 嘗試取得 Gradle 8.8 時失敗：

`java.net.UnknownHostException: services.gradle.org`

因此本次不能宣稱 `gradlew core:check` 完整通過，也未啟動 LWJGL3 視窗或 OpenAL、未產生 Android APK、未測 Android pause／resume／Back 與雙指手勢。PowerShell 字型複製工具也尚未在 Windows PowerShell 實機執行。

## 本機驗證順序

先備份 `.sango/save`。以 Patch 更新後，在實際 Java 17 環境執行：

```powershell
.\gradlew.bat clean core:check
.\gradlew.bat lwjgl3:run
.\gradlew.bat android:assembleDebug
```

主要操作檢查：

1. 在新槽建局，確認六勢力按鈕、42 城與各自兩城；全圖、定位、滾輪及拖曳可操作，拖曳不誤觸選城；節點不越過地圖區域攔截其他按鈕。
2. 進入內政確認「設定」在最左、士氣可見。徵兵扣 5 士氣；訓練加 5，訓練滿值仍可恢復士氣。
3. 無守軍城市在任務進行、成功與失敗後皆可佔領，顯示無抵抗紀錄且零戰鬥傷亡。缺糧前置逃兵須與戰鬥傷亡分開看。
4. 多 AI 月份後開啟長月報，確認可捲動；第三方戰爭顯示實際攻守勢力，不誤稱我軍；戰報返回月報不重現 yellow_turban City lookup crash。
5. 讀取真實舊 schema 2／3 存檔，確認保持原地圖與戰報；保存後再讀，檢查士氣與已讀；不要為測新版地圖覆寫舊槽。
6. 測試真實 UI 字型、設定／返回路徑、BGM／SFX 音量與切換、Desktop ESC，以及 Android 觸控、Back、pause／resume。

完成以上本機測試後才 Commit；有錯誤時保留完整 stack trace 與觸發操作，不以刪存檔作為預設修復手段。

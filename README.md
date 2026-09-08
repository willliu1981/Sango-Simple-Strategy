# Sango - 簡易三國志

`Sango` 是一款以漢末群雄割據為背景的回合制區域戰略遊戲。玩家需要經營城池、維持軍糧與民心、調動部隊，並在有限時間內完成劇本目標。

目前版本：`0.6.0` 早期開發版

GitHub 專案：[Sango-Simple-Strategy](https://github.com/willliu1981/Sango-Simple-Strategy)

## 遊戲特色

- 42 座城池的全國戰略地圖，可拖曳、縮放及快速定位。
- 劉備、曹操、袁紹、孫策等勢力與電腦控制的敵對勢力。
- 城池內政：農業、商業、治水、訓練、徵兵與安民。
- 共用金、糧與每月軍糧需求，缺糧會造成逃兵及其他負面影響。
- 偵察、運兵、出征、行軍時間與安全道路判定。
- 多路部隊可同時行動；同勢力、同目標、同月份抵達時合併為聯合作戰。
- 戰敗撤退、天下戰報、勢力月報與分城參戰明細。
- 三個存檔槽，個別保存所選城池與地圖視角。
- 支援 Windows 與 Android。

完整規則請見 [遊戲玩法說明](docs/gameplay.md)。

## 下載與安裝

正式測試版應放在 GitHub repository 的 **Releases**，不要把 APK 或 Windows 成品直接提交進 Git history。

本遊戲以免費分享為目的。預計提供可直接遊玩的 Android APK 與 Windows ZIP，不另外提供獨立素材包。成品以 [Releases 下載頁](https://github.com/willliu1981/Sango-Simple-Strategy/releases) 實際發布的檔案為準。

背景音樂使用 Suno 免費方案生成（Music generated with Suno），來源與限制見下方「音訊與素材」。下載頁文案見 [發布說明草稿](docs/release-notes-0.6.0.md)。

請由上方 Releases 下載頁查看已發布的版本與附件。0.6.0 測試包的建議檔名如下，實際名稱以附件列表為準：

```text
Sango-0.6.0-Android-debug.apk
Sango-0.6.0-Windows-x64.zip
SHA256SUMS.txt
```

### Android

1. 從 Releases 下載 `Sango-0.6.0-Android-debug.apk`。
2. 在 Android 系統允許目前瀏覽器或檔案管理員「安裝未知應用程式」。
3. 開啟 APK 完成安裝。

本次提供 debug 簽章測試版，可側載安裝。直接開啟 APK 通常只需允許安裝未知應用程式，不需要開啟開發者選項；透過 USB／ADB 安裝才需要開啟開發者選項與 USB 偵錯。

更新安裝需要與舊版相同的簽章。若出現簽章不符，請先備份存檔再處理；移除應用程式可能會清除遊戲資料。未來正式 release 版應使用固定且妥善保管的 release signing key。

### Windows

1. 從 Releases 下載 `Sango-0.6.0-Windows-x64.zip`。
2. 將 ZIP 完整解壓縮到可寫入的資料夾。
3. 執行資料夾內的 `Sango.exe`，不要直接從壓縮檔內啟動。

ZIP 已內含 Java runtime，無須另外安裝 Java。保留解壓後的完整資料夾，不能只搬移 `Sango.exe`。

Windows 存檔位於：

```text
%USERPROFILE%\.sango\save\slot-01.json
%USERPROFILE%\.sango\save\slot-02.json
%USERPROFILE%\.sango\save\slot-03.json
```

## 存檔與讀取

- 內政、偵察、運兵、出征及閱讀戰報只更新當前戰局，不立即寫入存檔。
- 讀取存檔會回到上次保存的狀態，不會先保存當前進度；可用來取消尚未保存的操作。
- 新局建立、月份結算、手動存檔，以及從遊戲選單返回大廳或離開遊戲時會保存進度。
- 地圖與內政畫面切換、應用程式暫停不會自動保存。強制關閉後，尚未保存的進度不會保留。
- 月份結算已寫入存檔後，讀取同一槽無法回到結算前；需要保留較早進度時，先手動存到另一槽。

## 從原始碼執行

GitHub 公開版本為排除素材的原始碼快照，不附音檔、圖片、字型 binary 或 `libs/simpleui-1.1.2.jar`。自行建置前需取得合法可用的對應資產與 SimpleUI 依賴，並放回專案要求的位置；僅下載原始碼無法直接建置完整遊戲。

需求：

- JDK 17
- Android SDK 34（僅 Android 建置需要）
- 專案要求的本機中文字型資產

準備本機資產：

```powershell
.\tools\prepare-local-assets-0.6.0.ps1 -SourceProject "<原 Sango 專案路徑>"
```

執行 Windows 桌面版：

```powershell
.\gradlew.bat :lwjgl3:run
```

建立可側載的 debug 測試 APK：

```powershell
.\gradlew.bat :android:assembleDebug
```

輸出位置：

```text
android/build/outputs/apk/debug/android-debug.apk
```

## 製作可發布檔案

### Android APK

本次使用 debug 建置，Gradle 會以 debug key 簽署：

```powershell
.\gradlew.bat :android:assembleDebug
```

將 `android/build/outputs/apk/debug/android-debug.apk` 複製並命名為 `Sango-0.6.0-Android-debug.apk`，作為測試版 Release 附件。不要上傳 keystore、密碼或 signing properties。

日後製作正式版時，再於 Android Studio 使用 `Build > Generate Signed Bundle / APK` 與固定 release key；正式版檔名使用 `Sango-0.6.0-Android.apk`，避免與 debug 測試包混淆。

### Windows x64 ZIP

```powershell
.\gradlew.bat :lwjgl3:packageWinX64
```

Construo 會在下列目錄產生可直接上傳的 ZIP，內含 Windows 執行檔與必要 Java runtime：

```text
lwjgl3/build/construo/dist/
```

上傳前將成品統一命名為：

```text
Sango-0.6.0-Windows-x64.zip
```

## 發布到 GitHub Releases

1. 將確認過的原始碼建立 `v0.6.0` tag。
2. 在 GitHub repository 開啟 **Releases > Draft a new release**。
3. 選擇 `v0.6.0`，標題填寫 `Sango 0.6.0 測試版`，勾選 Pre-release。
4. 上傳 `Sango-0.6.0-Android-debug.apk`、`Sango-0.6.0-Windows-x64.zip` 與 `SHA256SUMS.txt`。
5. 在 release notes 說明新增內容、已知問題、存檔相容性與安裝方式。
6. 實機確認下載、解壓縮、安裝、啟動、讀檔與更新流程後再 Publish。

GitHub 自動提供的 `Source code (zip)` 只是原始碼快照，不是可執行的 Windows 版本；玩家應下載你另外上傳的 `Sango-0.6.0-Windows-x64.zip`。

## 音訊與素材

- 依開發者提供的來源資訊，目前 BGM 使用 [Suno](https://suno.com/) 免費方案生成並下載。本遊戲旨在免費分享，並未因標示來源而取得額外授權。
- Suno 免費方案限合法、個人及非商業用途；官方說明未明確確認將這些曲目隨免費遊戲提供下載的情況。目前未取得針對此用途的額外授權確認。
- 不另外提供獨立素材包。音樂、圖像及字型不因收錄於遊戲或公開原始碼而取得可供他人獨立取用、販售或再散布的授權。
- 音檔與圖資不納入一般原始碼 commit；正式成品是否包含素材，取決於發布版本的打包內容。
- 將 Suno 或其他來源的 BGM 放進 APK／ZIP 仍屬於散布，免費提供不會自動取得使用或再散布權。
- 發布前應保留每首曲目的生成帳號、方案、日期、曲目 ID、下載紀錄與當時適用條款。
- 收到具體權利爭議或無法證明來源時，應先下架或替換相關素材。

更多依賴與素材聲明請見 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

官方依據：[Suno 服務條款](https://about.suno.com/terms)、[免費方案權利說明](https://help.suno.com/en/articles/9601601)。條款查閱日期：2026-09-08。本段為來源與用途聲明，不代表 Suno 背書或已核准本遊戲的散布用途。

## 授權與發布狀態

本 repository 目前尚未提供專案層級的 `LICENSE`，因此公開 repository 不等於允許他人任意複製、修改或再散布原始碼。

正式公開前仍需完成：

- 決定 Sango 自有程式碼採用的授權條款。
- 確認 `libs/simpleui-1.1.2.jar` 的授權與 binary 再散布權；目前專案未附該依賴的授權檔。
- 確認所有打包進 APK／ZIP 的字型、音訊、圖像與第三方 binary 均可合法散布。
- 將所有必要 notice 與 license 一併放入 release 成品。

本 README 與第三方聲明僅整理專案狀態與風險，不構成法律意見或權利保證。

## 開發文件

- [遊戲玩法說明](docs/gameplay.md)
- [第三方與素材聲明](THIRD_PARTY_NOTICES.md)
- [0.6.0 驗收清單](docs/0.6.0/acceptance-checklist.md)

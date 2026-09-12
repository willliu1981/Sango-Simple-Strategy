# Sango《征服世界》

一款從三國延伸至世界的回合制城池征服遊戲。

選擇劉備、曹操、袁紹、孫策等勢力，經營城池、維持軍糧與民心、調動部隊，在期限內完成劇本目標，或繼續征戰天下。

目前原始碼版本：**0.6.3 早期測試版**。下載附件以 Releases 實際發布內容為準。

## 遊戲影片

[▶ 觀看《征服世界》實際遊玩影片](https://youtu.be/RraldBojNZg)

## 下載遊戲

請前往 [Releases 下載頁](https://github.com/willliu1981/Sango-Simple-Strategy/releases)，選擇適合裝置的附件：

- **Android**：下載 APK。
- **Windows x64**：下載 Windows ZIP。

GitHub 自動提供的 `Source code (zip)` 是原始碼，不是可直接執行的遊戲。

### Android 安裝

1. 下載 Release 附件中的 APK。
2. 依 Android 提示，允許目前瀏覽器或檔案管理員「安裝未知應用程式」。
3. 開啟 APK 完成安裝。

目前提供的是 debug 簽章測試版。一般側載不需要開啟開發者選項；只有透過 USB／ADB 安裝時才需要 USB 偵錯。

### Windows 安裝

1. 下載 Release 附件中的 Windows ZIP。
2. 將 ZIP 完整解壓縮到可寫入的資料夾。
3. 執行資料夾內的 `Sango.exe`。

Windows 版本已包含 Java 執行環境，不需另外安裝 Java。請保留解壓後的完整資料夾，不要直接從 ZIP 內啟動，也不要只搬移 `Sango.exe`。

## 遊戲特色

- 可選 42 城中原劇本或 72 據點世界劇本，地圖可拖曳、縮放、定位與全螢幕查看。
- 世界劇本將不同時空的地名與虛構勢力納入同一張地圖，主目標為在期限內消滅指定勢力。
- 城池內政包含農業、商業、治水、城防、訓練、徵兵與巡查。
- 金、糧由勢力共用；人口、訓練、士氣、民心與城防由各城管理。
- 偵察敵情、運兵增援、單城出征與多城聯合作戰；攻方採「強攻、誘敵、穩進」，守方採「迎擊、設伏、據守」。
- 各勢力依上月底城市快照同時規劃；相向部隊先在道路接戰，勝方再依命令繼續攻城或返城。
- 行軍、撤退、安全道路、軍糧需求、缺糧逃兵與季節災害。
- 勢力月報、天下戰報及各來源城的參戰明細。
- 三個存檔槽，個別保存戰局、選取城池與地圖視角。
- 四季戰局音樂與獨立音樂鑑賞。

## 基本玩法

每個月可以依序進行：

1. 在戰略地圖查看攻下城池或消滅勢力的劇本目標、據點、道路、情報與勢力資源。
2. 進入我方城池發展內政、訓練、徵兵或巡查。
3. 視局勢偵察敵城、運兵增援，或由一座及多座城池出征。
4. 安排完成後結束月份：先完成敵軍命令，再依序結算軍糧、洪災與季節收入、道路接戰、行軍與攻城、撤退、民心與人口，最後推進月份、刷新行動力並建立新的月初快照。

未使用的行動力不會保留。實際成本、兵力限制與效果會顯示在各操作畫面；遊戲內「設定 → 玩法說明」提供通用規則，各主要畫面也有對應的操作說明。

更完整的數值與戰鬥規則請見 [遊戲玩法與操作說明](docs/gameplay.md)。

## 存檔與讀取

- 一般內政、偵察、運兵、出征與閱讀戰報不會立即存檔。
- 讀取存檔不會先保存當前進度，可用來取消尚未保存的操作。
- 建立新局、月份結算、手動存檔，以及從遊戲選單返回大廳或離開遊戲時會保存進度。
- 強制關閉程式不會保存尚未存檔的操作。
- 月份結算後，讀取同一槽無法回到結算前；想保留較早進度，請先存到另一個槽位。

Windows 存檔位於 `%USERPROFILE%\.sango\save\`。

## 測試版狀態

0.6.3 仍是早期測試版，遊戲規則、平衡與介面會持續調整。

- 已完成程式回歸檢查。
- 本次 BGM 更新已完成音檔解碼、音樂與介面資源檢查，以及 Android／Windows 編譯。
- 新版 APK／Windows ZIP 須另行建置；本次新音樂尚未完成手機與桌面實際播放驗證。

遇到問題時，歡迎在 GitHub 回報操作步驟、裝置或 Windows 版本，以及相關截圖。

---

## 開發者與原始碼資訊

公開 repository 已包含目前遊戲使用的音訊、圖片、預設介面素材與 `libs/simpleui-1.1.2.jar`。中文字型 binary 未納入 Git；只下載 GitHub 自動產生的 Source code ZIP 後，仍需準備合法的本機中文字型與建置環境，才能建立完整遊戲。

<details>
<summary>本機建置方式</summary>

需求：

- JDK 17
- Android SDK 34（僅 Android 建置需要）
- `SourceHanSansCN-Regular.otf` 與 `SourceHanSansCN-Heavy.otf` 的合法本機來源

準備本機中文字型（來源可為包含上述字型的 ZIP 或目錄）：

```powershell
.\tools\prepare-local-fonts.ps1 -StudyRoutineSource "<字型來源 ZIP 或目錄>"
```

執行桌面版：

```powershell
.\gradlew.bat :lwjgl3:run
```

建立 Android debug APK：

```powershell
.\gradlew.bat :android:assembleDebug
```

建立 Windows x64 ZIP：

```powershell
.\gradlew.bat :lwjgl3:packageWinX64
```

</details>

## 原始碼與素材聲明

BGM 作者的 Suno 頁面：[willliu1981](https://suno.com/@willliu1981)。此連結為作者個人頁，不是六首曲目的專屬播放清單；音樂使用範圍以本專案授權文件為準。

作者允許重複利用本公開 V1 專案中其有權授權的原始碼與內容，包含修改、再散布及商業用途，條件詳見 [使用授權](LICENSE.md)。此授權不涵蓋私人獨立開發的 V2。

**另行發布衍生 App 時，務必修改程式 package、Android namespace 與 applicationId**，使用自己的識別名稱、簽章與存檔／偏好設定名稱，不能冒充原版。本版的 `idv.kuan.studio.sango` 不可直接沿用於衍生 App；只改顯示名稱不夠。本專案本身的正常版本更新則保留既有 ID。

本次六首 BGM 由作者確認為 Suno Pro 方案正式下載的音樂。作者在其有權授予的範圍內，同意他人下載、使用、修改與商用，包含用於其他遊戲；不是 Suno 對所有第三方用途的無條件保證，也不追溯授權舊版音樂。曲目與中英名稱見 [音樂清單](assets/audio/README.md)。

圖像、字型、SimpleUI 與其他第三方內容仍依各自授權處理，不會因專案開放而自動取得再散布權。Suno 條款依據、限制及來源紀錄請見 [第三方與素材聲明](THIRD_PARTY_NOTICES.md)。

## 相關文件

- [0.6.3 發布說明](docs/release-notes-0.6.3.md)
- [遊戲玩法與操作說明](docs/gameplay.md)
- [0.6.2 發布說明](docs/release-notes-0.6.2.md)
- [0.6.1 發布說明](docs/release-notes-0.6.1.md)
- [0.6.0 發布說明](docs/release-notes-0.6.0.md)
- [0.6.0 驗證紀錄](docs/0.6.0/Sango-0.6.0-VALIDATION.md)
- [專案架構](docs/ARCHITECTURE.md)

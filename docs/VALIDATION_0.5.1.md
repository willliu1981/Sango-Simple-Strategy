# Sango 0.5.1 驗證紀錄

## 本次修正

- 補齊六勢力、42 城與 0.5.x 新增 UI 文案所需的中文字型 glyph。
- `UiResourceSmokeTest` 新增中文字型覆蓋檢查，未來 i18n / data JSON 新增漢字但漏更新 `characters/default.txt` 時直接失敗。
- 禁止 i18n 使用 LibGDX `XmlReader` 無法正確解析的 numeric XML entity（例如 `&#10;`）。
- 徵兵改為兵力 +200、人口 -200、訓練 -5、士氣 -5。
- 徵兵／訓練按鈕由 `CityScreen` 將 i18n 的字面 `\n` 轉為真正換行，避免 `#10` 顯示與文字裁切。

## 已完成驗證

- Java 17 target：Core main + test 全量 `javac --release 17 -Xlint:all`：PASS。
- `VerticalSliceSmokeTest`：PASS。
- `NationalCampaignSmokeTest`：PASS（9512 assertions、18 組無抵抗佔領、288 模擬月份）。
- `UiResourceSmokeTest`：PASS（4753 checks，含 glyph / numeric entity regression）。
- 以 LibGDX `XmlReader` 直接解析 i18n，確認按鈕取得的是字面 `\n`，不再是 `#10`。

## 此環境無法完成

Gradle Wrapper 仍需下載 Gradle 8.8，但本環境無法解析 `services.gradle.org`，因此未宣稱：

- `gradlew core:check` 真實 Gradle 執行通過。
- LWJGL3 視窗排版通過。
- Android Runtime 通過。

需由使用者本機實際啟動確認中文字與按鈕換行的視覺結果。

# 本機字型準備

交付 Source ZIP 不附字型二進位檔。現有專案套用 Patch 時，原有字型完全保留，不需再次處理。

另解壓 ZIP 時，在新 Sango 根目錄執行：

```powershell
.\tools\prepare-local-fonts.ps1 -SourceProject "C:\Users\Kuanwei\AndroidStudioProjects\others\Sango"
```

`SourceProject` 是你自己的、目前可執行的 Sango 專案根目錄，不是 ZIP 檔案。工具複製：

- `assets/font/SourceHanSansCN-Regular.otf`
- `assets/font/SourceHanSansCN-Heavy.otf`
- `assets/skin/default/default.fnt`
- `assets/skin/default/default.png`

僅限本機複製；不下載、不重新散布字型。請保留各字型原有授權文件。`Charis-Regular.ttf` 未被目前 Sango 程式引用，因此不是必要資產；工具不複製它，也不刪除你原有的檔案。

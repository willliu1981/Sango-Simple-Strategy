# Local font setup

Sango 的 SimpleUI 中文介面使用下列字型檔：

- `SourceHanSansCN-Regular.otf`
- `SourceHanSansCN-Heavy.otf`

交付 ZIP 不重複攜帶這兩個大型字型二進位檔。請從你自己的 StudyRoutine source 複製，或在 Windows PowerShell 執行：

```powershell
.\tools\prepare-local-fonts.ps1 -StudyRoutineSource "C:\path\to\StudyRoutineCurated-source.zip"
```

也可以把 `StudyRoutineCurated-source.zip` 放在 Sango 專案目錄的上一層，再直接執行：

```powershell
.\tools\prepare-local-fonts.ps1
```

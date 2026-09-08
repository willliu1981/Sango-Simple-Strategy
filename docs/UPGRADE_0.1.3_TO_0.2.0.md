# Upgrade 0.1.3 → 0.2.0

若直接以 0.2.0 ZIP 取代整個專案目錄，不需額外操作。

若將 ZIP 內容覆蓋到既有 Git working tree，請刪除下列 0.1.3 原型檔：

```text
core/src/main/java/idv/kuan/studio/sango/ui/PrototypeCampaignScreen.java
assets/ui/prototype_campaign.xml
```

然後確認新增：

```text
assets/data/**
assets/ui/new_game.xml
assets/ui/city.xml
core/src/main/java/idv/kuan/studio/sango/application/**
core/src/main/java/idv/kuan/studio/sango/domain/**
core/src/main/java/idv/kuan/studio/sango/repository/**
core/src/main/java/idv/kuan/studio/sango/runtime/**
core/src/test/java/idv/kuan/studio/sango/validation/VerticalSliceSmokeTest.java
```

建議命令：

```powershell
git add -A
.\gradlew.bat clean core:check lwjgl3:run
```

0.1.3 的 `prototypeCampaignExists` 是 Preferences 測試旗標，不是正式存檔，不會遷移到 0.2.0。這是預期行為。

# Third-party notices

## SimpleUI 1.1.2

Binary location:

```text
libs/simpleui-1.1.2.jar
```

The supplied StudyRoutine snapshot did not contain a SimpleUI license file. Confirm the applicable license before redistributing this binary outside your own projects.

The Gradle build creates `simpleui-1.1.2-no-bundled-gdx.jar` and excludes `com/badlogic/**` so the bundled LibGDX classes do not conflict with the project's declared LibGDX version.

## Source Han Sans

Sango expects the following developer-local files after running `tools/prepare-local-fonts.ps1`:

```text
assets/font/SourceHanSansCN-Regular.otf
assets/font/SourceHanSansCN-Heavy.otf
```

The font binaries are copied from the developer's own StudyRoutine source and are not duplicated in this delivery ZIP. The supplied license text is retained at:

```text
assets/font/SIL Open Font License 1.1.md
```

## LWJGL3 StartupHelper

`lwjgl3/src/main/java/idv/kuan/studio/sango/lwjgl3/StartupHelper.java` retains its Apache License 2.0 copyright and license header.

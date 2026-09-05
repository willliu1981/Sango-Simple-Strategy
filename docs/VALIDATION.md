# Validation record

## Passed in delivery environment

- Dynamic font atlas page raised to `256 × 256`; this avoids the `118 px` title glyph exceeding the former `128 × 128` page.

- Java 17 core source compilation with `javac --release 17 -Xlint:all`.
- `StartupHelper` compilation with `javac --release 17 -Xlint:all`.
- Desktop and Android launcher syntax compilation against minimal API stubs.
- SimpleUI 1.1.2 `SimpleUiXmlValidator` validation:
  - `assets/ui/lobby.xml`
  - `assets/ui/prototype_campaign.xml`
- i18n key consistency.
- Java actor ID to XML actor ID consistency.
- configured Chinese glyph coverage for current i18n values.
- required source/resource path checks.

## Not executed in delivery environment

The container could not reach the Gradle distribution server and did not contain a cached Gradle 8.8 distribution. Therefore these runtime/build commands still need to be executed on the development machine after local fonts are prepared:

```powershell
.\gradlew.bat clean core:compileJava lwjgl3:dist
.\gradlew.bat lwjgl3:run
.\gradlew.bat android:assembleDebug
```

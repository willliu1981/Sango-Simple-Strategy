package idv.kuan.studio.sango.provider;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.FitViewport;

import idv.kuan.studio.libgdx.simpleui.context.config.ApplicationContextConfigBuilder;
import idv.kuan.studio.libgdx.simpleui.context.config.ApplicationContextConfiguration;
import idv.kuan.studio.libgdx.simpleui.context.provider.ApplicationContextConfigProvider;
import idv.kuan.studio.libgdx.simpleui.resource.FontChars;
import idv.kuan.studio.libgdx.simpleui.resource.ResourceManager;
import idv.kuan.studio.libgdx.simpleui.validation.ValidationMode;
import idv.kuan.studio.sango.ui.BattleReportScreen;
import idv.kuan.studio.sango.ui.CityScreen;
import idv.kuan.studio.sango.ui.GameplayGuideScreen;
import idv.kuan.studio.sango.ui.LobbyScreen;
import idv.kuan.studio.sango.ui.LobbySettingsScreen;
import idv.kuan.studio.sango.ui.MusicPlayerScreen;
import idv.kuan.studio.sango.ui.MonthReportScreen;
import idv.kuan.studio.sango.ui.NewGameScreen;
import idv.kuan.studio.sango.ui.SaveLoadScreen;
import idv.kuan.studio.sango.ui.SettingsScreen;
import idv.kuan.studio.sango.ui.StrategicMapScreen;
import idv.kuan.studio.sango.ui.WorldBattleReportScreen;
import idv.kuan.studio.sango.ui.id.ScreenId;

/**
 * Sango 的 SimpleUI ApplicationContext 設定。
 */
public final class SangoAppContextConfigProvider implements ApplicationContextConfigProvider {
    private static final float DESIGN_WIDTH = 1920f;
    private static final float DESIGN_HEIGHT = 1080f;
    private static final String DEFAULT_FONT_PATH = "font/SourceHanSansCN-Regular.otf";
    private static final String TITLE_FONT_PATH = "font/SourceHanSansCN-Heavy.otf";

    @Override
    public ApplicationContextConfiguration createConfiguration() {
        return buildConfiguration(
            new ConfigBuilder(ApplicationContextConfigBuilder.begin())
        );
    }

    @Override
    public ApplicationContextConfiguration buildConfiguration(ConfigBuilder configBuilder) {
        verifyRequiredFonts();

        String supplementalCharacters = Gdx.files.internal("characters/default.txt").readString("UTF-8");
        FontChars.Characters defaultCharacters = new FontChars.Characters(
            FontChars.ASCII_BASIC.getCharacters() + supplementalCharacters
        );

        return configBuilder
            .setDefaultFontPath(DEFAULT_FONT_PATH)
            .setDefaultCharacters(defaultCharacters)
            .setBaseCharacters(FontChars.ASCII_BASIC)
            .setResourceManagerConfiguration(
                new ResourceManager.ResourceManagerConfiguration(
                    "skin/default/uiskin.json",
                    256,
                    256,
                    true
                )
            )
            .setI18n("i18n/ui_zh_Hant.xml")
            .registerFontPath("title", TITLE_FONT_PATH)
            .skipToRegisterCharacters()
            .skipToDefaultStage()
            .setDefaultStage(() -> new Stage(new FitViewport(DESIGN_WIDTH, DESIGN_HEIGHT)))
            .registerScreen(ScreenId.LOBBY, LobbyScreen::new)
            .registerScreen(ScreenId.NEW_GAME, NewGameScreen::new)
            .registerScreen(ScreenId.STRATEGIC_MAP, StrategicMapScreen::new)
            .registerScreen(ScreenId.CITY, CityScreen::new)
            .registerScreen(ScreenId.MONTH_REPORT, MonthReportScreen::new)
            .registerScreen(ScreenId.BATTLE_REPORT, BattleReportScreen::new)
            .registerScreen(ScreenId.WORLD_BATTLE_REPORT, WorldBattleReportScreen::new)
            .registerScreen(ScreenId.SAVE_LOAD, SaveLoadScreen::new)
            .registerScreen(ScreenId.SETTINGS, SettingsScreen::new)
            .registerScreen(ScreenId.GAMEPLAY_GUIDE, GameplayGuideScreen::new)
            .registerScreen(ScreenId.LOBBY_SETTINGS, LobbySettingsScreen::new)
            .registerScreen(ScreenId.MUSIC_PLAYER, MusicPlayerScreen::new)
            .skipToValidation()
            .setVolidationMode(resolveValidationMode())
            .build();
    }

    private void verifyRequiredFonts() {
        verifyRequiredFont(DEFAULT_FONT_PATH);
        verifyRequiredFont(TITLE_FONT_PATH);
    }

    private void verifyRequiredFont(String fontPath) {
        if (!Gdx.files.internal(fontPath).exists()) {
            throw new IllegalStateException(
                "缺少 Sango 中文字型資產：" + fontPath
                    + "。請先執行 tools/prepare-local-assets-0.6.0.ps1。"
            );
        }
    }

    private ValidationMode resolveValidationMode() {
        if (Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.Android) {
            return ValidationMode.WARN_ONLY;
        }
        return ValidationMode.FAIL_FAST;
    }
}

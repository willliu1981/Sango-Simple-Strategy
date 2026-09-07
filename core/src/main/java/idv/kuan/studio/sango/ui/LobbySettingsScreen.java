package idv.kuan.studio.sango.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.audio.SoundEffect;
import idv.kuan.studio.sango.data.SangoPreferences;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.support.ScreenMusic;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/** 主選單的獨立音訊設定畫面。 */
public final class LobbySettingsScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";
    private static final float VOLUME_STEP = 0.10f;

    private final ScreenBackground screenBackground = new ScreenBackground();

    @Override
    protected BuiltUI buildUI(UIFactory uiFactory) {
        return uiFactory.begin()
            .skipToRegisterTemplate()
            .skipToRegisterXml()
            .registerXml("ui/lobby_settings.xml")
            .skipToBuild()
            .buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        screenBackground.attach(stage, BACKGROUND_PATH);
        applyStyles();
        bindActions();
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.22f));
    }

    @Override
    protected void afterShow() {
        ScreenMusic.play(ScreenId.LOBBY_SETTINGS);
        refreshView();
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigationInput = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    returnToLobby();
                    return true;
                }
                return false;
            }
        };
        return new InputMultiplexer(navigationInput, stage);
    }

    @Override
    protected void afterResize(int width, int height) {
        screenBackground.resize(stage);
    }

    @Override
    protected void beforeDispose() {
        screenBackground.remove();
        super.beforeDispose();
    }

    private void applyStyles() {
        SangoUiStyles.applySecondaryButton(button("settings_music_player_button"));
        SangoUiStyles.applySecondaryButton(button("settings_music_toggle_button"));
        SangoUiStyles.applySecondaryButton(button("settings_music_down_button"));
        SangoUiStyles.applySecondaryButton(button("settings_music_up_button"));
        SangoUiStyles.applySecondaryButton(button("settings_sound_toggle_button"));
        SangoUiStyles.applySecondaryButton(button("settings_sound_down_button"));
        SangoUiStyles.applySecondaryButton(button("settings_sound_up_button"));
        SangoUiStyles.applyPrimaryButton(button("settings_back_button"));
    }

    private void bindActions() {
        ui.onClick("settings_music_player_button", () -> {
            SangoServices.audio().playSound(SoundEffect.UI_CLICK);
            SangoServices.session().openMusicPlayer(ScreenId.LOBBY_SETTINGS);
            Sui.screens.set(ScreenId.MUSIC_PLAYER);
        });
        ui.onClick("settings_music_toggle_button", this::toggleMusic);
        ui.onClick("settings_music_down_button", () -> changeMusicVolume(-VOLUME_STEP));
        ui.onClick("settings_music_up_button", () -> changeMusicVolume(VOLUME_STEP));
        ui.onClick("settings_sound_toggle_button", this::toggleSound);
        ui.onClick("settings_sound_down_button", () -> changeSoundVolume(-VOLUME_STEP));
        ui.onClick("settings_sound_up_button", () -> changeSoundVolume(VOLUME_STEP));
        ui.onClick("settings_back_button", this::returnToLobby);
    }

    private void refreshView() {
        button("settings_music_toggle_button").setText(
            SangoPreferences.isMusicEnabled()
                ? text("settings_music_on", "背景音樂：開啟")
                : text("settings_music_off", "背景音樂：關閉")
        );
        button("settings_sound_toggle_button").setText(
            SangoPreferences.isSoundEnabled()
                ? text("settings_sound_on", "遊戲音效：開啟")
                : text("settings_sound_off", "遊戲音效：關閉")
        );
        label("settings_music_volume_label").setText(text(
            "settings_music_volume_format", "音樂音量：{0}%", Math.round(SangoPreferences.getMusicVolume() * 100f)
        ));
        label("settings_sound_volume_label").setText(text(
            "settings_sound_volume_format", "音效音量：{0}%", Math.round(SangoPreferences.getSoundVolume() * 100f)
        ));
    }

    private void toggleMusic() {
        SangoPreferences.setMusicEnabled(!SangoPreferences.isMusicEnabled());
        SangoServices.audio().applyPreferences();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshView();
    }

    private void toggleSound() {
        SangoPreferences.setSoundEnabled(!SangoPreferences.isSoundEnabled());
        SangoServices.audio().applyPreferences();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshView();
    }

    private void changeMusicVolume(float delta) {
        SangoPreferences.setMusicVolume(SangoPreferences.getMusicVolume() + delta);
        SangoServices.audio().applyPreferences();
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshView();
    }

    private void changeSoundVolume(float delta) {
        SangoPreferences.setSoundVolume(SangoPreferences.getSoundVolume() + delta);
        SangoServices.audio().playSound(SoundEffect.UI_CLICK);
        refreshView();
    }

    private void returnToLobby() {
        SangoServices.audio().playSound(SoundEffect.CANCEL);
        Sui.screens.set(ScreenId.LOBBY);
    }

    private TextButton button(String actorId) {
        return ui.getActor(actorId, TextButton.class);
    }

    private Label label(String actorId) {
        return ui.getActor(actorId, Label.class);
    }

    private String text(String entryName, String fallback, Object... arguments) {
        return Sui.i18n.manager().getText("literal", entryName, fallback, arguments);
    }
}

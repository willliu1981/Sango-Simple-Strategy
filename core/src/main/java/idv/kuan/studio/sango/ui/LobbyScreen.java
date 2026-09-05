package idv.kuan.studio.sango.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.data.SangoPreferences;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/**
 * Sango 啟動後的第一個 Lobby／主選單畫面。
 */
public final class LobbyScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";
    private static final Color STATUS_READY_COLOR = new Color(0.83f, 0.73f, 0.53f, 1f);
    private static final Color STATUS_ACTIVE_COLOR = new Color(0.95f, 0.78f, 0.30f, 1f);

    private Image backgroundImage;
    private Actor newGameMask;
    private Actor settingsMask;
    private Actor exitMask;

    @Override
    protected BuiltUI buildUI(UIFactory uiFactory) {
        return uiFactory.begin()
            .skipToRegisterTemplate()
            .skipToRegisterXml()
            .registerXml("ui/lobby.xml")
            .skipToBuild()
            .buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        attachBackground();
        attachModalMasks();
        applyStyles();
        bindActions();
        refreshCampaignActions(false);
        refreshSettingsLabels();
        animateEntrance();
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigationInput = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    handleBackAction();
                    return true;
                }
                return false;
            }
        };
        return new InputMultiplexer(navigationInput, stage);
    }

    @Override
    protected void afterResize(int width, int height) {
        layoutBackground();
    }

    @Override
    protected void beforeDispose() {
        if (backgroundImage != null) {
            backgroundImage.remove();
            backgroundImage = null;
        }
        super.beforeDispose();
    }

    private void attachBackground() {
        if (backgroundImage != null) {
            backgroundImage.remove();
        }

        Texture backgroundTexture = Sui.resources.manager().getOrLoadTextureByPath(BACKGROUND_PATH);
        backgroundTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        backgroundImage = new Image(backgroundTexture);
        backgroundImage.setScaling(Scaling.fill);
        backgroundImage.setTouchable(Touchable.disabled);
        layoutBackground();
        stage.addActor(backgroundImage);
        backgroundImage.toBack();
    }

    private void layoutBackground() {
        if (backgroundImage == null || stage == null) {
            return;
        }
        backgroundImage.setBounds(0f, 0f, stage.getWidth(), stage.getHeight());
    }

    private void attachModalMasks() {
        newGameMask = attachModalMask("new_game_mask");
        settingsMask = attachModalMask("settings_mask");
        exitMask = attachModalMask("exit_mask");
    }

    private Actor attachModalMask(String actorId) {
        Actor mask = ui.getActor(actorId);
        if (mask.getStage() == null) {
            stage.addActor(mask);
        }
        mask.setVisible(false);
        return mask;
    }

    private void applyStyles() {
        SangoUiStyles.applyMenuButton(button("continue_button"));
        SangoUiStyles.applyPrimaryButton(button("new_game_button"));
        SangoUiStyles.applyMenuButton(button("load_game_button"));
        SangoUiStyles.applyMenuButton(button("settings_button"));
        SangoUiStyles.applyMenuButton(button("exit_button"));

        SangoUiStyles.applySecondaryButton(button("new_game_cancel_button"));
        SangoUiStyles.applyPrimaryButton(button("new_game_confirm_button"));
        SangoUiStyles.applySecondaryButton(button("settings_music_button"));
        SangoUiStyles.applySecondaryButton(button("settings_sound_button"));
        SangoUiStyles.applyPrimaryButton(button("settings_close_button"));
        SangoUiStyles.applySecondaryButton(button("exit_cancel_button"));
        SangoUiStyles.applyPrimaryButton(button("exit_confirm_button"));

        Label statusLabel = ui.getActor("lobby_status", Label.class);
        statusLabel.setAlignment(Align.center);
    }

    private void bindActions() {
        ui.onClick("continue_button", this::continueCampaign);
        ui.onClick("new_game_button", () -> openModal(newGameMask));
        ui.onClick("settings_button", this::openSettings);
        ui.onClick("exit_button", () -> openModal(exitMask));

        ui.onClick("new_game_cancel_button", this::closeAllModals);
        ui.onClick("new_game_confirm_button", this::createPrototypeCampaign);
        ui.onClick("settings_music_button", this::toggleMusic);
        ui.onClick("settings_sound_button", this::toggleSound);
        ui.onClick("settings_close_button", this::closeAllModals);
        ui.onClick("exit_cancel_button", this::closeAllModals);
        ui.onClick("exit_confirm_button", Gdx.app::exit);
    }

    private void animateEntrance() {
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.42f));
    }

    private void continueCampaign() {
        if (!SangoPreferences.hasPrototypeCampaign()) {
            refreshCampaignActions(false);
            return;
        }
        Sui.screens.set(ScreenId.PROTOTYPE_CAMPAIGN);
    }

    private void createPrototypeCampaign() {
        SangoPreferences.setPrototypeCampaignExists(true);
        closeAllModals();
        refreshCampaignActions(true);
        Sui.screens.set(ScreenId.PROTOTYPE_CAMPAIGN);
    }

    private void refreshCampaignActions(boolean newlyCreated) {
        boolean campaignExists = SangoPreferences.hasPrototypeCampaign();
        setButtonEnabled(button("continue_button"), campaignExists);
        setButtonEnabled(button("load_game_button"), false);

        Label statusLabel = ui.getActor("lobby_status", Label.class);
        if (newlyCreated) {
            statusLabel.setText(text(
                "status_campaign_created",
                "測試戰局已建立，正在進入流程驗證畫面。"
            ));
            statusLabel.setColor(STATUS_ACTIVE_COLOR);
        } else if (campaignExists) {
            statusLabel.setText(text(
                "status_campaign_available",
                "已有測試戰局，可選擇「繼續遊戲」。"
            ));
            statusLabel.setColor(STATUS_ACTIVE_COLOR);
        } else {
            statusLabel.setText(text(
                "status_no_campaign",
                "尚無戰局存檔；請先開始新局。"
            ));
            statusLabel.setColor(STATUS_READY_COLOR);
        }
    }

    private void openSettings() {
        refreshSettingsLabels();
        openModal(settingsMask);
    }

    private void toggleMusic() {
        SangoPreferences.setMusicEnabled(!SangoPreferences.isMusicEnabled());
        refreshSettingsLabels();
    }

    private void toggleSound() {
        SangoPreferences.setSoundEnabled(!SangoPreferences.isSoundEnabled());
        refreshSettingsLabels();
    }

    private void refreshSettingsLabels() {
        TextButton musicButton = button("settings_music_button");
        TextButton soundButton = button("settings_sound_button");
        musicButton.setText(
            SangoPreferences.isMusicEnabled()
                ? text("settings_music_on", "背景音樂：開啟")
                : text("settings_music_off", "背景音樂：關閉")
        );
        soundButton.setText(
            SangoPreferences.isSoundEnabled()
                ? text("settings_sound_on", "介面音效：開啟")
                : text("settings_sound_off", "介面音效：關閉")
        );
    }

    private void handleBackAction() {
        if (isAnyModalVisible()) {
            closeAllModals();
            return;
        }
        openModal(exitMask);
    }

    private boolean isAnyModalVisible() {
        return isVisible(newGameMask) || isVisible(settingsMask) || isVisible(exitMask);
    }

    private boolean isVisible(Actor actor) {
        return actor != null && actor.isVisible();
    }

    private void openModal(Actor mask) {
        closeAllModals();
        mask.setVisible(true);
        mask.getColor().a = 0f;
        mask.toFront();
        mask.addAction(Actions.fadeIn(0.16f));
    }

    private void closeAllModals() {
        setVisible(newGameMask, false);
        setVisible(settingsMask, false);
        setVisible(exitMask, false);
    }

    private void setVisible(Actor actor, boolean visible) {
        if (actor != null) {
            actor.clearActions();
            actor.setVisible(visible);
            actor.getColor().a = 1f;
        }
    }

    private void setButtonEnabled(TextButton button, boolean enabled) {
        button.setDisabled(!enabled);
        button.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
    }

    private TextButton button(String actorId) {
        return ui.getActor(actorId, TextButton.class);
    }

    private String text(String entryName, String fallback, Object... arguments) {
        return Sui.i18n.manager().getText("literal", entryName, fallback, arguments);
    }
}

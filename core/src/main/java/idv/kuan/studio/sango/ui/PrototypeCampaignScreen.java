package idv.kuan.studio.sango.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Scaling;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.data.SangoPreferences;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/**
 * 只用來驗證 Lobby 到戰局的導覽與測試進度流程。
 */
public final class PrototypeCampaignScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";

    private Image backgroundImage;

    @Override
    protected BuiltUI buildUI(UIFactory uiFactory) {
        return uiFactory.begin()
            .skipToRegisterTemplate()
            .skipToRegisterXml()
            .registerXml("ui/prototype_campaign.xml")
            .skipToBuild()
            .buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        attachBackground();
        TextButton returnButton = ui.getActor("campaign_return_button", TextButton.class);
        TextButton clearButton = ui.getActor("campaign_clear_button", TextButton.class);
        SangoUiStyles.applyPrimaryButton(returnButton);
        SangoUiStyles.applySecondaryButton(clearButton);
        ui.onClick("campaign_return_button", this::returnToLobby);
        ui.onClick("campaign_clear_button", this::clearPrototypeCampaign);
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

    private void returnToLobby() {
        Sui.screens.set(ScreenId.LOBBY);
    }

    private void clearPrototypeCampaign() {
        SangoPreferences.setPrototypeCampaignExists(false);
        returnToLobby();
    }
}

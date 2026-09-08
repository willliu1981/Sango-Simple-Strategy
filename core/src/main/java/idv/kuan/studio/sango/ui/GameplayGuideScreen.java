package idv.kuan.studio.sango.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.audio.SoundEffect;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.ScreenBackground;
import idv.kuan.studio.sango.ui.support.ScreenMusic;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/** Settings 內可捲動的玩法與主要數值規則說明。 */
public final class GameplayGuideScreen extends SuiScreen {
    private static final String BACKGROUND_PATH = "picture/lobby/sango_lobby_background.png";

    private final ScreenBackground screenBackground = new ScreenBackground();
    private Group scrollHost;
    private Table scrollContent;
    private ScrollPane scrollPane;

    @Override
    protected BuiltUI buildUI(UIFactory uiFactory) {
        return uiFactory.begin()
            .skipToRegisterTemplate()
            .skipToRegisterXml()
            .registerXml("ui/gameplay_guide.xml")
            .skipToBuild()
            .buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        screenBackground.attach(stage, BACKGROUND_PATH);
        scrollHost = ui.getActor("gameplay_guide_scroll_host", Group.class);
        Label contentLabel = label("gameplay_guide_content_label");
        contentLabel.remove();
        contentLabel.setAlignment(Align.topLeft);
        scrollContent = new Table();
        scrollContent.top().left();
        scrollContent.add(contentLabel).top().left().growX();
        scrollPane = new ScrollPane(scrollContent);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setOverscroll(false, false);
        scrollPane.setFadeScrollBars(false);
        scrollHost.addActor(scrollPane);
        resizeScrollPane();

        SangoUiStyles.applySecondaryButton(button("gameplay_guide_back_button"));
        ui.onClick("gameplay_guide_back_button", this::returnToSettings);
        Actor main = ui.getActor("main");
        main.getColor().a = 0f;
        main.addAction(Actions.fadeIn(0.22f));
    }

    @Override
    protected void afterShow() {
        ScreenMusic.play(ScreenId.SETTINGS);
        label("gameplay_guide_content_label").setText(
            text("gameplay_guide_content", "治水可降低六月洪災風險與秋收損失。")
                .replace("\\n", "\n")
        );
        resizeScrollPane();
        scrollPane.setScrollY(0f);
        scrollPane.updateVisualScroll();
        stage.setScrollFocus(scrollPane);
    }

    @Override
    protected InputProcessor createInputProcessor(Stage stage) {
        InputAdapter navigationInput = new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
                    returnToSettings();
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
        resizeScrollPane();
    }

    @Override
    protected void beforeDispose() {
        screenBackground.remove();
        super.beforeDispose();
    }

    private void resizeScrollPane() {
        if (scrollHost == null || scrollPane == null) {
            return;
        }
        float contentWidth = Math.max(1f, scrollHost.getWidth() - 28f);
        scrollPane.setBounds(0f, 0f, scrollHost.getWidth(), scrollHost.getHeight());
        scrollContent.getCell(label("gameplay_guide_content_label")).width(contentWidth);
        scrollContent.invalidateHierarchy();
        scrollPane.validate();
    }

    private void returnToSettings() {
        SangoServices.audio().playSound(SoundEffect.CANCEL);
        Sui.screens.set(ScreenId.SETTINGS);
    }

    private TextButton button(String actorId) {
        return ui.getActor(actorId, TextButton.class);
    }

    private Label label(String actorId) {
        return ui.getActor(actorId, Label.class);
    }

    private String text(String entryName, String fallback) {
        return Sui.i18n.manager().getText("literal", entryName, fallback);
    }
}

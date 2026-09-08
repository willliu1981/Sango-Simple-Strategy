package idv.kuan.studio.sango.ui.support;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;

import idv.kuan.studio.libgdx.simpleui.Sui;

/** 可疊在既有畫面或 modal 上方、且不改變底層操作狀態的情境說明。 */
public final class ContextHelpOverlay {
    private final Stage stage;
    private final Table mask;
    private final Label titleLabel;
    private final Label bodyLabel;
    private final ScrollPane scrollPane;

    public ContextHelpOverlay(
        Stage stage,
        Label.LabelStyle titleStyle,
        Label.LabelStyle bodyStyle,
        TextButton.TextButtonStyle closeButtonStyle,
        String closeText
    ) {
        if (stage == null || titleStyle == null || bodyStyle == null || closeButtonStyle == null) {
            throw new IllegalArgumentException("情境說明所需的 stage 與 style 不可為 null。");
        }
        this.stage = stage;

        mask = new Table();
        mask.setFillParent(true);
        mask.setTouchable(Touchable.enabled);
        mask.setBackground(Sui.resources.manager().getSkin().newDrawable(
            "white", new Color(0f, 0f, 0f, 0.90f)
        ));

        Table card = new Table();
        card.pad(42f);
        card.setBackground(Sui.resources.manager().getSkin().newDrawable(
            "white", new Color(0.055f, 0.043f, 0.035f, 0.995f)
        ));

        titleLabel = new Label("", new Label.LabelStyle(titleStyle));
        titleLabel.setAlignment(Align.left);
        titleLabel.setColor(0.94f, 0.76f, 0.43f, 1f);

        bodyLabel = new Label("", new Label.LabelStyle(bodyStyle));
        bodyLabel.setAlignment(Align.topLeft);
        bodyLabel.setWrap(true);
        bodyLabel.setColor(0.86f, 0.81f, 0.72f, 1f);

        Table scrollContent = new Table();
        scrollContent.top().left();
        scrollContent.add(bodyLabel).width(1120f).growX().top().left();
        scrollPane = new ScrollPane(scrollContent, new ScrollPane.ScrollPaneStyle());
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setOverscroll(false, false);
        scrollPane.setFadeScrollBars(false);

        TextButton closeButton = new TextButton(
            closeText,
            new TextButton.TextButtonStyle(closeButtonStyle)
        );
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                hide();
            }
        });

        card.add(titleLabel).width(1160f).height(76f).left();
        card.row();
        card.add(scrollPane).width(1160f).height(570f).top().left().padTop(18f);
        card.row();
        card.add(closeButton).width(420f).height(78f).padTop(24f);
        mask.add(card).width(1280f).height(820f);
        mask.setVisible(false);
        stage.addActor(mask);
    }

    public void show(String title, String body) {
        titleLabel.setText(title == null ? "" : title);
        bodyLabel.setText(body == null ? "" : body);
        scrollPane.setScrollY(0f);
        scrollPane.updateVisualScroll();
        mask.setVisible(true);
        mask.toFront();
        stage.setScrollFocus(scrollPane);
    }

    public void hide() {
        mask.setVisible(false);
        if (stage.getScrollFocus() == scrollPane) {
            stage.setScrollFocus(null);
        }
    }

    public boolean isVisible() {
        return mask.isVisible();
    }

    public void remove() {
        mask.remove();
    }
}

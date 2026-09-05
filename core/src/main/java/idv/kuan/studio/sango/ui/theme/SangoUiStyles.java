package idv.kuan.studio.sango.ui.theme;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

import idv.kuan.studio.libgdx.simpleui.Sui;

/**
 * Sango Lobby 共用的 Scene2D Style 工具。
 */
public final class SangoUiStyles {
    private static final Color MENU_UP = new Color(0.13f, 0.11f, 0.09f, 0.96f);
    private static final Color MENU_OVER = new Color(0.33f, 0.10f, 0.07f, 0.98f);
    private static final Color MENU_DOWN = new Color(0.20f, 0.055f, 0.04f, 1f);
    private static final Color PRIMARY_UP = new Color(0.48f, 0.13f, 0.075f, 0.98f);
    private static final Color PRIMARY_OVER = new Color(0.68f, 0.23f, 0.09f, 1f);
    private static final Color PRIMARY_DOWN = new Color(0.34f, 0.075f, 0.045f, 1f);
    private static final Color DISABLED = new Color(0.08f, 0.075f, 0.07f, 0.82f);
    private static final Color FONT_NORMAL = new Color(0.95f, 0.88f, 0.72f, 1f);
    private static final Color FONT_OVER = new Color(1f, 0.95f, 0.80f, 1f);
    private static final Color FONT_DISABLED = new Color(0.45f, 0.43f, 0.39f, 1f);

    private SangoUiStyles() {
    }

    public static void applyMenuButton(TextButton button) {
        apply(button, MENU_UP, MENU_OVER, MENU_DOWN);
    }

    public static void applyPrimaryButton(TextButton button) {
        apply(button, PRIMARY_UP, PRIMARY_OVER, PRIMARY_DOWN);
    }

    public static void applySecondaryButton(TextButton button) {
        apply(button, MENU_UP, MENU_OVER, MENU_DOWN);
    }

    private static void apply(
        TextButton button,
        Color upColor,
        Color overColor,
        Color downColor
    ) {
        Skin skin = Sui.resources.manager().getSkin();
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(button.getStyle());

        // SimpleUI 已為每個 TextButton 產生包含中文字形的動態字型。
        // TextButton#setStyle 會以 style.font 覆寫 Label 的字型，因此必須先保留它。
        style.font = button.getLabel().getStyle().font;
        style.up = drawable(skin, upColor);
        style.over = drawable(skin, overColor);
        style.down = drawable(skin, downColor);
        style.disabled = drawable(skin, DISABLED);
        style.fontColor = FONT_NORMAL.cpy();
        style.overFontColor = FONT_OVER.cpy();
        style.downFontColor = FONT_OVER.cpy();
        style.disabledFontColor = FONT_DISABLED.cpy();
        button.setStyle(style);
    }

    private static Drawable drawable(Skin skin, Color color) {
        return skin.newDrawable("white", color);
    }
}

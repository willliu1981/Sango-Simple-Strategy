package idv.kuan.studio.sango.ui.theme;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

import idv.kuan.studio.libgdx.simpleui.Sui;

/**
 * Sango 共用的 Scene2D Style 工具。
 */
public final class SangoUiStyles {
    private static final Color MENU_UP = new Color(0.13f, 0.11f, 0.09f, 0.96f);
    private static final Color MENU_OVER = new Color(0.33f, 0.10f, 0.07f, 0.98f);
    private static final Color MENU_DOWN = new Color(0.20f, 0.055f, 0.04f, 1f);
    private static final Color PRIMARY_UP = new Color(0.48f, 0.13f, 0.075f, 0.98f);
    private static final Color PRIMARY_OVER = new Color(0.68f, 0.23f, 0.09f, 1f);
    private static final Color PRIMARY_DOWN = new Color(0.34f, 0.075f, 0.045f, 1f);
    private static final Color DANGER_UP = new Color(0.40f, 0.07f, 0.055f, 0.98f);
    private static final Color DANGER_OVER = new Color(0.62f, 0.10f, 0.065f, 1f);
    private static final Color DANGER_DOWN = new Color(0.28f, 0.035f, 0.030f, 1f);
    private static final Color DISABLED = new Color(0.08f, 0.075f, 0.07f, 0.82f);
    private static final Color FONT_NORMAL = new Color(0.95f, 0.88f, 0.72f, 1f);
    private static final Color FONT_OVER = new Color(1f, 0.95f, 0.80f, 1f);
    private static final Color FONT_DISABLED = new Color(0.45f, 0.43f, 0.39f, 1f);
    private static final Color MAP_PLAYER = new Color(0.46f, 0.34f, 0.12f, 0.98f);
    private static final Color MAP_ENEMY = new Color(0.48f, 0.10f, 0.07f, 0.98f);
    private static final Color MAP_NEUTRAL = new Color(0.16f, 0.16f, 0.15f, 0.98f);
    private static final Color MAP_SELECTED = new Color(0.78f, 0.45f, 0.10f, 1f);
    // 淺色山河底圖上使用較深的道路，避免與河流或紙紋混在一起。
    private static final Color MAP_LINE = new Color(0.19f, 0.15f, 0.09f, 0.94f);

    private SangoUiStyles() {
    }

    public static void applyMenuButton(TextButton button) {
        apply(button, MENU_UP, MENU_OVER, MENU_DOWN);
    }

    public static void applyPrimaryButton(TextButton button) {
        apply(button, PRIMARY_UP, PRIMARY_OVER, PRIMARY_DOWN);
    }

    public static void applySelectedButton(TextButton button) {
        applyPrimaryButton(button);
    }

    public static void applySecondaryButton(TextButton button) {
        applyMenuButton(button);
    }

    public static void applyDangerButton(TextButton button) {
        apply(button, DANGER_UP, DANGER_OVER, DANGER_DOWN);
    }

    public static TextButton.TextButtonStyle createMapNodeStyle(
        BitmapFont bitmapFont,
        MapNodeTone nodeTone,
        boolean selected
    ) {
        if (bitmapFont == null || nodeTone == null) {
            throw new IllegalArgumentException("bitmapFont 與 nodeTone 不可為 null。");
        }
        Skin skin = Sui.resources.manager().getSkin();
        Color baseColor;
        if (selected) {
            baseColor = MAP_SELECTED;
        } else if (nodeTone == MapNodeTone.PLAYER) {
            baseColor = MAP_PLAYER;
        } else if (nodeTone == MapNodeTone.ENEMY) {
            baseColor = MAP_ENEMY;
        } else {
            baseColor = MAP_NEUTRAL;
        }

        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = bitmapFont;
        style.up = drawable(skin, baseColor);
        style.over = drawable(skin, baseColor.cpy().mul(1.18f));
        style.down = drawable(skin, baseColor.cpy().mul(0.78f));
        style.fontColor = FONT_NORMAL.cpy();
        style.overFontColor = FONT_OVER.cpy();
        style.downFontColor = FONT_OVER.cpy();
        return style;
    }

    public static Drawable createMapLineDrawable() {
        return drawable(Sui.resources.manager().getSkin(), MAP_LINE);
    }

    public static Drawable createMapCityMarkerDrawable(MapNodeTone nodeTone, boolean selected) {
        if (nodeTone == null) {
            throw new IllegalArgumentException("nodeTone 不可為 null。");
        }
        Color markerColor = selected ? MAP_SELECTED
            : nodeTone == MapNodeTone.PLAYER ? MAP_PLAYER
            : nodeTone == MapNodeTone.ENEMY ? MAP_ENEMY : MAP_NEUTRAL;
        return drawable(Sui.resources.manager().getSkin(), markerColor.cpy().lerp(Color.WHITE, 0.18f));
    }

    public static Drawable createMapOutlineDrawable(boolean selected) {
        return drawable(Sui.resources.manager().getSkin(), selected
            ? new Color(1f, 0.96f, 0.76f, 1f)
            : new Color(0.95f, 0.73f, 0.30f, 1f));
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

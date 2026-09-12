package idv.kuan.studio.sango.ui.widget;

import java.util.List;

import com.badlogic.gdx.math.Rectangle;

/** 替可見的地圖標籤尋找不重疊、且不超出視窗的位置。 */
public final class MapLabelLayout {
    private static final float VIEWPORT_PADDING = 4f;
    private static final float LABEL_GAP = 5f;
    private static final int MAXIMUM_SEARCH_RING = 8;
    private static final float MINIMUM_DETAIL_SCALE = 0.72f;
    private static final float EDGE_PREVIEW_SCALE = 0.85f;

    private MapLabelLayout() {
    }

    /** 標籤只在剛進入細節模式時略縮，地圖繼續放大時不再跟著膨脹。 */
    public static float detailScale(float mapZoom) {
        if (!Float.isFinite(mapZoom) || mapZoom <= 0f) {
            throw new IllegalArgumentException("地圖縮放倍率必須是有限正數。");
        }
        return clamp(mapZoom, MINIMUM_DETAIL_SCALE, 1f);
    }

    /** 當城池錨點仍在畫面外時，將夾在邊界上的提示標籤略微縮小。 */
    public static float labelScaleForAnchor(
        float anchorX,
        float anchorY,
        float viewportWidth,
        float viewportHeight
    ) {
        if (!Float.isFinite(anchorX) || !Float.isFinite(anchorY)
            || viewportWidth <= 0f || viewportHeight <= 0f) {
            throw new IllegalArgumentException("城池錨點與視窗尺寸必須有效。");
        }
        boolean inside = anchorX >= 0f && anchorX <= viewportWidth
            && anchorY >= 0f && anchorY <= viewportHeight;
        return inside ? 1f : EDGE_PREVIEW_SCALE;
    }

    public static Rectangle place(
        float anchorX,
        float anchorY,
        float width,
        float height,
        float viewportWidth,
        float viewportHeight,
        List<Rectangle> occupied
    ) {
        if (width <= 0f || height <= 0f || viewportWidth <= 0f || viewportHeight <= 0f
            || occupied == null) {
            throw new IllegalArgumentException("地圖標籤尺寸、視窗與已占用區域必須有效。");
        }
        for (int ring = 0; ring <= MAXIMUM_SEARCH_RING; ring++) {
            for (int offsetY = -ring; offsetY <= ring; offsetY++) {
                for (int offsetX = -ring; offsetX <= ring; offsetX++) {
                    if (ring > 0 && Math.abs(offsetX) != ring && Math.abs(offsetY) != ring) {
                        continue;
                    }
                    Rectangle candidate = candidate(
                        anchorX + offsetX * (width + LABEL_GAP),
                        anchorY + offsetY * (height + LABEL_GAP),
                        width,
                        height,
                        viewportWidth,
                        viewportHeight
                    );
                    if (!overlaps(candidate, occupied)) {
                        occupied.add(new Rectangle(candidate));
                        return candidate;
                    }
                }
            }
        }
        Rectangle fallback = candidate(
            anchorX, anchorY, width, height, viewportWidth, viewportHeight);
        occupied.add(new Rectangle(fallback));
        return fallback;
    }

    private static Rectangle candidate(
        float centerX,
        float centerY,
        float width,
        float height,
        float viewportWidth,
        float viewportHeight
    ) {
        float maximumX = Math.max(VIEWPORT_PADDING, viewportWidth - width - VIEWPORT_PADDING);
        float maximumY = Math.max(VIEWPORT_PADDING, viewportHeight - height - VIEWPORT_PADDING);
        float x = clamp(centerX - width / 2f, VIEWPORT_PADDING, maximumX);
        float y = clamp(centerY - height / 2f, VIEWPORT_PADDING, maximumY);
        return new Rectangle(x, y, width, height);
    }

    private static boolean overlaps(Rectangle candidate, List<Rectangle> occupied) {
        for (Rectangle rectangle : occupied) {
            if (candidate.overlaps(rectangle)) {
                return true;
            }
        }
        return false;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

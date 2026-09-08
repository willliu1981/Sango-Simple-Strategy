package idv.kuan.studio.sango.ui.widget;

/**
 * 純 Java 的地圖相機：只保存 View 狀態，不依賴 Graphics Context，也不修改 GameState。
 */
public final class MapCameraState {
    public static final float MAXIMUM_ZOOM = 1.75f;
    private float viewportWidth = 1f;
    private float viewportHeight = 1f;
    private float worldWidth = 1f;
    private float worldHeight = 1f;
    private float centerX;
    private float centerY;
    private float zoom = 1f;

    public void configure(float viewportWidth, float viewportHeight, float worldWidth, float worldHeight) {
        requirePositive(viewportWidth);
        requirePositive(viewportHeight);
        requirePositive(worldWidth);
        requirePositive(worldHeight);
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        zoom = clamp(zoom, minimumZoom(), MAXIMUM_ZOOM);
        clampCenter();
    }

    public float minimumZoom() {
        return Math.min(MAXIMUM_ZOOM, Math.min(viewportWidth / worldWidth, viewportHeight / worldHeight));
    }

    public float getZoom() {
        return zoom;
    }

    public float screenX(float worldX) {
        return (worldX - centerX) * zoom + viewportWidth / 2f;
    }

    public float screenY(float worldY) {
        return (worldY - centerY) * zoom + viewportHeight / 2f;
    }

    public float worldX(float screenX) {
        return centerX + (screenX - viewportWidth / 2f) / zoom;
    }

    public float worldY(float screenY) {
        return centerY + (screenY - viewportHeight / 2f) / zoom;
    }

    public void pan(float deltaX, float deltaY) {
        if (!Float.isFinite(deltaX) || !Float.isFinite(deltaY)) {
            return;
        }
        centerX -= deltaX / zoom;
        centerY -= deltaY / zoom;
        clampCenter();
    }

    public void zoomAt(float requestedZoom, float screenX, float screenY) {
        if (!Float.isFinite(requestedZoom) || !Float.isFinite(screenX) || !Float.isFinite(screenY)) {
            return;
        }
        float anchoredWorldX = worldX(screenX);
        float anchoredWorldY = worldY(screenY);
        zoom = clamp(requestedZoom, minimumZoom(), MAXIMUM_ZOOM);
        centerX = anchoredWorldX - (screenX - viewportWidth / 2f) / zoom;
        centerY = anchoredWorldY - (screenY - viewportHeight / 2f) / zoom;
        clampCenter();
    }

    public void centerOn(float worldX, float worldY) {
        if (!Float.isFinite(worldX) || !Float.isFinite(worldY)) {
            return;
        }
        centerX = worldX;
        centerY = worldY;
        clampCenter();
    }

    public void fitAll() {
        zoom = minimumZoom();
        centerX = worldWidth / 2f;
        centerY = worldHeight / 2f;
        clampCenter();
    }

    private void clampCenter() {
        float visibleHalfWidth = viewportWidth / (2f * zoom);
        float visibleHalfHeight = viewportHeight / (2f * zoom);
        centerX = worldWidth <= visibleHalfWidth * 2f
            ? worldWidth / 2f : clamp(centerX, visibleHalfWidth, worldWidth - visibleHalfWidth);
        centerY = worldHeight <= visibleHalfHeight * 2f
            ? worldHeight / 2f : clamp(centerY, visibleHalfHeight, worldHeight - visibleHalfHeight);
    }

    private float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void requirePositive(float value) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException("地圖尺寸必須是有限正數。");
        }
    }
}

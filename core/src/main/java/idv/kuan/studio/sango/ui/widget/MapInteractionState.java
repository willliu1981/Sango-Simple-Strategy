package idv.kuan.studio.sango.ui.widget;

import java.util.Objects;

/** Tap history survives node rebuilding; drag and mode changes clear it. */
public final class MapInteractionState {
    public enum Action { NONE, ENTER_FULLSCREEN, EXIT_FULLSCREEN }

    private static final long DOUBLE_TAP_MILLIS = 350L;
    private static final float MAXIMUM_TAP_DISTANCE = 36f;
    private boolean pending;
    private boolean previousFullscreen;
    private String previousCityId;
    private long previousTime;
    private float previousX;
    private float previousY;

    public Action tap(String cityId, boolean fullscreen, long now, float x, float y) {
        float dx = x - previousX;
        float dy = y - previousY;
        boolean doubleTap = pending && previousFullscreen == fullscreen
            && Objects.equals(previousCityId, cityId)
            && now >= previousTime && now - previousTime <= DOUBLE_TAP_MILLIS
            && dx * dx + dy * dy <= MAXIMUM_TAP_DISTANCE * MAXIMUM_TAP_DISTANCE;
        if (doubleTap) {
            reset();
            return fullscreen
                ? (cityId == null ? Action.EXIT_FULLSCREEN : Action.NONE)
                : Action.ENTER_FULLSCREEN;
        }
        pending = true;
        previousCityId = cityId;
        previousFullscreen = fullscreen;
        previousTime = now;
        previousX = x;
        previousY = y;
        return Action.NONE;
    }

    public void reset() {
        pending = false;
        previousCityId = null;
    }
}

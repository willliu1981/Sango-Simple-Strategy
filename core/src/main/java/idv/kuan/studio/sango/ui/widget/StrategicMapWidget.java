package idv.kuan.studio.sango.ui.widget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.Timer;

import idv.kuan.studio.sango.domain.definition.CityConnectionDefinition;
import idv.kuan.studio.sango.domain.definition.MapCityNodeDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.ui.theme.MapNodeTone;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/**
 * 可裁切、拖曳、滾輪與雙指縮放的節點式地圖。節點與道路共用相機座標。
 * 總覽時縮成城名標籤；拉近後顯示勢力、目標與未讀戰事。
 */
public final class StrategicMapWidget extends WidgetGroup {
    private static final float LARGE_WORLD_WIDTH = 4200f;
    private static final float LARGE_WORLD_HEIGHT = 2800f;
    private static final float DRAG_THRESHOLD = 12f;
    private final BitmapFont nodeFont;
    private final Consumer<String> citySelectionHandler;
    private final Runnable mapTapHandler;
    private final Runnable mapRestoreHandler;
    private final MapInteractionState interaction = new MapInteractionState();
    private boolean fullscreen;
    private final MapCameraState camera = new MapCameraState();
    private final Map<String, String> captionsByCityId = new LinkedHashMap<>();
    private final Map<String, MapNodeTone> tonesByCityId = new LinkedHashMap<>();
    private final Map<String, Integer> unreadBattlesByCityId = new LinkedHashMap<>();
    private final Map<String, String> factionIdsByCityId = new LinkedHashMap<>();
    private final Map<String, TextButton> buttonsByCityId = new LinkedHashMap<>();
    private final List<Image> roadImages = new ArrayList<>();
    private final List<ClickListener> nodeClickListeners = new ArrayList<>();
    private final Map<Integer, PointerPosition> pointers = new LinkedHashMap<>();
    private StrategicMapDefinition mapDefinition;
    private Drawable terrainDrawable;
    private String selectedCityId;
    private String playerFactionId;
    private String neutralFactionId;
    private String highlightedFactionId;
    private String pinnedFactionId;
    private final Set<Timer.Task> pendingHighlightTasks = new HashSet<>();
    private float worldWidth;
    private float worldHeight;
    private boolean initialFocusPending;
    private boolean dragging;

    public StrategicMapWidget(BitmapFont nodeFont, Consumer<String> citySelectionHandler) {
        this(nodeFont, citySelectionHandler, () -> { });
    }

    public StrategicMapWidget(
        BitmapFont nodeFont,
        Consumer<String> citySelectionHandler,
        Runnable mapTapHandler
    ) {
        this(nodeFont, citySelectionHandler, mapTapHandler, () -> { });
    }

    public StrategicMapWidget(BitmapFont nodeFont, Consumer<String> citySelectionHandler,
        Runnable mapTapHandler, Runnable mapRestoreHandler) {
        if (nodeFont == null || citySelectionHandler == null || mapTapHandler == null
            || mapRestoreHandler == null) {
            throw new IllegalArgumentException("地圖字型、選城與點擊處理不可為 null。");
        }
        this.nodeFont = nodeFont;
        this.citySelectionHandler = citySelectionHandler;
        this.mapTapHandler = mapTapHandler;
        this.mapRestoreHandler = mapRestoreHandler;
        setFillParent(true);
        setTouchable(Touchable.enabled);
        addCaptureListener(createNavigationListener());
    }

    public void setMapData(
        StrategicMapDefinition mapDefinition,
        Map<String, String> captionsByCityId,
        Map<String, MapNodeTone> tonesByCityId,
        Map<String, Integer> unreadBattlesByCityId,
        String selectedCityId
    ) {
        setMapData(mapDefinition, captionsByCityId, tonesByCityId, unreadBattlesByCityId,
            Map.of(), null, null, selectedCityId);
    }

    public void setMapData(
        StrategicMapDefinition mapDefinition,
        Map<String, String> captionsByCityId,
        Map<String, MapNodeTone> tonesByCityId,
        Map<String, Integer> unreadBattlesByCityId,
        Map<String, String> factionIdsByCityId,
        String playerFactionId,
        String neutralFactionId,
        String selectedCityId
    ) {
        if (mapDefinition == null || captionsByCityId == null
            || tonesByCityId == null || unreadBattlesByCityId == null
            || factionIdsByCityId == null) {
            throw new IllegalArgumentException("地圖顯示資料不可為 null。");
        }
        if (this.mapDefinition != mapDefinition) {
            initialFocusPending = true;
        }
        this.mapDefinition = mapDefinition;
        this.captionsByCityId.clear();
        this.captionsByCityId.putAll(captionsByCityId);
        this.tonesByCityId.clear();
        this.tonesByCityId.putAll(tonesByCityId);
        this.unreadBattlesByCityId.clear();
        this.unreadBattlesByCityId.putAll(unreadBattlesByCityId);
        this.factionIdsByCityId.clear();
        this.factionIdsByCityId.putAll(factionIdsByCityId);
        this.playerFactionId = playerFactionId;
        this.neutralFactionId = neutralFactionId;
        highlightedFactionId = null;
        if (pinnedFactionId != null && !this.factionIdsByCityId.containsValue(pinnedFactionId)) {
            pinnedFactionId = null;
        }
        this.selectedCityId = selectedCityId;
        rebuildChildren();
        invalidate();
    }

    public void setTerrainDrawable(Drawable terrainDrawable) {
        this.terrainDrawable = terrainDrawable;
    }

    public void zoomBy(float factor) {
        validate();
        camera.zoomAt(camera.getZoom() * factor, getWidth() / 2f, getHeight() / 2f);
        invalidate();
    }

    public void fitAll() {
        validate();
        camera.fitAll();
        invalidate();
    }

    public void focusSelectedCity() {
        // Focus after the host has completed layout, including the first loaded frame.
        initialFocusPending = true;
        invalidate();
    }

    /** 畫面隱藏或釋放時，阻止尚未觸發的滑鼠懸停／長按提示回呼存取舊 UI。 */
    public void cancelPendingFactionHighlightTimers() {
        cancelPendingHighlights();
        clearFactionHighlight();
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        interaction.reset();
    }

    @Override
    public void layout() {
        if (mapDefinition == null || getWidth() <= 0f || getHeight() <= 0f) {
            return;
        }
        boolean largeMap = mapDefinition.nodes.length > 12;
        worldWidth = largeMap ? LARGE_WORLD_WIDTH : getWidth();
        worldHeight = largeMap ? LARGE_WORLD_HEIGHT : getHeight();
        camera.configure(getWidth(), getHeight(), worldWidth, worldHeight);
        if (initialFocusPending && selectedCityId != null) {
            initialFocusPending = false;
            camera.zoomAt(1f, getWidth() / 2f, getHeight() / 2f);
            MapCityNodeDefinition focusNode = mapDefinition.requireNode(selectedCityId);
            camera.centerOn(focusNode.x * worldWidth, focusNode.y * worldHeight);
        }
        positionConnections();
        positionNodes();
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        validate();
        applyTransform(batch, computeTransform());
        batch.flush();
        if (clipBegin(0f, 0f, getWidth(), getHeight())) {
            drawTerrain(batch, parentAlpha);
            drawChildren(batch, parentAlpha);
            batch.flush();
            clipEnd();
        }
        resetTransform(batch);
    }

    private void drawTerrain(Batch batch, float parentAlpha) {
        if (terrainDrawable == null || worldWidth <= 0f || worldHeight <= 0f) {
            return;
        }
        float originalPackedColor = batch.getPackedColor();
        batch.setColor(getColor().r, getColor().g, getColor().b, getColor().a * parentAlpha);
        // 與城池和道路完全共用同一組世界轉螢幕座標，並在同一裁切範圍內繪製。
        terrainDrawable.draw(
            batch,
            camera.screenX(0f),
            camera.screenY(0f),
            worldWidth * camera.getZoom(),
            worldHeight * camera.getZoom()
        );
        batch.setPackedColor(originalPackedColor);
    }

    @Override
    public Actor hit(float localX, float localY, boolean touchable) {
        // 裁切不只限繪圖；視窗外節點也不能攔截右側面板或底部按鈕。
        if (localX < 0f || localY < 0f || localX >= getWidth() || localY >= getHeight()) {
            return null;
        }
        return super.hit(localX, localY, touchable);
    }

    private void rebuildChildren() {
        cancelPendingHighlights();
        clearChildren();
        buttonsByCityId.clear();
        roadImages.clear();
        nodeClickListeners.clear();
        for (CityConnectionDefinition connection : mapDefinition.connections) {
            Image lineImage = new Image(SangoUiStyles.createMapLineDrawable());
            lineImage.setTouchable(Touchable.disabled);
            roadImages.add(lineImage);
            addActor(lineImage);
        }
        for (MapCityNodeDefinition node : mapDefinition.nodes) {
            String caption = captionsByCityId.getOrDefault(node.cityId, node.cityId);
            TextButton nodeButton = new TextButton(caption, SangoUiStyles.createMapNodeStyle(
                nodeFont, tonesByCityId.getOrDefault(node.cityId, MapNodeTone.NEUTRAL),
                node.cityId.equals(selectedCityId)
            ));
            nodeButton.setName("node-" + node.cityId);
            nodeButton.getLabel().setAlignment(Align.center);
            nodeButton.getLabel().setWrap(true);
            ClickListener clickListener = new ClickListener() {
                @Override
                public void clicked(InputEvent event, float localX, float localY) {
                    handleTap(node.cityId, event);
                }
            };
            nodeButton.addListener(clickListener);
            nodeButton.addListener(createFactionHighlightListener(node.cityId));
            nodeClickListeners.add(clickListener);
            buttonsByCityId.put(node.cityId, nodeButton);
            addActor(nodeButton);
        }
        refreshNodeAnimations();
    }

    private InputListener createFactionHighlightListener(String cityId) {
        return new InputListener() {
            private Timer.Task longPressTask;
            private Timer.Task hoverTask;
            private float downX;
            private float downY;

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (pointer == -1) {
                    hoverTask = scheduleHighlight(cityId);
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (pointer == -1 && (toActor == null || (toActor != event.getListenerActor()
                    && !toActor.isDescendantOf(event.getListenerActor())))) {
                    cancelTask(hoverTask);
                    hoverTask = null;
                    clearFactionHighlight();
                }
            }

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button != Input.Buttons.LEFT) return false;
                downX = x;
                downY = y;
                longPressTask = scheduleHighlight(cityId);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                if (distance(x, y, downX, downY) >= DRAG_THRESHOLD) cancelLongPress();
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                cancelLongPress();
                clearFactionHighlight();
            }

            private void cancelLongPress() {
                if (longPressTask != null) {
                    cancelTask(longPressTask);
                    longPressTask = null;
                }
            }
        };
    }

    private Timer.Task scheduleHighlight(String cityId) {
        Timer.Task task = new Timer.Task() {
            @Override public void run() {
                pendingHighlightTasks.remove(this);
                activateFactionHighlight(cityId);
            }
        };
        pendingHighlightTasks.add(task);
        Timer.schedule(task, 0.45f);
        return task;
    }

    private void cancelTask(Timer.Task task) {
        if (task != null) {
            task.cancel();
            pendingHighlightTasks.remove(task);
        }
    }

    private void cancelPendingHighlights() {
        for (Timer.Task task : new HashSet<>(pendingHighlightTasks)) {
            task.cancel();
        }
        pendingHighlightTasks.clear();
    }

    private boolean activateFactionHighlight(String cityId) {
        String factionId = factionIdsByCityId.get(cityId);
        if (factionId == null || factionId.equals(playerFactionId)
            || factionId.equals(neutralFactionId)) return false;
        if (!factionId.equals(highlightedFactionId)) {
            highlightedFactionId = factionId;
            refreshNodeAnimations();
        }
        return true;
    }

    private void clearFactionHighlight() {
        if (highlightedFactionId == null) return;
        highlightedFactionId = null;
        refreshNodeAnimations();
    }

    private void togglePinnedFaction(String cityId) {
        String factionId = factionIdsByCityId.get(cityId);
        if (factionId == null) {
            return;
        }
        pinnedFactionId = factionId.equals(pinnedFactionId) ? null : factionId;
        highlightedFactionId = null;
        refreshNodeAnimations();
    }

    private void refreshNodeAnimations() {
        for (Map.Entry<String, TextButton> entry : buttonsByCityId.entrySet()) {
            TextButton button = entry.getValue();
            button.clearActions();
            button.getColor().a = 1f;
            String effectiveHighlight = highlightedFactionId != null
                ? highlightedFactionId : pinnedFactionId;
            boolean shouldFlash = effectiveHighlight == null
                ? unreadBattlesByCityId.getOrDefault(entry.getKey(), 0) > 0
                : effectiveHighlight.equals(factionIdsByCityId.get(entry.getKey()));
            if (shouldFlash) {
                button.addAction(Actions.forever(Actions.sequence(
                    Actions.alpha(0.52f, 0.48f), Actions.alpha(1f, 0.48f)
                )));
            }
        }
    }

    private void positionConnections() {
        float thickness = Math.max(1.5f, 5f * camera.getZoom());
        for (int i = 0; i < mapDefinition.connections.length; i++) {
            CityConnectionDefinition connection = mapDefinition.connections[i];
            MapCityNodeDefinition fromNode = mapDefinition.requireNode(connection.fromCityId);
            MapCityNodeDefinition toNode = mapDefinition.requireNode(connection.toCityId);
            float fromX = camera.screenX(fromNode.x * worldWidth);
            float fromY = camera.screenY(fromNode.y * worldHeight);
            float deltaX = camera.screenX(toNode.x * worldWidth) - fromX;
            float deltaY = camera.screenY(toNode.y * worldHeight) - fromY;
            Image lineImage = roadImages.get(i);
            lineImage.setBounds(fromX, fromY - thickness / 2f,
                (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY), thickness);
            lineImage.setOrigin(0f, thickness / 2f);
            lineImage.setRotation(MathUtils.atan2(deltaY, deltaX) * MathUtils.radiansToDegrees);
        }
    }

    private void positionNodes() {
        float zoom = camera.getZoom();
        boolean overview = zoom < 0.55f;
        float nodeWidth = overview ? Math.max(62f, 170f * zoom) : 224f * zoom;
        float nodeHeight = overview ? 32f : 106f * zoom;
        for (MapCityNodeDefinition node : mapDefinition.nodes) {
            TextButton nodeButton = buttonsByCityId.get(node.cityId);
            String fullCaption = captionsByCityId.getOrDefault(node.cityId, node.cityId);
            String caption = overview ? fullCaption.split("\\n", 2)[0] : fullCaption;
            nodeButton.setText(caption);
            nodeButton.getLabel().setFontScale(overview ? 0.50f : Math.min(1.15f, zoom));
            nodeButton.setBounds(
                camera.screenX(node.x * worldWidth) - nodeWidth / 2f,
                camera.screenY(node.y * worldHeight) - nodeHeight / 2f,
                nodeWidth, nodeHeight
            );
        }
    }

    private InputListener createNavigationListener() {
        return new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float localX, float localY, int pointer, int mouseButton) {
                if (mouseButton != Input.Buttons.LEFT) {
                    return false;
                }
                if (pointers.isEmpty()) {
                    dragging = false;
                }
                pointers.put(pointer, new PointerPosition(localX, localY));
                if (pointers.size() > 1) {
                    cancelNodeClicks();
                }
                getStage().setScrollFocus(StrategicMapWidget.this);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float localX, float localY, int pointer) {
                PointerPosition position = pointers.get(pointer);
                if (position == null) {
                    return;
                }
                validate();
                if (pointers.size() == 2) {
                    PointerPosition otherPosition = null;
                    for (Map.Entry<Integer, PointerPosition> entry : pointers.entrySet()) {
                        if (entry.getKey() != pointer) {
                            otherPosition = entry.getValue();
                        }
                    }
                    if (otherPosition != null) {
                        float oldDistance = distance(position.localX, position.localY, otherPosition.localX, otherPosition.localY);
                        float newDistance = distance(localX, localY, otherPosition.localX, otherPosition.localY);
                        float midpointX = (localX + otherPosition.localX) / 2f;
                        float midpointY = (localY + otherPosition.localY) / 2f;
                        camera.pan((localX - position.localX) / 2f, (localY - position.localY) / 2f);
                        if (oldDistance > 2f && newDistance > 2f) {
                            camera.zoomAt(camera.getZoom() * newDistance / oldDistance, midpointX, midpointY);
                        }
                    }
                    cancelNodeClicks();
                } else if (pointers.size() == 1) {
                    float movedDistance = distance(localX, localY, position.startX, position.startY);
                    if (dragging || movedDistance >= DRAG_THRESHOLD) {
                        if (!dragging) {
                            camera.pan(localX - position.startX, localY - position.startY);
                        } else {
                            camera.pan(localX - position.localX, localY - position.localY);
                        }
                        cancelNodeClicks();
                    }
                }
                position.localX = localX;
                position.localY = localY;
                invalidate();
            }

            @Override
            public void touchUp(InputEvent event, float localX, float localY, int pointer, int mouseButton) {
                pointers.remove(pointer);
                if (event.isTouchFocusCancel() || dragging) {
                    cancelNodeClicks();
                } else if (pointers.isEmpty() && event.getTarget() == StrategicMapWidget.this) {
                    handleTap(null, event);
                }
            }

            @Override
            public void enter(InputEvent event, float localX, float localY, int pointer, Actor fromActor) {
                if (pointer == -1 && getStage() != null) {
                    getStage().setScrollFocus(StrategicMapWidget.this);
                }
            }

            @Override
            public boolean scrolled(InputEvent event, float localX, float localY, float amountX, float amountY) {
                Actor hoveredActor = getStage().hit(event.getStageX(), event.getStageY(), true);
                if (hoveredActor == null || (hoveredActor != StrategicMapWidget.this
                    && !hoveredActor.isDescendantOf(StrategicMapWidget.this))) {
                    return false;
                }
                validate();
                float factor = (float) Math.exp(-amountY * 0.18f);
                interaction.reset();
                camera.zoomAt(camera.getZoom() * factor, localX, localY);
                invalidate();
                return true;
            }
        };
    }

    private void cancelNodeClicks() {
        dragging = true;
        interaction.reset();
        for (ClickListener clickListener : nodeClickListeners) {
            clickListener.cancel();
        }
    }

    private void handleTap(String cityId, InputEvent event) {
        MapInteractionState.Action action = interaction.tap(
            cityId, fullscreen, TimeUtils.millis(), event.getStageX(), event.getStageY());
        if (cityId != null) {
            String focusedFactionId = factionIdsByCityId.get(cityId);
            if (pinnedFactionId != null && !pinnedFactionId.equals(focusedFactionId)) {
                pinnedFactionId = null;
                refreshNodeAnimations();
            }
            citySelectionHandler.accept(cityId);
        }
        if (action == MapInteractionState.Action.ENTER_FULLSCREEN) {
            mapTapHandler.run();
        } else if (action == MapInteractionState.Action.EXIT_FULLSCREEN) {
            mapRestoreHandler.run();
        } else if (action == MapInteractionState.Action.TOGGLE_FACTION_HIGHLIGHT) {
            togglePinnedFaction(cityId);
        }
    }

    private float distance(float firstX, float firstY, float secondX, float secondY) {
        float deltaX = firstX - secondX;
        float deltaY = firstY - secondY;
        return (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    private static final class PointerPosition {
        private final float startX;
        private final float startY;
        private float localX;
        private float localY;

        private PointerPosition(float localX, float localY) {
            startX = localX;
            startY = localY;
            this.localX = localX;
            this.localY = localY;
        }
    }
}

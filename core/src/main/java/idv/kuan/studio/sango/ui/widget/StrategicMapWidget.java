package idv.kuan.studio.sango.ui.widget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final MapCameraState camera = new MapCameraState();
    private final Map<String, String> captionsByCityId = new LinkedHashMap<>();
    private final Map<String, MapNodeTone> tonesByCityId = new LinkedHashMap<>();
    private final Map<String, Integer> unreadBattlesByCityId = new LinkedHashMap<>();
    private final Map<String, TextButton> buttonsByCityId = new LinkedHashMap<>();
    private final List<Image> roadImages = new ArrayList<>();
    private final List<ClickListener> nodeClickListeners = new ArrayList<>();
    private final Map<Integer, PointerPosition> pointers = new LinkedHashMap<>();
    private StrategicMapDefinition mapDefinition;
    private Drawable terrainDrawable;
    private String selectedCityId;
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
        if (nodeFont == null || citySelectionHandler == null || mapTapHandler == null) {
            throw new IllegalArgumentException("地圖字型、選城與點擊處理不可為 null。");
        }
        this.nodeFont = nodeFont;
        this.citySelectionHandler = citySelectionHandler;
        this.mapTapHandler = mapTapHandler;
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
        if (mapDefinition == null || captionsByCityId == null
            || tonesByCityId == null || unreadBattlesByCityId == null) {
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
        validate();
        if (mapDefinition != null && selectedCityId != null) {
            MapCityNodeDefinition selectedNode = mapDefinition.requireNode(selectedCityId);
            camera.zoomAt(Math.max(camera.minimumZoom(), 1f), getWidth() / 2f, getHeight() / 2f);
            camera.centerOn(selectedNode.x * worldWidth, selectedNode.y * worldHeight);
            invalidate();
        }
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
        if (initialFocusPending) {
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
                    citySelectionHandler.accept(node.cityId);
                }
            };
            nodeButton.addListener(clickListener);
            nodeClickListeners.add(clickListener);
            if (unreadBattlesByCityId.getOrDefault(node.cityId, 0) > 0) {
                nodeButton.addAction(Actions.forever(Actions.sequence(
                    Actions.alpha(0.52f, 0.48f), Actions.alpha(1f, 0.48f)
                )));
            }
            buttonsByCityId.put(node.cityId, nodeButton);
            addActor(nodeButton);
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
                if (dragging) {
                    cancelNodeClicks();
                } else if (pointers.isEmpty() && event.getTarget() == StrategicMapWidget.this) {
                    mapTapHandler.run();
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
                camera.zoomAt(camera.getZoom() * factor, localX, localY);
                invalidate();
                return true;
            }
        };
    }

    private void cancelNodeClicks() {
        dragging = true;
        for (ClickListener clickListener : nodeClickListeners) {
            clickListener.cancel();
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

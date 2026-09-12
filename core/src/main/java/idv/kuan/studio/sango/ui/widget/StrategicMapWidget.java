package idv.kuan.studio.sango.ui.widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import com.badlogic.gdx.math.Rectangle;
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
    private static final float REGIONAL_WORLD_WIDTH = 4200f;
    private static final float REGIONAL_WORLD_HEIGHT = 2800f;
    private static final float GLOBAL_WORLD_WIDTH = 8400f;
    private static final float GLOBAL_WORLD_HEIGHT = 5600f;
    private static final float OVERVIEW_ZOOM_THRESHOLD = 0.55f;
    private static final float DETAIL_TERRAIN_ZOOM_THRESHOLD = 0.42f;
    private static final float DRAG_THRESHOLD = 12f;
    private final BitmapFont nodeFont;
    private final Consumer<String> citySelectionHandler;
    private final Consumer<String> cityPreviewHandler;
    private final Runnable mapTapHandler;
    private final Runnable mapRestoreHandler;
    private final MapInteractionState interaction = new MapInteractionState();
    private boolean fullscreen;
    private final MapCameraState camera = new MapCameraState();
    private final Map<String, String> captionsByCityId = new LinkedHashMap<>();
    private final Map<String, MapNodeTone> tonesByCityId = new LinkedHashMap<>();
    private final Map<String, Integer> unreadBattlesByCityId = new LinkedHashMap<>();
    private final Map<String, String> factionIdsByCityId = new LinkedHashMap<>();
    private final Set<String> capitalCityIds = new HashSet<>();
    private final Map<String, TextButton> buttonsByCityId = new LinkedHashMap<>();
    private final Map<String, Image> markerHalosByCityId = new LinkedHashMap<>();
    private final Map<String, Image> markersByCityId = new LinkedHashMap<>();
    private final Map<String, Image> outlinesByCityId = new LinkedHashMap<>();
    private final Map<String, Image> labelLeadersByCityId = new LinkedHashMap<>();
    private final List<Image> roadImages = new ArrayList<>();
    private final List<ClickListener> nodeClickListeners = new ArrayList<>();
    private final Map<Integer, PointerPosition> pointers = new LinkedHashMap<>();
    private StrategicMapDefinition mapDefinition;
    private TerrainTileProvider terrainTileProvider;
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
        this(nodeFont, citySelectionHandler, ignored -> { }, () -> { }, () -> { });
    }

    public StrategicMapWidget(
        BitmapFont nodeFont,
        Consumer<String> citySelectionHandler,
        Runnable mapTapHandler
    ) {
        this(nodeFont, citySelectionHandler, ignored -> { }, mapTapHandler, () -> { });
    }

    public StrategicMapWidget(BitmapFont nodeFont, Consumer<String> citySelectionHandler,
        Runnable mapTapHandler, Runnable mapRestoreHandler) {
        this(nodeFont, citySelectionHandler, ignored -> { }, mapTapHandler, mapRestoreHandler);
    }

    public StrategicMapWidget(BitmapFont nodeFont, Consumer<String> citySelectionHandler,
        Consumer<String> cityPreviewHandler, Runnable mapTapHandler, Runnable mapRestoreHandler) {
        if (nodeFont == null || citySelectionHandler == null || mapTapHandler == null
            || cityPreviewHandler == null || mapRestoreHandler == null) {
            throw new IllegalArgumentException("地圖字型、選城與點擊處理不可為 null。");
        }
        this.nodeFont = nodeFont;
        this.citySelectionHandler = citySelectionHandler;
        this.cityPreviewHandler = cityPreviewHandler;
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
            Map.of(), Set.of(), null, null, selectedCityId);
    }

    public void setMapData(
        StrategicMapDefinition mapDefinition,
        Map<String, String> captionsByCityId,
        Map<String, MapNodeTone> tonesByCityId,
        Map<String, Integer> unreadBattlesByCityId,
        Map<String, String> factionIdsByCityId,
        Set<String> capitalCityIds,
        String playerFactionId,
        String neutralFactionId,
        String selectedCityId
    ) {
        if (mapDefinition == null || captionsByCityId == null
            || tonesByCityId == null || unreadBattlesByCityId == null
            || factionIdsByCityId == null || capitalCityIds == null) {
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
        this.capitalCityIds.clear();
        this.capitalCityIds.addAll(capitalCityIds);
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

    public void setTerrainTileProvider(TerrainTileProvider terrainTileProvider) {
        this.terrainTileProvider = terrainTileProvider;
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
        boolean globalMap = mapDefinition.nodes.length >= 60;
        boolean regionalMap = !globalMap && mapDefinition.nodes.length > 12;
        worldWidth = globalMap ? GLOBAL_WORLD_WIDTH
            : regionalMap ? REGIONAL_WORLD_WIDTH : getWidth();
        worldHeight = globalMap ? GLOBAL_WORLD_HEIGHT
            : regionalMap ? REGIONAL_WORLD_HEIGHT : getHeight();
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
        if (terrainTileProvider == null || worldWidth <= 0f || worldHeight <= 0f) {
            return;
        }
        float originalPackedColor = batch.getPackedColor();
        batch.setColor(getColor().r, getColor().g, getColor().b, getColor().a * parentAlpha);
        if (terrainTileProvider.hasDetailTiles()
            && camera.getZoom() >= DETAIL_TERRAIN_ZOOM_THRESHOLD) {
            drawVisibleTerrainTiles(batch);
        } else {
            Drawable overview = terrainTileProvider.overviewDrawable();
            if (overview != null) {
                overview.draw(batch, camera.screenX(0f), camera.screenY(0f),
                    worldWidth * camera.getZoom(), worldHeight * camera.getZoom());
            }
        }
        batch.setPackedColor(originalPackedColor);
    }

    private void drawVisibleTerrainTiles(Batch batch) {
        int columns = terrainTileProvider.tileColumns();
        int rows = terrainTileProvider.tileRows();
        float tileWorldWidth = worldWidth / columns;
        float tileWorldHeight = worldHeight / rows;
        float drawWidth = tileWorldWidth * camera.getZoom();
        float drawHeight = tileWorldHeight * camera.getZoom();
        for (int row = 0; row < rows; row++) {
            float worldY = worldHeight - (row + 1) * tileWorldHeight;
            float drawY = camera.screenY(worldY);
            if (drawY + drawHeight < 0f || drawY > getHeight()) {
                continue;
            }
            for (int column = 0; column < columns; column++) {
                float drawX = camera.screenX(column * tileWorldWidth);
                if (drawX + drawWidth < 0f || drawX > getWidth()) {
                    continue;
                }
                Drawable tile = terrainTileProvider.detailTileDrawable(column, row);
                if (tile != null) {
                    tile.draw(batch, drawX, drawY, drawWidth, drawHeight);
                }
            }
        }
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
        markerHalosByCityId.clear();
        markersByCityId.clear();
        outlinesByCityId.clear();
        labelLeadersByCityId.clear();
        roadImages.clear();
        nodeClickListeners.clear();
        for (CityConnectionDefinition connection : mapDefinition.connections) {
            Image lineImage = new Image(SangoUiStyles.createMapLineDrawable());
            lineImage.setTouchable(Touchable.disabled);
            roadImages.add(lineImage);
            addActor(lineImage);
        }
        for (MapCityNodeDefinition node : mapDefinition.nodes) {
            Image markerHalo = new Image(SangoUiStyles.createMapCityMarkerHaloDrawable(
                node.cityId.equals(selectedCityId)));
            markerHalo.setTouchable(Touchable.disabled);
            markerHalo.setOrigin(Align.center);
            markerHalo.setRotation(45f);
            markerHalosByCityId.put(node.cityId, markerHalo);
            addActor(markerHalo);
        }
        // 所有勢力色核心都位於外層之上，密集節點互相靠近時也不會被別城外層蓋住。
        for (MapCityNodeDefinition node : mapDefinition.nodes) {
            Image marker = new Image(SangoUiStyles.createMapCityMarkerDrawable(
                tonesByCityId.getOrDefault(node.cityId, MapNodeTone.NEUTRAL),
                node.cityId.equals(selectedCityId)));
            marker.setTouchable(Touchable.disabled);
            marker.setOrigin(Align.center);
            marker.setRotation(45f);
            markersByCityId.put(node.cityId, marker);
            addActor(marker);
        }
        for (MapCityNodeDefinition node : mapDefinition.nodes) {
            Image labelLeader = new Image(SangoUiStyles.createMapLineDrawable());
            labelLeader.setTouchable(Touchable.disabled);
            labelLeader.setVisible(false);
            labelLeadersByCityId.put(node.cityId, labelLeader);
            addActor(labelLeader);
            Image outline = new Image(SangoUiStyles.createMapOutlineDrawable(false));
            outline.setTouchable(Touchable.disabled);
            outline.setVisible(false);
            outlinesByCityId.put(node.cityId, outline);
            addActor(outline);
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
            private Timer.Task previewTask;
            private float downX;
            private float downY;

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                if (pointer == -1) {
                    hoverTask = scheduleHighlight(cityId);
                    previewTask = schedulePreview(cityId);
                }
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (pointer == -1 && (toActor == null || (toActor != event.getListenerActor()
                    && !toActor.isDescendantOf(event.getListenerActor())))) {
                    cancelTask(hoverTask);
                    cancelTask(previewTask);
                    hoverTask = null;
                    previewTask = null;
                    clearFactionHighlight();
                    cityPreviewHandler.accept(null);
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

    private Timer.Task schedulePreview(String cityId) {
        Timer.Task task = new Timer.Task() {
            @Override public void run() {
                pendingHighlightTasks.remove(this);
                cityPreviewHandler.accept(cityId);
            }
        };
        pendingHighlightTasks.add(task);
        Timer.schedule(task, 0.20f);
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

    private void pinFaction(String cityId) {
        String factionId = factionIdsByCityId.get(cityId);
        if (factionId == null) {
            return;
        }
        pinnedFactionId = factionId;
        highlightedFactionId = null;
        refreshNodeAnimations();
    }

    private void refreshNodeAnimations() {
        for (Map.Entry<String, TextButton> entry : buttonsByCityId.entrySet()) {
            TextButton button = entry.getValue();
            button.clearActions();
            button.getColor().a = 1f;
            boolean shouldFlash = highlightedFactionId == null
                ? unreadBattlesByCityId.getOrDefault(entry.getKey(), 0) > 0
                : highlightedFactionId.equals(factionIdsByCityId.get(entry.getKey()));
            if (shouldFlash) {
                button.addAction(Actions.forever(Actions.sequence(
                    Actions.alpha(0.52f, 0.48f), Actions.alpha(1f, 0.48f)
                )));
            }
            Image outline = outlinesByCityId.get(entry.getKey());
            if (outline != null) {
                outline.setVisible(pinnedFactionId != null
                    && pinnedFactionId.equals(factionIdsByCityId.get(entry.getKey())));
            }
        }
    }

    private void positionConnections() {
        boolean overview = camera.getZoom() < OVERVIEW_ZOOM_THRESHOLD;
        float thickness = MathUtils.clamp(5f * camera.getZoom(), 1.5f, 5f);
        for (int i = 0; i < mapDefinition.connections.length; i++) {
            CityConnectionDefinition connection = mapDefinition.connections[i];
            MapCityNodeDefinition fromNode = mapDefinition.requireNode(connection.fromCityId);
            MapCityNodeDefinition toNode = mapDefinition.requireNode(connection.toCityId);
            float fromX = camera.screenX(fromNode.x * worldWidth);
            float fromY = camera.screenY(fromNode.y * worldHeight);
            float deltaX = camera.screenX(toNode.x * worldWidth) - fromX;
            float deltaY = camera.screenY(toNode.y * worldHeight) - fromY;
            Image lineImage = roadImages.get(i);
            lineImage.setVisible(!overview);
            lineImage.setBounds(fromX, fromY - thickness / 2f,
                (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY), thickness);
            lineImage.setOrigin(0f, thickness / 2f);
            lineImage.setRotation(MathUtils.atan2(deltaY, deltaX) * MathUtils.radiansToDegrees);
        }
    }

    private void positionNodes() {
        float zoom = camera.getZoom();
        boolean overview = zoom < OVERVIEW_ZOOM_THRESHOLD;
        if (overview) {
            positionOverviewNodes();
            return;
        }
        boolean globalMap = mapDefinition.nodes.length >= 60;
        float labelScale = MapLabelLayout.detailScale(zoom);
        float nodeWidth = (globalMap ? 156f : 208f) * labelScale;
        float nodeHeight = (globalMap ? 72f : 96f) * labelScale;
        List<Rectangle> occupied = new ArrayList<>();
        List<MapCityNodeDefinition> orderedNodes = new ArrayList<>(Arrays.asList(mapDefinition.nodes));
        orderedNodes.sort(Comparator.comparingInt(this::detailPriority));
        for (MapCityNodeDefinition node : orderedNodes) {
            TextButton nodeButton = buttonsByCityId.get(node.cityId);
            Image markerHalo = markerHalosByCityId.get(node.cityId);
            Image marker = markersByCityId.get(node.cityId);
            Image outline = outlinesByCityId.get(node.cityId);
            Image labelLeader = labelLeadersByCityId.get(node.cityId);
            nodeButton.setVisible(true);
            labelLeader.setVisible(false);
            String fullCaption = captionsByCityId.getOrDefault(node.cityId, node.cityId);
            nodeButton.setText(fullCaption);
            float anchorX = camera.screenX(node.x * worldWidth);
            float anchorY = camera.screenY(node.y * worldHeight);
            float edgeScale = MapLabelLayout.labelScaleForAnchor(
                anchorX, anchorY, getWidth(), getHeight());
            float markerSize = Math.max(9f, 12f * Math.min(1f, zoom));
            float haloSize = markerSize + (node.cityId.equals(selectedCityId) ? 10f : 6f);
            positionCityMarker(markerHalo, anchorX, anchorY, haloSize);
            positionCityMarker(marker, anchorX, anchorY, markerSize);
            nodeButton.getLabel().setFontScale(
                (globalMap ? 0.82f : 1.02f) * labelScale * edgeScale);
            float placedWidth = nodeWidth * edgeScale;
            float placedHeight = nodeHeight * edgeScale;
            Rectangle placement = new Rectangle(
                anchorX - nodeWidth / 2f, anchorY - nodeHeight / 2f, nodeWidth, nodeHeight);
            if (intersectsViewport(placement)) {
                placement = MapLabelLayout.place(
                    anchorX, anchorY, placedWidth, placedHeight,
                    getWidth(), getHeight(), occupied);
                positionLabelLeader(labelLeader, anchorX, anchorY,
                    placement.x + placement.width / 2f, placement.y + placement.height / 2f);
            }
            nodeButton.setBounds(placement.x, placement.y, placement.width, placement.height);
            if (outline != null) {
                float border = Math.max(4f, 7f * zoom);
                outline.setBounds(nodeButton.getX() - border, nodeButton.getY() - border,
                    placedWidth + border * 2f, placedHeight + border * 2f);
                outline.setVisible(pinnedFactionId != null
                    && pinnedFactionId.equals(factionIdsByCityId.get(node.cityId)));
            }
        }
    }

    private boolean intersectsViewport(Rectangle rectangle) {
        return rectangle.x + rectangle.width >= 0f && rectangle.x <= getWidth()
            && rectangle.y + rectangle.height >= 0f && rectangle.y <= getHeight();
    }

    private int detailPriority(MapCityNodeDefinition node) {
        if (unreadBattlesByCityId.getOrDefault(node.cityId, 0) > 0) {
            return 0;
        }
        if (playerFactionId != null && playerFactionId.equals(factionIdsByCityId.get(node.cityId))) {
            return 1;
        }
        if (capitalCityIds.contains(node.cityId)) {
            return 2;
        }
        return 3;
    }

    private void positionOverviewNodes() {
        List<Rectangle> occupied = new ArrayList<>();
        List<MapCityNodeDefinition> orderedNodes = new ArrayList<>(Arrays.asList(mapDefinition.nodes));
        orderedNodes.sort(Comparator.comparingInt(this::overviewPriority));
        for (MapCityNodeDefinition node : orderedNodes) {
            TextButton nodeButton = buttonsByCityId.get(node.cityId);
            Image markerHalo = markerHalosByCityId.get(node.cityId);
            Image marker = markersByCityId.get(node.cityId);
            Image outline = outlinesByCityId.get(node.cityId);
            Image labelLeader = labelLeadersByCityId.get(node.cityId);
            boolean visible = capitalCityIds.contains(node.cityId)
                || node.cityId.equals(selectedCityId)
                || unreadBattlesByCityId.getOrDefault(node.cityId, 0) > 0;
            float anchorX = camera.screenX(node.x * worldWidth);
            float anchorY = camera.screenY(node.y * worldHeight);
            boolean selected = node.cityId.equals(selectedCityId);
            positionCityMarker(markerHalo, anchorX, anchorY, selected ? 19f : 15f);
            positionCityMarker(marker, anchorX, anchorY, selected ? 10f : 9f);
            nodeButton.setVisible(visible);
            outline.setVisible(false);
            labelLeader.setVisible(false);
            if (!visible) {
                continue;
            }
            String caption = captionsByCityId.getOrDefault(node.cityId, node.cityId)
                .split("\\n", 2)[0];
            float edgeScale = MapLabelLayout.labelScaleForAnchor(
                anchorX, anchorY, getWidth(), getHeight());
            float nodeWidth = Math.min(124f, Math.max(72f, 16f + caption.length() * 12f))
                * edgeScale;
            float nodeHeight = 34f * edgeScale;
            Rectangle placement = MapLabelLayout.place(
                anchorX, anchorY, nodeWidth, nodeHeight, getWidth(), getHeight(), occupied);
            nodeButton.setText(caption);
            nodeButton.getLabel().setFontScale(0.50f * edgeScale);
            nodeButton.setBounds(placement.x, placement.y, placement.width, placement.height);
            positionLabelLeader(labelLeader, anchorX, anchorY,
                placement.x + placement.width / 2f, placement.y + placement.height / 2f);
        }
    }

    private int overviewPriority(MapCityNodeDefinition node) {
        // 因選取而臨時出現的標籤最後放置，不擠動原有首都／戰事標籤。
        if (!capitalCityIds.contains(node.cityId)
            && unreadBattlesByCityId.getOrDefault(node.cityId, 0) == 0) {
            return 3;
        }
        if (unreadBattlesByCityId.getOrDefault(node.cityId, 0) > 0) {
            return 0;
        }
        if (playerFactionId != null && playerFactionId.equals(factionIdsByCityId.get(node.cityId))) {
            return 1;
        }
        return 2;
    }

    private void positionLabelLeader(
        Image leader,
        float anchorX,
        float anchorY,
        float labelX,
        float labelY
    ) {
        float deltaX = labelX - anchorX;
        float deltaY = labelY - anchorY;
        float length = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (length < 8f) {
            leader.setVisible(false);
            return;
        }
        leader.setVisible(true);
        leader.setBounds(anchorX, anchorY - 1f, length, 2f);
        leader.setOrigin(0f, 1f);
        leader.setRotation(MathUtils.atan2(deltaY, deltaX) * MathUtils.radiansToDegrees);
    }

    private void positionCityMarker(Image marker, float anchorX, float anchorY, float size) {
        marker.setVisible(anchorX + size >= 0f && anchorX - size <= getWidth()
            && anchorY + size >= 0f && anchorY - size <= getHeight());
        marker.setBounds(anchorX - size / 2f, anchorY - size / 2f, size, size);
        marker.setOrigin(Align.center);
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
        } else if (action == MapInteractionState.Action.NONE && pinnedFactionId != null) {
            pinnedFactionId = null;
            refreshNodeAnimations();
        }
        if (action == MapInteractionState.Action.ENTER_FULLSCREEN) {
            mapTapHandler.run();
        } else if (action == MapInteractionState.Action.EXIT_FULLSCREEN) {
            mapRestoreHandler.run();
        } else if (action == MapInteractionState.Action.TOGGLE_FACTION_HIGHLIGHT) {
            pinFaction(cityId);
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

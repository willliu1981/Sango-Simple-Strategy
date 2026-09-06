package idv.kuan.studio.sango.ui.widget;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;

import idv.kuan.studio.sango.domain.definition.CityConnectionDefinition;
import idv.kuan.studio.sango.domain.definition.MapCityNodeDefinition;
import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.ui.theme.MapNodeTone;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/**
 * 以 Scene2D 節點與連線繪製的小型戰略地圖，不依賴額外地圖貼圖。
 */
public final class StrategicMapWidget extends WidgetGroup {
    private static final float NODE_WIDTH = 178f;
    private static final float NODE_HEIGHT = 78f;
    private static final float LINE_THICKNESS = 6f;

    private final BitmapFont nodeFont;
    private final Consumer<String> citySelectionHandler;
    private final Map<String, String> captionsByCityId = new LinkedHashMap<>();
    private final Map<String, MapNodeTone> tonesByCityId = new LinkedHashMap<>();

    private StrategicMapDefinition mapDefinition;
    private String selectedCityId;

    public StrategicMapWidget(
        BitmapFont nodeFont,
        Consumer<String> citySelectionHandler
    ) {
        if (nodeFont == null || citySelectionHandler == null) {
            throw new IllegalArgumentException(
                "nodeFont 與 citySelectionHandler 不可為 null。"
            );
        }
        this.nodeFont = nodeFont;
        this.citySelectionHandler = citySelectionHandler;
        setTransform(false);
        setTouchable(Touchable.childrenOnly);
    }

    public void setMapData(
        StrategicMapDefinition mapDefinition,
        Map<String, String> captionsByCityId,
        Map<String, MapNodeTone> tonesByCityId,
        String selectedCityId
    ) {
        if (mapDefinition == null || captionsByCityId == null || tonesByCityId == null) {
            throw new IllegalArgumentException("地圖顯示資料不可為 null。");
        }
        this.mapDefinition = mapDefinition;
        this.captionsByCityId.clear();
        this.captionsByCityId.putAll(captionsByCityId);
        this.tonesByCityId.clear();
        this.tonesByCityId.putAll(tonesByCityId);
        this.selectedCityId = selectedCityId;
        rebuildChildren();
        invalidateHierarchy();
    }

    @Override
    public void layout() {
        if (mapDefinition == null) {
            return;
        }
        positionConnections();
        positionNodes();
    }

    private void rebuildChildren() {
        clearChildren();
        if (mapDefinition == null) {
            return;
        }

        for (CityConnectionDefinition connectionDefinition : mapDefinition.connections) {
            Image lineImage = new Image(SangoUiStyles.createMapLineDrawable());
            lineImage.setName(connectionName(connectionDefinition));
            lineImage.setTouchable(Touchable.disabled);
            addActor(lineImage);
        }

        for (MapCityNodeDefinition nodeDefinition : mapDefinition.nodes) {
            String caption = captionsByCityId.getOrDefault(
                nodeDefinition.cityId,
                nodeDefinition.cityId
            );
            MapNodeTone nodeTone = tonesByCityId.getOrDefault(
                nodeDefinition.cityId,
                MapNodeTone.NEUTRAL
            );
            boolean selected = nodeDefinition.cityId.equals(selectedCityId);
            TextButton nodeButton = new TextButton(
                caption,
                SangoUiStyles.createMapNodeStyle(nodeFont, nodeTone, selected)
            );
            nodeButton.setName(nodeName(nodeDefinition.cityId));
            nodeButton.getLabel().setAlignment(Align.center);
            nodeButton.getLabel().setWrap(true);
            nodeButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    citySelectionHandler.accept(nodeDefinition.cityId);
                }
            });
            addActor(nodeButton);
        }
    }

    private void positionConnections() {
        for (CityConnectionDefinition connectionDefinition : mapDefinition.connections) {
            Image lineImage = findActor(connectionName(connectionDefinition));
            MapCityNodeDefinition fromNode = mapDefinition.requireNode(
                connectionDefinition.fromCityId
            );
            MapCityNodeDefinition toNode = mapDefinition.requireNode(
                connectionDefinition.toCityId
            );
            float fromX = fromNode.x * getWidth();
            float fromY = fromNode.y * getHeight();
            float toX = toNode.x * getWidth();
            float toY = toNode.y * getHeight();
            float deltaX = toX - fromX;
            float deltaY = toY - fromY;
            float length = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            float rotation = MathUtils.atan2(deltaY, deltaX) * MathUtils.radiansToDegrees;
            lineImage.setBounds(
                fromX,
                fromY - LINE_THICKNESS / 2f,
                length,
                LINE_THICKNESS
            );
            lineImage.setOrigin(0f, LINE_THICKNESS / 2f);
            lineImage.setRotation(rotation);
        }
    }

    private void positionNodes() {
        for (MapCityNodeDefinition nodeDefinition : mapDefinition.nodes) {
            TextButton nodeButton = findActor(nodeName(nodeDefinition.cityId));
            float centerX = nodeDefinition.x * getWidth();
            float centerY = nodeDefinition.y * getHeight();
            nodeButton.setBounds(
                centerX - NODE_WIDTH / 2f,
                centerY - NODE_HEIGHT / 2f,
                NODE_WIDTH,
                NODE_HEIGHT
            );
        }
    }

    private String connectionName(CityConnectionDefinition connectionDefinition) {
        return "connection-" + connectionDefinition.fromCityId + "-"
            + connectionDefinition.toCityId;
    }

    private String nodeName(String cityId) {
        return "node-" + cityId;
    }
}

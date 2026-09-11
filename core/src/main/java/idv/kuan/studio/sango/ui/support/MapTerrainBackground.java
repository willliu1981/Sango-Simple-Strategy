package idv.kuan.studio.sango.ui.support;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;

import idv.kuan.studio.sango.domain.definition.StrategicMapDefinition;
import idv.kuan.studio.sango.ui.widget.TerrainTileProvider;

/**
 * 地圖 Screen 專屬的背景資源。一張地圖只載入一次，切換地圖或 Screen dispose 時釋放。
 * FileTextureData 使用 LibGDX managed texture，避免 Android Context 重建後留下失效貼圖。
 */
public final class MapTerrainBackground implements Disposable, TerrainTileProvider {
    private static final int MAX_CACHED_DETAIL_TILES = 8;
    private String loadedAssetPath;
    private Texture terrainTexture;
    private Drawable terrainDrawable;
    private String[] detailAssetPaths = new String[0];
    private int tileColumns;
    private int tileRows;
    private final LinkedHashMap<Integer, TileResource> detailTiles =
        new LinkedHashMap<>(16, 0.75f, true);

    public void configure(StrategicMapDefinition mapDefinition) {
        String assetPath = mapDefinition == null ? null : mapDefinition.backgroundAssetPath;
        String[] nextTilePaths = mapDefinition == null || mapDefinition.backgroundTileAssetPaths == null
            ? new String[0] : mapDefinition.backgroundTileAssetPaths;
        int nextColumns = mapDefinition == null ? 0 : mapDefinition.backgroundTileColumns;
        int nextRows = mapDefinition == null ? 0 : mapDefinition.backgroundTileRows;
        if (assetPath == null || assetPath.isBlank()) {
            dispose();
            return;
        }
        if (assetPath.equals(loadedAssetPath)
            && Arrays.equals(detailAssetPaths, nextTilePaths)
            && tileColumns == nextColumns && tileRows == nextRows) {
            return;
        }
        // 先建立新資源，成功後才釋放上一張；不由 Screen 或 Widget 重複 dispose 同一張圖。
        Texture nextTexture = new Texture(Gdx.files.internal(assetPath));
        nextTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        nextTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        dispose();
        terrainTexture = nextTexture;
        terrainDrawable = new TextureRegionDrawable(new TextureRegion(nextTexture));
        loadedAssetPath = assetPath;
        detailAssetPaths = Arrays.copyOf(nextTilePaths, nextTilePaths.length);
        tileColumns = nextColumns;
        tileRows = nextRows;
    }

    @Override
    public Drawable overviewDrawable() {
        return terrainDrawable;
    }

    @Override
    public boolean hasDetailTiles() {
        return tileColumns > 0 && tileRows > 0
            && detailAssetPaths.length == tileColumns * tileRows;
    }

    @Override
    public int tileColumns() {
        return tileColumns;
    }

    @Override
    public int tileRows() {
        return tileRows;
    }

    @Override
    public Drawable detailTileDrawable(int column, int row) {
        if (!hasDetailTiles() || column < 0 || column >= tileColumns || row < 0 || row >= tileRows) {
            return null;
        }
        int index = row * tileColumns + column;
        TileResource cached = detailTiles.get(index);
        if (cached != null) {
            return cached.drawable;
        }
        Texture texture = new Texture(Gdx.files.internal(detailAssetPaths[index]));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        TileResource loaded = new TileResource(texture);
        detailTiles.put(index, loaded);
        trimDetailCache();
        return loaded.drawable;
    }

    private void trimDetailCache() {
        Iterator<Map.Entry<Integer, TileResource>> iterator = detailTiles.entrySet().iterator();
        while (detailTiles.size() > MAX_CACHED_DETAIL_TILES && iterator.hasNext()) {
            TileResource eldest = iterator.next().getValue();
            iterator.remove();
            eldest.dispose();
        }
    }

    @Override
    public void dispose() {
        if (terrainTexture != null) {
            terrainTexture.dispose();
        }
        for (TileResource resource : detailTiles.values()) {
            resource.dispose();
        }
        detailTiles.clear();
        terrainTexture = null;
        terrainDrawable = null;
        loadedAssetPath = null;
        detailAssetPaths = new String[0];
        tileColumns = 0;
        tileRows = 0;
    }

    private static final class TileResource implements Disposable {
        private final Texture texture;
        private final Drawable drawable;

        private TileResource(Texture texture) {
            this.texture = texture;
            drawable = new TextureRegionDrawable(new TextureRegion(texture));
        }

        @Override
        public void dispose() {
            texture.dispose();
        }
    }
}

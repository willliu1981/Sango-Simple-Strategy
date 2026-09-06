package idv.kuan.studio.sango.ui.support;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;

/**
 * 地圖 Screen 專屬的背景資源。一張地圖只載入一次，切換地圖或 Screen dispose 時釋放。
 * FileTextureData 使用 LibGDX managed texture，避免 Android Context 重建後留下失效貼圖。
 */
public final class MapTerrainBackground implements Disposable {
    private String loadedAssetPath;
    private Texture terrainTexture;
    private Drawable terrainDrawable;

    public Drawable getOrLoad(String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            dispose();
            return null;
        }
        if (assetPath.equals(loadedAssetPath)) {
            return terrainDrawable;
        }
        // 先建立新資源，成功後才釋放上一張；不由 Screen 或 Widget 重複 dispose 同一張圖。
        Texture nextTexture = new Texture(Gdx.files.internal(assetPath));
        nextTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        nextTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        dispose();
        terrainTexture = nextTexture;
        terrainDrawable = new TextureRegionDrawable(new TextureRegion(nextTexture));
        loadedAssetPath = assetPath;
        return terrainDrawable;
    }

    @Override
    public void dispose() {
        if (terrainTexture != null) {
            terrainTexture.dispose();
        }
        terrainTexture = null;
        terrainDrawable = null;
        loadedAssetPath = null;
    }
}

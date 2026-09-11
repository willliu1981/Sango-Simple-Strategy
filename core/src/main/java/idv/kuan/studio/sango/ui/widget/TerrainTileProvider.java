package idv.kuan.studio.sango.ui.widget;

import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/**
 * 提供戰略地圖總覽圖與可延遲載入的細節圖塊。
 */
public interface TerrainTileProvider {
    Drawable overviewDrawable();

    boolean hasDetailTiles();

    int tileColumns();

    int tileRows();

    Drawable detailTileDrawable(int column, int row);
}

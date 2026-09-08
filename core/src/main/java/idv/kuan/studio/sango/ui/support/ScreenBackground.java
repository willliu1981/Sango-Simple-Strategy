package idv.kuan.studio.sango.ui.support;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Scaling;

import idv.kuan.studio.libgdx.simpleui.Sui;

/**
 * 三個主畫面共用的全螢幕背景管理器。
 */
public final class ScreenBackground {
    private Image image;

    public void attach(Stage stage, String texturePath) {
        remove();

        Texture backgroundTexture = Sui.resources.manager().getOrLoadTextureByPath(texturePath);
        backgroundTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        image = new Image(backgroundTexture);
        image.setScaling(Scaling.fill);
        image.setTouchable(Touchable.disabled);
        resize(stage);
        stage.addActor(image);
        image.toBack();
    }

    public void resize(Stage stage) {
        if (image == null || stage == null) {
            return;
        }
        image.setBounds(0f, 0f, stage.getWidth(), stage.getHeight());
    }

    public void remove() {
        if (image != null) {
            image.remove();
            image = null;
        }
    }
}

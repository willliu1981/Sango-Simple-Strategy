package idv.kuan.studio.sango;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiGame;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.id.ScreenId;

/**
 * 所有平台共用的 Sango ApplicationListener。
 */
public final class Main extends SuiGame {
    @Override
    protected void afterCreate() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        SangoServices.initialize();
        Sui.screens.set(ScreenId.LOBBY);
    }

    @Override
    public void render() {
        SangoServices.audio().update(Gdx.graphics.getDeltaTime());
        super.render();
    }

    @Override
    public void pause() {
        SangoServices.audio().pause();
        super.pause();
    }

    @Override
    public void resume() {
        super.resume();
        SangoServices.audio().resume();
    }

    @Override
    protected void beforeDispose() {
        SangoServices.dispose();
        super.beforeDispose();
    }
}

package idv.kuan.studio.sango.validation;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;

import idv.kuan.studio.sango.ui.widget.MapInteractionState;
import idv.kuan.studio.sango.ui.widget.MapInteractionState.Action;
import idv.kuan.studio.sango.ui.widget.StrategicMapWidget;

/** Exercises real widget sizing without a graphics context and gesture transitions. */
public final class MapInteractionSmokeTest {
    private static int checks;

    public static void main(String[] args) {
        MapInteractionState taps = new MapInteractionState();
        check(taps.tap("pingyuan", true, 1000, 10, 10) == Action.NONE, "Single city tap stays full");
        check(taps.tap("pingyuan", true, 1200, 12, 12) == Action.RESTORE_AND_FOCUS,
            "Same-city double tap restores and focuses");
        check(taps.tap(null, true, 2000, 10, 10) == Action.NONE, "Blank single tap stays full");
        check(taps.tap(null, true, 2200, 10, 10) == Action.NONE, "Blank double tap stays full");
        taps.tap("pingyuan", true, 3000, 10, 10);
        check(taps.tap("beihai", true, 3100, 10, 10) == Action.NONE, "Different cities do not activate");
        taps.reset();
        taps.tap(null, false, 4000, 10, 10);
        check(taps.tap(null, false, 4200, 10, 10) == Action.ENTER_FULLSCREEN,
            "Normal map double tap expands");
        taps.tap("pingyuan", true, 5000, 10, 10);
        taps.reset();
        check(taps.tap("pingyuan", true, 5200, 10, 10) == Action.NONE, "Drag resets pending tap");
        check(taps.tap("pingyuan", true, 5300, 100, 100) == Action.NONE, "Distant taps do not activate");
        check(taps.tap("pingyuan", true, 6000, 100, 100) == Action.NONE, "Slow taps do not activate");
        check(taps.tap("pingyuan", false, 6100, 100, 100) == Action.NONE, "Mode change resets sequence");

        BitmapFont font = new BitmapFont(new BitmapFont.BitmapFontData(), new TextureRegion(), false);
        StrategicMapWidget widget = new StrategicMapWidget(font, ignored -> { });
        Group normalHost = new Group();
        normalHost.setSize(1040, 626);
        normalHost.addActor(widget);
        widget.validate();
        check(widget.getWidth() == 1040 && widget.getHeight() == 626, "Initial layout fills actual host");
        normalHost.setSize(1120, 700);
        widget.validate();
        check(widget.getWidth() == 1120 && widget.getHeight() == 700,
            "Parent resize propagates without a screen resize callback");
        Group fullHost = new Group();
        fullHost.setSize(1920, 1080);
        fullHost.addActor(widget);
        widget.validate();
        check(widget.getWidth() == 1920 && widget.getHeight() == 1080, "Reparent fills full host");
        normalHost.addActor(widget);
        widget.validate();
        check(widget.getWidth() == 1120 && widget.getHeight() == 700, "Restore uses current normal size");
        font.dispose();
        System.out.println("Map sizing and gestures: PASS; checks=" + checks);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

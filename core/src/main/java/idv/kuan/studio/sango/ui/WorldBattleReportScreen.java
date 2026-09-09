package idv.kuan.studio.sango.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.utils.Align;

import idv.kuan.studio.libgdx.simpleui.Sui;
import idv.kuan.studio.libgdx.simpleui.SuiScreen;
import idv.kuan.studio.libgdx.simpleui.builder.BuiltUI;
import idv.kuan.studio.sango.domain.model.BattleReport;
import idv.kuan.studio.sango.domain.model.GameState;
import idv.kuan.studio.sango.runtime.SangoServices;
import idv.kuan.studio.sango.ui.id.ScreenId;
import idv.kuan.studio.sango.ui.support.BattleReportCatalog;
import idv.kuan.studio.sango.ui.support.ScreenMusic;
import idv.kuan.studio.sango.ui.theme.SangoUiStyles;

/** All factions' battles, with the player's battles first and revisitable after reading. */
public final class WorldBattleReportScreen extends SuiScreen {
    private Group host;
    private Table rows;
    private ScrollPane scroll;

    @Override
    protected BuiltUI buildUI(UIFactory factory) {
        return factory.begin().skipToRegisterTemplate().skipToRegisterXml()
            .registerXml("ui/world_battle_report.xml").skipToBuild().buildUI();
    }

    @Override
    protected void onUIBuilt(BuiltUI builtUI) {
        host = ui.getActor("world_battle_scroll_host", Group.class);
        rows = new Table();
        rows.top().left();
        scroll = new ScrollPane(rows);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        host.addActor(scroll);
        SangoUiStyles.applySecondaryButton(ui.getActor("world_battle_back_button", TextButton.class));
        ui.onClick("world_battle_back_button", () -> Sui.screens.set(ScreenId.STRATEGIC_MAP));
    }

    @Override
    protected void afterShow() {
        if (!SangoServices.session().hasCurrentState()) {
            Sui.screens.set(ScreenId.LOBBY);
            return;
        }
        ScreenMusic.play(ScreenId.BATTLE_REPORT);
        GameState state = SangoServices.session().requireCurrentState();
        TextButton template = ui.getActor("world_battle_row_template", TextButton.class);
        TextButton.TextButtonStyle rowStyle = new TextButton.TextButtonStyle(template.getStyle());
        rowStyle.font = template.getLabel().getStyle().font;
        rows.clearChildren();
        for (BattleReport report : BattleReportCatalog.world(state)) {
            boolean player = report.involvesFaction(state.playerFactionId);
            String title = text(player ? "world_battle_player" : "world_battle_other",
                player ? "我方戰事" : "其他勢力") + " | "
                + text(report.read ? "world_battle_read" : "world_battle_unread", report.read ? "已讀" : "未讀")
                + " | " + report.resolvedYear + " / " + report.resolvedMonth + "\n"
                + faction(report.attackerFactionId) + " → " + faction(report.defenderFactionId)
                + " | " + city(report.targetCityId) + " | "
                + text("world_battle_winner", "勝方：{0}", faction(report.winnerFactionId));
            TextButton row = new TextButton(title, new TextButton.TextButtonStyle(rowStyle));
            row.getLabel().setWrap(true);
            row.getLabel().setAlignment(Align.left);
            if (player) SangoUiStyles.applyPrimaryButton(row);
            else SangoUiStyles.applySecondaryButton(row);
            row.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent event, Actor actor) {
                    SangoServices.session().openBattleReport(report.battleId, ScreenId.WORLD_BATTLE_REPORT);
                    Sui.screens.set(ScreenId.BATTLE_REPORT);
                }
            });
            rows.add(row).width(Math.max(1f, host.getWidth() - 24f)).height(100f).padBottom(10f);
            rows.row();
        }
        if (rows.getChildren().size == 0) {
            TextButton empty = new TextButton(text("world_battle_empty", "目前沒有戰事紀錄。"), rowStyle);
            empty.setDisabled(true);
            rows.add(empty).width(Math.max(1f, host.getWidth() - 24f)).height(80f);
        }
        resizeScroll();
        scroll.setScrollY(0f);
        scroll.updateVisualScroll();
        stage.setScrollFocus(scroll);
        stage.addAction(Actions.sequence(Actions.delay(0.01f), Actions.run(() -> {
            resizeScroll();
            scroll.setScrollY(0f);
            scroll.updateVisualScroll();
        })));
    }

    @Override protected void afterResize(int width, int height) { resizeScroll(); }

    private void resizeScroll() {
        if (host == null || scroll == null) return;
        float rowWidth = Math.max(1f, host.getWidth() - 24f);
        scroll.setBounds(0, 0, host.getWidth(), host.getHeight());
        for (var cell : rows.getCells()) {
            cell.width(rowWidth);
        }
        rows.invalidateHierarchy();
        rows.pack();
        scroll.validate();
    }

    @Override protected InputProcessor createInputProcessor(Stage stage) {
        return new InputMultiplexer(new InputAdapter() {
            @Override public boolean keyDown(int key) {
                if (key != Input.Keys.ESCAPE && key != Input.Keys.BACK) return false;
                Sui.screens.set(ScreenId.STRATEGIC_MAP);
                return true;
            }
        }, stage);
    }

    private String city(String id) { return text(SangoServices.definitions().requireCity(id).nameKey, id); }
    private String faction(String id) { return text(SangoServices.definitions().requireFaction(id).nameKey, id); }
    private String text(String key, String fallback, Object... args) {
        return Sui.i18n.manager().getText("literal", key, fallback, args);
    }
}

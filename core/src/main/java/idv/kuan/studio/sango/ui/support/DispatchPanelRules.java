package idv.kuan.studio.sango.ui.support;

/** 派兵視窗中攻擊與運兵的顯示差異。 */
public final class DispatchPanelRules {
    private DispatchPanelRules() {
    }

    public static boolean showsTacticSelection(boolean transfer) {
        return !transfer;
    }
}

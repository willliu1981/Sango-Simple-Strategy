package idv.kuan.studio.sango.domain.service;

/**
 * 以固定輸入產生可重現的 0～99 擲骰，避免讀檔重試改變事件結果。
 */
public final class DeterministicEventRoller {
    public int rollPercent(String scenarioId, String cityId, int year, String eventCode) {
        String source = scenarioId + "|" + cityId + "|" + year + "|" + eventCode;
        int hash = 0x811C9DC5;
        for (int i = 0; i < source.length(); i++) {
            hash ^= source.charAt(i);
            hash *= 0x01000193;
        }
        return Math.floorMod(hash, 100);
    }
}

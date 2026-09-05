package idv.kuan.studio.sango.repository.save;

/**
 * 存檔讀寫或格式驗證失敗。
 */
public final class SaveGameException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SaveGameException(String message) {
        super(message);
    }

    public SaveGameException(String message, Throwable cause) {
        super(message, cause);
    }
}

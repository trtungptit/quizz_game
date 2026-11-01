package quizgame.server.service;

/** Lỗi nghiệp vụ với mã giao thức và thông báo an toàn cho người dùng. */
public final class ServiceException extends RuntimeException {
    private final String code;
    public ServiceException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}

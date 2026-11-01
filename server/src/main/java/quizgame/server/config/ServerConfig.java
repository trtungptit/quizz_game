package quizgame.server.config;

import java.net.URI;
import java.util.Map;

/** Cấu hình được đọc từ biến môi trường; không ghi log thông tin đăng nhập DB. */
public final class ServerConfig {
    private final String host;
    private final int port;
    private final String databaseUrl;
    private final String databaseUser;
    private final String databasePassword;

    private ServerConfig(String host, int port, String databaseUrl, String databaseUser, String databasePassword) {
        this.host = host;
        this.port = port;
        this.databaseUrl = databaseUrl;
        this.databaseUser = databaseUser;
        this.databasePassword = databasePassword;
    }

    public static ServerConfig from(Map<String, String> env) {
        String host = nonblank(env.getOrDefault("QUIZ_SERVER_HOST", "127.0.0.1"), "QUIZ_SERVER_HOST");
        int port;
        try {
            port = Integer.parseInt(env.getOrDefault("QUIZ_SERVER_PORT", "9090"));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("QUIZ_SERVER_PORT không hợp lệ");
        }
        if (port < 1 || port > 65535) throw new IllegalArgumentException("QUIZ_SERVER_PORT ngoài phạm vi 1-65535");
        String url = nonblank(env.get("QUIZ_DB_URL"), "QUIZ_DB_URL");
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            String query = uri.getQuery();
            boolean embedsCredentials = query != null && java.util.Arrays.stream(query.split("&"))
                    .map(part -> part.split("=", 2)[0].toLowerCase(java.util.Locale.ROOT))
                    .anyMatch(key -> key.equals("user") || key.equals("password"));
            if (!url.startsWith("jdbc:mysql://") || uri.getUserInfo() != null || embedsCredentials
                    || !"quiz_game".equals(uri.getPath().replaceFirst("^/", ""))) {
                throw new IllegalArgumentException("QUIZ_DB_URL phải trỏ đúng database quiz_game qua MySQL");
            }
        } catch (RuntimeException ex) {
            if (ex instanceof IllegalArgumentException && ex.getMessage() != null
                    && ex.getMessage().startsWith("QUIZ_DB_URL")) throw ex;
            throw new IllegalArgumentException("QUIZ_DB_URL không hợp lệ hoặc không trỏ tới quiz_game");
        }
        return new ServerConfig(host, port, url, nonblank(env.get("QUIZ_DB_USER"), "QUIZ_DB_USER"),
                nonblank(env.get("QUIZ_DB_PASSWORD"), "QUIZ_DB_PASSWORD"));
    }

    private static String nonblank(String value, String key) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Thiếu cấu hình " + key);
        return value;
    }

    public String host() { return host; }
    public int port() { return port; }
    public String databaseUrl() { return databaseUrl; }
    public String databaseName() { return "quiz_game"; }
    public String databaseUser() { return databaseUser; }
    public String databasePassword() { return databasePassword; }

    @Override public String toString() {
        return "ServerConfig[host=" + host + ", port=" + port + ", database=<redacted>]";
    }
}

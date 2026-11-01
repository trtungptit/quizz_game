package quizgame.server.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Đọc KEY=VALUE từ .env; biến môi trường của tiến trình được ưu tiên hơn. */
public final class EnvironmentFile {
    private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private EnvironmentFile() { }

    public static Map<String, String> load(Path path, Map<String, String> processEnvironment) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        if (Files.exists(path)) {
            if (!Files.isRegularFile(path)) throw new IOException("Đường dẫn .env không phải file thường");
            var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int separator = line.indexOf('=');
                if (separator < 1) throw invalidLine(i + 1);
                String key = line.substring(0, separator).trim();
                if (!KEY.matcher(key).matches() || values.containsKey(key)) throw invalidLine(i + 1);
                String value = line.substring(separator + 1).trim();
                if (value.startsWith("'") || value.startsWith("\"")) {
                    char quote = value.charAt(0);
                    if (value.length() < 2 || value.charAt(value.length() - 1) != quote) throw invalidLine(i + 1);
                    value = value.substring(1, value.length() - 1);
                } else if (value.endsWith("'") || value.endsWith("\"")) {
                    throw invalidLine(i + 1);
                }
                values.put(key, value);
            }
        }
        values.putAll(processEnvironment);
        return Map.copyOf(values);
    }

    private static IOException invalidLine(int line) {
        return new IOException("Định dạng file .env không hợp lệ ở dòng " + line);
    }
}

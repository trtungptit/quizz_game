package quizgame.server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentFileTest {
    @TempDir Path tempDir;

    @Test
    void readsEnvValuesAndLetsProcessEnvironmentOverrideThem() throws IOException {
        Path file = tempDir.resolve(".env");
        Files.writeString(file, "# local config\nQUIZ_DB_USER='demo user'\nQUIZ_DB_PASSWORD=p#word\nQUIZ_SERVER_PORT=9001\n");

        Map<String, String> values = EnvironmentFile.load(file, Map.of("QUIZ_SERVER_PORT", "9090", "PATH", "kept-out"));

        assertEquals("demo user", values.get("QUIZ_DB_USER"));
        assertEquals("p#word", values.get("QUIZ_DB_PASSWORD"));
        assertEquals("9090", values.get("QUIZ_SERVER_PORT"));
        assertEquals("kept-out", values.get("PATH"));
    }

    @Test
    void missingEnvFileFallsBackToProcessEnvironment() throws IOException {
        Map<String, String> values = EnvironmentFile.load(tempDir.resolve("missing.env"), Map.of("QUIZ_DB_USER", "from-process"));
        assertEquals(Map.of("QUIZ_DB_USER", "from-process"), values);
    }

    @Test
    void malformedLinesFailWithoutEchoingTheirContents() throws IOException {
        Path file = tempDir.resolve(".env");
        Files.writeString(file, "QUIZ_DB_PASSWORD=private-value\nINVALID LINE\n");

        IOException error = assertThrows(IOException.class, () -> EnvironmentFile.load(file, Map.of()));

        assertTrue(error.getMessage().contains("dòng 2"));
        assertFalse(error.getMessage().contains("private-value"));
    }
}

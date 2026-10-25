package quizgame.client.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import quizgame.common.model.QuestionDraft;
import quizgame.common.model.QuestionType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvImporterTest {
    private static final String HEADER = "Loại câu hỏi,Nội dung,Đáp án A,Đáp án B,Đáp án C,Đáp án D,Đáp án đúng,"
            + "Thời gian (giây),Ảnh\n";

    @TempDir
    Path dir;

    private ImportResult read(String content) throws IOException {
        Path file = dir.resolve("q.csv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return CsvImporter.read(file);
    }

    @Test
    void threeTypes() throws IOException {
        ImportResult r = read(HEADER
                + "Buttons,1+1=?,1,2,3,4,B,20,\n"
                + "checkboxes,Số chẵn?,1,2,3,4,\"B,D\",30,\n"
                + "TYPE ANSWER,Thủ đô VN?,,,,,Hà Nội|Ha Noi,,\n");
        assertEquals(List.of(), r.errors());
        assertEquals(3, r.questions().size());
        QuestionDraft single = r.questions().get(0);
        assertEquals(QuestionType.SINGLE, single.type());
        assertEquals(List.of(1), single.correctIndexes());
        assertEquals(20, single.timeSeconds());
        QuestionDraft multi = r.questions().get(1);
        assertEquals(QuestionType.MULTI, multi.type());
        assertEquals(List.of(1, 3), multi.correctIndexes());
        QuestionDraft type = r.questions().get(2);
        assertEquals(QuestionType.TYPE, type.type());
        assertEquals(List.of("Hà Nội", "Ha Noi"), type.acceptedAnswers());
        assertEquals(20, type.timeSeconds(), "thời gian trống = 20");
        assertEquals(List.of(), type.options());
        assertNull(type.imageBase64());
    }

    @Test
    void semicolonDelimiterAndBom() throws IOException {
        ImportResult r = read("﻿Loại;Nội dung;A;B;C;D;Đúng;Giây;Ảnh\n"
                + "Buttons;Câu, có dấu phẩy;x;y;z;t;C;15;\n");
        assertEquals(List.of(), r.errors());
        assertEquals("Câu, có dấu phẩy", r.questions().get(0).content());
        assertEquals(List.of(2), r.questions().get(0).correctIndexes());
        assertEquals(15, r.questions().get(0).timeSeconds());
    }

    @Test
    void commaInsideQuotedCellAndNewlineInCell() throws IOException {
        ImportResult r = read(HEADER
                + "Buttons,\"Dòng 1\nDòng 2, \"\"trích dẫn\"\"\",a,b,c,d,A,20,\n"
                + "Buttons,Câu sau,a,b,c,d,D,20,\n");
        assertEquals(List.of(), r.errors());
        assertEquals("Dòng 1\nDòng 2, \"trích dẫn\"", r.questions().get(0).content());
        assertEquals("Câu sau", r.questions().get(1).content());
    }

    @Test
    void badRowsAreReportedWithLineNumbers() throws IOException {
        ImportResult r = read(HEADER
                + "Buttons,Ổn,a,b,c,d,A,20,\n"                  // dòng 2: đúng
                + "Buttons,Thiếu đáp án C,a,b,,d,A,20,\n"       // dòng 3
                + "Buttons,Đúng ngoài A-D,a,b,c,d,E,20,\n"      // dòng 4
                + "Buttons,Không có đáp án đúng,a,b,c,d,,20,\n" // dòng 5
                + "Quiz,Sai loại,a,b,c,d,A,20,\n"               // dòng 6
                + "Buttons,Hai đáp án đúng,a,b,c,d,\"A,B\",20,\n" // dòng 7
                + "Buttons,Ảnh mất,a,b,c,d,A,20,khong_co.png\n"  // dòng 8
                + "Checkboxes,Cuối,a,b,c,d,\"A,C\",20,\n");
        assertEquals(2, r.questions().size());
        assertEquals(6, r.errors().size());
        assertEquals("Dòng 3: thiếu đáp án C", r.errors().get(0));
        assertTrue(r.errors().get(1).startsWith("Dòng 4: "));
        assertEquals("Dòng 5: thiếu đáp án đúng", r.errors().get(2));
        assertTrue(r.errors().get(3).startsWith("Dòng 6: sai loại"));
        assertTrue(r.errors().get(4).startsWith("Dòng 7: "));
        assertTrue(r.errors().get(5).startsWith("Dòng 8: không tìm thấy ảnh"));
    }

    @Test
    void lineNumbersCountNewlinesInsideCells() throws IOException {
        ImportResult r = read(HEADER
                + "Buttons,\"a\nb\nc\",a,b,c,d,A,20,\n"  // dòng 2-4
                + "Buttons,Lỗi,a,b,c,d,,20,\n");          // dòng 5
        assertEquals(List.of("Dòng 5: thiếu đáp án đúng"), r.errors());
    }

    @Test
    void timeOutOfRangeIsAnError() throws IOException {
        ImportResult r = read(HEADER + "Buttons,Q,a,b,c,d,A,500,\n" + "Buttons,Q,a,b,c,d,A,abc,\n");
        assertEquals(0, r.questions().size());
        assertEquals(2, r.errors().size());
    }

    @Test
    void imageRelativeToCsvFolderIsScaledAndEncoded() throws IOException {
        BufferedImage big = new BufferedImage(1600, 800, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(big, "png", dir.resolve("anh.png").toFile());
        ImportResult r = read(HEADER + "Buttons,Có ảnh,a,b,c,d,A,20,anh.png\n");
        assertEquals(List.of(), r.errors());
        String base64 = r.questions().get(0).imageBase64();
        assertNotNull(base64);
        BufferedImage back = ImageUtil.decode(base64);
        assertEquals(800, back.getWidth());
        assertEquals(400, back.getHeight());
    }

    @Test
    void templateRoundTripsThroughImporter() throws IOException {
        Path file = dir.resolve("mau.csv");
        CsvTemplate.write(file);
        byte[] bytes = Files.readAllBytes(file);
        assertEquals((byte) 0xEF, bytes[0]);
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
        ImportResult r = CsvImporter.read(file);
        assertEquals(List.of(), r.errors());
        assertEquals(List.of(QuestionType.SINGLE, QuestionType.MULTI, QuestionType.TYPE),
                r.questions().stream().map(QuestionDraft::type).toList());
    }
}

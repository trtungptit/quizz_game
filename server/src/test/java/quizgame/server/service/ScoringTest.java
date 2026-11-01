package quizgame.server.service;
import org.junit.jupiter.api.Test;
import quizgame.common.model.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ScoringTest {
    QuestionDraft q(QuestionType type,List<Integer> correct,List<String> accepted){return new QuestionDraft(type,"Câu hỏi",type==QuestionType.TYPE?List.of():List.of("1","2","3","4"),correct,accepted,5,null);}
    @Test void scoresClampAtDeadline(){assertEquals(1000,Scoring.points(true,0,5000));assertEquals(750,Scoring.points(true,2500,5000));assertEquals(500,Scoring.points(true,5500,5000));assertEquals(0,Scoring.points(false,0,5000));}
    @Test void singleAndMultiUseExactChoiceSets(){assertTrue(Scoring.correct(q(QuestionType.SINGLE,List.of(1),List.of()),new Answer(List.of(1),null)));assertFalse(Scoring.correct(q(QuestionType.SINGLE,List.of(1),List.of()),new Answer(List.of(0),null)));assertTrue(Scoring.correct(q(QuestionType.MULTI,List.of(1,3),List.of()),new Answer(List.of(3,1),null)));assertFalse(Scoring.correct(q(QuestionType.MULTI,List.of(1,3),List.of()),new Answer(List.of(1),null)));}
    @Test void textNormalizationPreservesVietnameseAccents(){var q=q(QuestionType.TYPE,List.of(),List.of("Hà Nội"));assertTrue(Scoring.correct(q,new Answer(List.of(),"  HÀ \t NỘI ")));assertFalse(Scoring.correct(q,new Answer(List.of(),"Ha Noi")));}
    @Test void textNormalizationHandlesUnicodeWhitespace(){assertTrue(Scoring.correct(q(QuestionType.TYPE,List.of(),List.of("Hà Nội")),new Answer(List.of(),"\u00a0Hà\u00a0Nội\u00a0")));}
    @Test void invalidChoicesAreRejected(){var q=q(QuestionType.SINGLE,List.of(1),List.of());assertThrows(ServiceException.class,()->Scoring.correct(q,new Answer(List.of(1,1),null)));assertThrows(ServiceException.class,()->Scoring.correct(q,new Answer(List.of(4),null)));assertThrows(ServiceException.class,()->Scoring.correct(q,new Answer(List.of(),null)));}
}

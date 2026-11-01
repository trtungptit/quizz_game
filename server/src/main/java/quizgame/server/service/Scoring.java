package quizgame.server.service;

import quizgame.common.model.Answer;
import quizgame.common.model.QuestionDraft;
import quizgame.common.model.QuestionType;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Hàm thuần chấm câu; không tin score hoặc thời gian do client cung cấp. */
public final class Scoring {
    private Scoring(){}
    public static String normalize(String text){return text.replaceAll("(?U)\\s+"," ").strip().toLowerCase(Locale.ROOT);}
    public static boolean correct(QuestionDraft q,Answer answer){
        if(answer==null)throw invalid();
        List<Integer> choices=answer.choices()==null?List.of():answer.choices();
        if(q.type()==QuestionType.TYPE){
            if(!choices.isEmpty()||answer.text()==null||answer.text().length()>200)throw invalid();
            String text=normalize(answer.text());
            return q.acceptedAnswers().stream().anyMatch(a->normalize(a).equals(text));
        }
        if(answer.text()!=null&&!answer.text().isEmpty())throw invalid();
        if(choices.isEmpty()||choices.size()>4||new HashSet<>(choices).size()!=choices.size()
                ||choices.stream().anyMatch(i->i==null||i<0||i>3)
                ||(q.type()==QuestionType.SINGLE&&choices.size()!=1))throw invalid();
        return new HashSet<>(choices).equals(new HashSet<>(q.correctIndexes()));
    }
    public static int points(boolean correct,long elapsedMs,long limitMs){
        if(limitMs<=0)throw new IllegalArgumentException("Thời gian câu hỏi phải dương");
        if(!correct)return 0;
        double fraction=(double)Math.min(Math.max(0,elapsedMs),limitMs)/limitMs;
        return (int)Math.round(500+500*(1-fraction));
    }
    private static ServiceException invalid(){return new ServiceException("BAD_REQUEST","Đáp án không đúng định dạng");}
}

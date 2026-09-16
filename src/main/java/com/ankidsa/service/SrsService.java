package com.ankidsa.service;
import com.ankidsa.dto.*;
import com.ankidsa.entity.*;
import com.ankidsa.exception.*;
import com.ankidsa.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.Objects;
@Service @RequiredArgsConstructor
public class SrsService {
    private final ProblemRepository problems;
    private final ReviewLogRepository reviews;
    private final Clock clock;

    @Transactional
    public ReviewResponse processReview(ReviewRequest req,Long userId) {
        Problem p=problems.lockOwned(req.getProblemId(),userId).orElseThrow(() -> ResourceNotFoundException.forProblem(req.getProblemId()));
        var previous=reviews.findByUserIdAndRequestId(userId,req.getRequestId());
        if(previous.isPresent()) {
            var log=previous.get();
            if(!log.getProblem().getId().equals(req.getProblemId()) || !log.getQualityRating().equals(req.getQualityRating()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"This review request was already used.");
            return response(log);
        }
        if(!Objects.equals(req.getVersion(),p.getVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This problem was already updated. Refresh your queue.");
        if(p.getNextReviewDate().isAfter(LocalDate.now(clock)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This problem is not due yet.");
        State next=calculate(p.getEasinessFactor(),p.getInterval(),p.getRepetitions(),req.getQualityRating());
        p.setEasinessFactor(next.ef()); p.setInterval(next.interval()); p.setRepetitions(next.repetitions());
        p.setNextReviewDate(LocalDate.now(clock).plusDays(next.interval())); p.setStatus(next.status());
        problems.saveAndFlush(p);
        ReviewLog log=ReviewLog.builder().problem(p).user(p.getUser()).qualityRating(req.getQualityRating())
            .easinessFactor(next.ef()).interval(next.interval()).repetitions(next.repetitions())
            .nextReviewDate(p.getNextReviewDate()).status(next.status().name()).requestId(req.getRequestId())
            .reviewedAt(LocalDateTime.now(clock)).build();
        reviews.save(log);
        return response(log);
    }
    public record State(double ef,int interval,int repetitions,ProblemStatus status) {}
    public static State calculate(double ef,int interval,int repetitions,int quality) {
        if(quality<0 || quality>5) throw new IllegalArgumentException("Quality must be 0–5.");
        double nextEf=Math.max(1.3,ef+0.1-(5-quality)*(0.08+(5-quality)*0.02));
        int reps=quality<3 ? 0 : repetitions+1;
        int days=quality<3 || repetitions==0 ? 1 : repetitions==1 ? 6 : (int)Math.min(3650,Math.round(interval*nextEf));
        ProblemStatus status=reps==0 || days<7 ? ProblemStatus.LEARNING : days>21 ? ProblemStatus.MASTERED : ProblemStatus.REVIEW;
        return new State(nextEf,days,reps,status);
    }
    private ReviewResponse response(ReviewLog log) {
        String message=log.getQualityRating()<3 ? "A fresh start tomorrow." : log.getInterval()==1 ? "See you tomorrow." : "Next review in "+log.getInterval()+" days.";
        return ReviewResponse.builder().problemId(log.getProblem().getId()).title(log.getProblem().getTitle())
            .easinessFactor(log.getEasinessFactor()).interval(log.getInterval()).repetitions(log.getRepetitions())
            .nextReviewDate(log.getNextReviewDate()).status(log.getStatus()).message(message).build();
    }
}

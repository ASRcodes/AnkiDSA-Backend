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
import java.net.URI;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class ProblemService {
    private final ProblemRepository problems;
    private final UserRepository users;
    private final ReviewLogRepository reviews;
    private final Clock clock;

    @Transactional
    public ProblemResponse logProblem(LogProblemRequest req,Long userId) {
        String url=canonicalUrl(req.getLeetcodeUrl());
        if(problems.existsByUserIdAndLeetcodeUrl(userId,url)) throw DuplicateProblemException.forUrl(url);
        User user=users.findById(userId).orElseThrow(() -> ResourceNotFoundException.forUser(userId));
        Problem p=Problem.builder().user(user).nextReviewDate(LocalDate.now(clock)).build();
        apply(p,req,url);
        return ProblemMapper.toResponse(problems.saveAndFlush(p));
    }
    @Transactional(readOnly=true)
    public List<ProblemResponse> getDailyQueue(Long userId) {
        return problems.findByUserIdAndNextReviewDateLessThanEqualOrderByNextReviewDateAscIdAsc(userId,LocalDate.now(clock)).stream().map(ProblemMapper::toResponse).toList();
    }
    @Transactional(readOnly=true)
    public List<ProblemResponse> getAllProblems(Long userId) {
        return problems.findByUserIdOrderByNextReviewDateAsc(userId).stream().map(ProblemMapper::toResponse).toList();
    }
    @Transactional(readOnly=true)
    public ProblemResponse getProblem(Long id,Long userId) {
        return ProblemMapper.toResponse(problems.findByIdAndUserId(id,userId).orElseThrow(() -> ResourceNotFoundException.forProblem(id)));
    }
    @Transactional
    public ProblemResponse update(Long id,Long userId,LogProblemRequest req) {
        Problem p=problems.lockOwned(id,userId).orElseThrow(() -> ResourceNotFoundException.forProblem(id));
        if(!Objects.equals(req.getVersion(),p.getVersion())) throw new ResponseStatusException(HttpStatus.CONFLICT,"This problem changed. Refresh before editing.");
        apply(p,req,canonicalUrl(req.getLeetcodeUrl()));
        return ProblemMapper.toResponse(problems.saveAndFlush(p));
    }
    @Transactional
    public void delete(Long id,Long userId) {
        Problem p=problems.lockOwned(id,userId).orElseThrow(() -> ResourceNotFoundException.forProblem(id));
        reviews.deleteForProblem(id);
        problems.delete(p);
    }
    private void apply(Problem p,LogProblemRequest req,String url) {
        p.setLeetcodeUrl(url); p.setTitle(req.getTitle().trim()); p.setDifficulty(Difficulty.valueOf(req.getDifficulty()));
        p.setNotes(req.getNotes()==null ? null : req.getNotes().trim());
        p.setTags(req.getTags()==null ? null : String.join(",",Arrays.stream(req.getTags().split(",")).map(String::trim).filter(s -> !s.isBlank()).distinct().toList()));
    }
    public static String canonicalUrl(String raw) {
        try {
            URI uri=URI.create(raw.trim());
            if(!"https".equalsIgnoreCase(uri.getScheme()) || !"leetcode.com".equalsIgnoreCase(uri.getHost()) || uri.getUserInfo()!=null || uri.getPort()!=-1)
                throw new IllegalArgumentException();
            var match=java.util.regex.Pattern.compile("^/problems/([a-z0-9-]+)(?:/.*)?$").matcher(uri.getPath());
            if(!match.matches()) throw new IllegalArgumentException();
            return "https://leetcode.com/problems/"+match.group(1)+"/";
        } catch(Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use a URL like https://leetcode.com/problems/two-sum/"); }
    }
}

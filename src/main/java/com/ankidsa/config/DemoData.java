package com.ankidsa.config;
import com.ankidsa.entity.*;
import com.ankidsa.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
@Configuration @Profile("demo") @RequiredArgsConstructor
public class DemoData {
 private final UserRepository users;
 private final ProblemRepository problems;
 private final ReviewLogRepository reviews;
 private final PasswordEncoder passwords;
 private final Clock clock;
 @Bean ApplicationRunner seedDemo() { return args -> seed(); }
 @Transactional public void seed() {
  if(users.existsByEmail("demo@ankidsa.local")) return;
  var user=users.save(User.builder().email("demo@ankidsa.local").name("Alex").passwordHash(passwords.encode("DemoRecall!2026")).build());
  seedProblems(user);
 }
 @Transactional public void reset(Long userId) {
  var user=users.findById(userId).orElseThrow();
  if(!user.getEmail().equals("demo@ankidsa.local")) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Only the sample account can be reset.");
  for(var p:problems.findByUserIdOrderByNextReviewDateAsc(userId)) { reviews.deleteForProblem(p.getId()); problems.delete(p); }
  problems.flush();
  seedProblems(user);
 }
 private void seedProblems(User user) {
  String[][] entries={
   {"valid-parentheses","Valid Parentheses","EASY","stack,string","Use a stack of opening brackets. Each closing bracket must match the most recent opening bracket. An empty stack at the end means every pair was closed.\n\nO(n) time, O(n) space. Watch for a closing bracket with an empty stack."},
   {"longest-substring-without-repeating-characters","Longest Substring Without Repeating Characters","MEDIUM","sliding-window,hash-map","Keep a window with unique characters. Store each character's last index; move the left boundary forward when a duplicate enters. Never move it backward.\n\nThe answer is the largest right − left + 1. O(n) time."},
   {"binary-search","Binary Search","EASY","binary-search,array","Maintain a closed interval [left, right]. Compare the middle element, then discard the half that cannot contain the target.\n\nUse left + (right − left) / 2. Stop when left > right."},
   {"merge-intervals","Merge Intervals","MEDIUM","sorting,intervals","Sort by start time. If the next interval starts before the current one ends, extend the end using max. Otherwise, start a new interval.\n\nSorting dominates: O(n log n)."},
   {"trapping-rain-water","Trapping Rain Water","HARD","two-pointers,array","Water above a bar depends on the smaller of the tallest walls on either side. Move the pointer on the lower side and keep a running maximum.\n\nO(n) time with O(1) extra space."},
   {"climbing-stairs","Climbing Stairs","EASY","dynamic-programming","To reach step n, the previous move came from n−1 or n−2. Keep only the last two counts.\n\nBase cases: one way for one step, two ways for two."},
   {"number-of-islands","Number of Islands","MEDIUM","graph,depth-first-search","Scan the grid. Each unvisited land cell begins a new island. Flood-fill its connected land so it is counted exactly once.\n\nRemember the grid boundaries. O(rows × columns)."},
   {"reverse-linked-list","Reverse Linked List","EASY","linked-list","Carry previous, current, and next pointers. Save next before redirecting current.next. Advance both pointers until current is null. Return previous."}
  };
  LocalDate today=LocalDate.now(clock);
  for(int i=0;i<entries.length;i++) {
   var e=entries[i];
   Problem p=problems.save(Problem.builder().user(user).leetcodeUrl("https://leetcode.com/problems/"+e[0]+"/")
    .title(e[1]).difficulty(Difficulty.valueOf(e[2])).tags(e[3]).notes(e[4])
    .nextReviewDate(today.plusDays(i<4 ? (i==0 ? -1:0):i+1))
    .interval(i<4 ? 6:38).repetitions(i<4 ? 2:4)
    .status(i<4 ? ProblemStatus.LEARNING:ProblemStatus.MASTERED).build());
   // Explicitly synthetic review history, confined to the local demo account.
   for(int day=1;day<=38;day++) {
    if((day+i)%4==0 || day<=4) reviews.save(ReviewLog.builder().problem(p).user(user)
      .qualityRating((day+i)%9==0 ? 1:4).easinessFactor(2.5).interval(6).repetitions(2)
      .reviewedAt(today.minusDays(day).atTime(9,i*5)).build());
   }
  }
 }
}

package com.ankidsa.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LogProblemRequest {
    @NotBlank(message="A LeetCode problem URL is required.") @Size(max=2048) private String leetcodeUrl;
    @NotBlank(message="Give this problem a title.") @Size(max=500) private String title;
    @NotNull @Pattern(regexp="EASY|MEDIUM|HARD",message="Choose Easy, Medium or Hard.") private String difficulty;
    @Size(max=2000) private String tags;
    @Size(max=20000) private String notes;
    private Long version;
}

package com.ankidsa.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ReviewRequest {
    @NotNull @Positive private Long problemId;
    @NotNull @Min(0) @Max(5) private Integer qualityRating;
    @NotNull @Min(0) private Long version;
    @NotBlank @Pattern(regexp="[a-fA-F0-9-]{36}") private String requestId;
}

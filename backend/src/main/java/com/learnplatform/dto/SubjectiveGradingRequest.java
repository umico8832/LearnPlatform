package com.learnplatform.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class SubjectiveGradingRequest {
    @NotEmpty
    @Valid
    private List<PointScore> points;
    @Size(max = 1000)
    private String reviewComment;

    public List<PointScore> getPoints() { return points; }
    public void setPoints(List<PointScore> points) { this.points = points; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }

    public static class PointScore {
        @NotNull
        private String pointKey;
        @NotNull
        private Integer awardedScore;
        @Size(max = 500)
        private String comment;

        public String getPointKey() { return pointKey; }
        public void setPointKey(String pointKey) { this.pointKey = pointKey; }
        public Integer getAwardedScore() { return awardedScore; }
        public void setAwardedScore(Integer awardedScore) { this.awardedScore = awardedScore; }
        public String getComment() { return comment; }
        public void setComment(String comment) { this.comment = comment; }
    }
}

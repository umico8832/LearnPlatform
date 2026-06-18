package com.learnplatform.dto;

import java.util.List;
import java.util.Map;

/**
 * 学习诊断 VO
 *
 * 包含知识点薄弱诊断、错因分析、学习习惯分析和每日推荐题目。
 */
public class LearningDiagnosisVO {

    /** 总刷题数 */
    private int totalPractice;
    /** 总正确率（百分制） */
    private double overallCorrectRate;
    /** 活跃天数（最近 30 天有练习的天数） */
    private int activeDaysLast30;
    /** 连续刷题天数 */
    private int streakDays;

    /** 知识点薄弱诊断（按优先级排序，取 Top N） */
    private List<WeakPoint> weakPoints;
    /** 各课程掌握概况 */
    private List<CourseMastery> courseMasteries;
    /** 错因分析汇总 */
    private ErrorPatternSummary errorPatterns;
    /** 学习习惯分析 */
    private LearningHabit learningHabit;
    /** 每日推荐题目 */
    private List<RecommendedQuestion> dailyRecommendations;
    /** 每日学习建议文本（AI 生成或规则生成） */
    private String dailyAdvice;

    public int getTotalPractice() { return totalPractice; }
    public void setTotalPractice(int totalPractice) { this.totalPractice = totalPractice; }
    public double getOverallCorrectRate() { return overallCorrectRate; }
    public void setOverallCorrectRate(double overallCorrectRate) { this.overallCorrectRate = overallCorrectRate; }
    public int getActiveDaysLast30() { return activeDaysLast30; }
    public void setActiveDaysLast30(int activeDaysLast30) { this.activeDaysLast30 = activeDaysLast30; }
    public int getStreakDays() { return streakDays; }
    public void setStreakDays(int streakDays) { this.streakDays = streakDays; }
    public List<WeakPoint> getWeakPoints() { return weakPoints; }
    public void setWeakPoints(List<WeakPoint> weakPoints) { this.weakPoints = weakPoints; }
    public List<CourseMastery> getCourseMasteries() { return courseMasteries; }
    public void setCourseMasteries(List<CourseMastery> courseMasteries) { this.courseMasteries = courseMasteries; }
    public ErrorPatternSummary getErrorPatterns() { return errorPatterns; }
    public void setErrorPatterns(ErrorPatternSummary errorPatterns) { this.errorPatterns = errorPatterns; }
    public LearningHabit getLearningHabit() { return learningHabit; }
    public void setLearningHabit(LearningHabit learningHabit) { this.learningHabit = learningHabit; }
    public List<RecommendedQuestion> getDailyRecommendations() { return dailyRecommendations; }
    public void setDailyRecommendations(List<RecommendedQuestion> dailyRecommendations) { this.dailyRecommendations = dailyRecommendations; }
    public String getDailyAdvice() { return dailyAdvice; }
    public void setDailyAdvice(String dailyAdvice) { this.dailyAdvice = dailyAdvice; }

    /**
     * 薄弱知识点
     */
    public static class WeakPoint {
        private Long knowledgePointId;
        private String knowledgePointName;
        private Long courseId;
        private String courseName;
        /** 正确率（百分制，-1 表示未练习） */
        private double correctRate;
        /** 总练习题数 */
        private int totalAttempts;
        /** 错题数 */
        private int wrongCount;
        /** 掌握状态：WEAK / NEEDS_REVIEW / NOT_STARTED */
        private String masteryStatus;
        /** 优先级得分（越高越需要关注） */
        private double priorityScore;
        /** 诊断建议 */
        private String diagnosis;

        public Long getKnowledgePointId() { return knowledgePointId; }
        public void setKnowledgePointId(Long knowledgePointId) { this.knowledgePointId = knowledgePointId; }
        public String getKnowledgePointName() { return knowledgePointName; }
        public void setKnowledgePointName(String knowledgePointName) { this.knowledgePointName = knowledgePointName; }
        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public double getCorrectRate() { return correctRate; }
        public void setCorrectRate(double correctRate) { this.correctRate = correctRate; }
        public int getTotalAttempts() { return totalAttempts; }
        public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }
        public int getWrongCount() { return wrongCount; }
        public void setWrongCount(int wrongCount) { this.wrongCount = wrongCount; }
        public String getMasteryStatus() { return masteryStatus; }
        public void setMasteryStatus(String masteryStatus) { this.masteryStatus = masteryStatus; }
        public double getPriorityScore() { return priorityScore; }
        public void setPriorityScore(double priorityScore) { this.priorityScore = priorityScore; }
        public String getDiagnosis() { return diagnosis; }
        public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    }

    /**
     * 课程掌握概况
     */
    public static class CourseMastery {
        private Long courseId;
        private String courseName;
        private double correctRate;
        private int totalAttempts;
        private int wrongCount;
        private int knowledgePointCount;
        private int weakPointCount;

        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public double getCorrectRate() { return correctRate; }
        public void setCorrectRate(double correctRate) { this.correctRate = correctRate; }
        public int getTotalAttempts() { return totalAttempts; }
        public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }
        public int getWrongCount() { return wrongCount; }
        public void setWrongCount(int wrongCount) { this.wrongCount = wrongCount; }
        public int getKnowledgePointCount() { return knowledgePointCount; }
        public void setKnowledgePointCount(int knowledgePointCount) { this.knowledgePointCount = knowledgePointCount; }
        public int getWeakPointCount() { return weakPointCount; }
        public void setWeakPointCount(int weakPointCount) { this.weakPointCount = weakPointCount; }
    }

    /**
     * 错因分析汇总
     */
    public static class ErrorPatternSummary {
        /** 高频错题课程 Top N */
        private List<CourseErrorCount> topErrorCourses;
        /** 错题掌握程度分布 */
        private Map<String, Integer> masteryDistribution;
        /** 反复出错的题目数（wrongCount >= 3） */
        private int repeatedErrorCount;
        /** 最近 7 天新增错题数 */
        private int recentNewWrongCount;
        /** 错题题型分布（题型中文名 -> 错题数） */
        private Map<String, Integer> questionTypeDistribution;
        /** 错题难度分布（难度星级 -> 错题数） */
        private Map<Integer, Integer> difficultyDistribution;
        /** 知识点错因排名（按错题数排序 Top N） */
        private List<KnowledgePointErrorRank> knowledgePointErrors;
        /** 反复错题详情（wrongCount >= 2） */
        private List<RepeatedErrorItem> repeatedErrors;
        /** 每周错题趋势（最近 4 周，每周错题数） */
        private List<Map<String, Object>> weeklyErrorTrend;

        public List<CourseErrorCount> getTopErrorCourses() { return topErrorCourses; }
        public void setTopErrorCourses(List<CourseErrorCount> topErrorCourses) { this.topErrorCourses = topErrorCourses; }
        public Map<String, Integer> getMasteryDistribution() { return masteryDistribution; }
        public void setMasteryDistribution(Map<String, Integer> masteryDistribution) { this.masteryDistribution = masteryDistribution; }
        public int getRepeatedErrorCount() { return repeatedErrorCount; }
        public void setRepeatedErrorCount(int repeatedErrorCount) { this.repeatedErrorCount = repeatedErrorCount; }
        public int getRecentNewWrongCount() { return recentNewWrongCount; }
        public void setRecentNewWrongCount(int recentNewWrongCount) { this.recentNewWrongCount = recentNewWrongCount; }
        public Map<String, Integer> getQuestionTypeDistribution() { return questionTypeDistribution; }
        public void setQuestionTypeDistribution(Map<String, Integer> questionTypeDistribution) { this.questionTypeDistribution = questionTypeDistribution; }
        public Map<Integer, Integer> getDifficultyDistribution() { return difficultyDistribution; }
        public void setDifficultyDistribution(Map<Integer, Integer> difficultyDistribution) { this.difficultyDistribution = difficultyDistribution; }
        public List<KnowledgePointErrorRank> getKnowledgePointErrors() { return knowledgePointErrors; }
        public void setKnowledgePointErrors(List<KnowledgePointErrorRank> knowledgePointErrors) { this.knowledgePointErrors = knowledgePointErrors; }
        public List<RepeatedErrorItem> getRepeatedErrors() { return repeatedErrors; }
        public void setRepeatedErrors(List<RepeatedErrorItem> repeatedErrors) { this.repeatedErrors = repeatedErrors; }
        public List<Map<String, Object>> getWeeklyErrorTrend() { return weeklyErrorTrend; }
        public void setWeeklyErrorTrend(List<Map<String, Object>> weeklyErrorTrend) { this.weeklyErrorTrend = weeklyErrorTrend; }
    }

    /**
     * 课程错题计数
     */
    public static class CourseErrorCount {
        private Long courseId;
        private String courseName;
        private int wrongCount;

        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public int getWrongCount() { return wrongCount; }
        public void setWrongCount(int wrongCount) { this.wrongCount = wrongCount; }
    }

    /**
     * 知识点错因排名
     */
    public static class KnowledgePointErrorRank {
        private Long knowledgePointId;
        private String knowledgePointName;
        private Long courseId;
        private String courseName;
        /** 错题数 */
        private int wrongCount;
        /** 练习题数 */
        private int totalAttempts;
        /** 正确率 */
        private double correctRate;

        public Long getKnowledgePointId() { return knowledgePointId; }
        public void setKnowledgePointId(Long knowledgePointId) { this.knowledgePointId = knowledgePointId; }
        public String getKnowledgePointName() { return knowledgePointName; }
        public void setKnowledgePointName(String knowledgePointName) { this.knowledgePointName = knowledgePointName; }
        public Long getCourseId() { return courseId; }
        public void setCourseId(Long courseId) { this.courseId = courseId; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public int getWrongCount() { return wrongCount; }
        public void setWrongCount(int wrongCount) { this.wrongCount = wrongCount; }
        public int getTotalAttempts() { return totalAttempts; }
        public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }
        public double getCorrectRate() { return correctRate; }
        public void setCorrectRate(double correctRate) { this.correctRate = correctRate; }
    }

    /**
     * 反复错题详情
     */
    public static class RepeatedErrorItem {
        private Long questionId;
        private String questionContent;
        private String questionType;
        private Integer difficulty;
        /** 错误次数 */
        private int wrongCount;
        /** 掌握程度 */
        private Integer masteryLevel;
        /** 最后一次答错的答案 */
        private String lastWrongAnswer;
        /** 关联的知识点名称 */
        private String knowledgePointName;
        /** 关联的课程名称 */
        private String courseName;

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public String getQuestionContent() { return questionContent; }
        public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }
        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
        public Integer getDifficulty() { return difficulty; }
        public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
        public int getWrongCount() { return wrongCount; }
        public void setWrongCount(int wrongCount) { this.wrongCount = wrongCount; }
        public Integer getMasteryLevel() { return masteryLevel; }
        public void setMasteryLevel(Integer masteryLevel) { this.masteryLevel = masteryLevel; }
        public String getLastWrongAnswer() { return lastWrongAnswer; }
        public void setLastWrongAnswer(String lastWrongAnswer) { this.lastWrongAnswer = lastWrongAnswer; }
        public String getKnowledgePointName() { return knowledgePointName; }
        public void setKnowledgePointName(String knowledgePointName) { this.knowledgePointName = knowledgePointName; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
    }

    /**
     * 学习习惯分析
     */
    public static class LearningHabit {
        /** 最近 30 天平均每天刷题数 */
        private double avgDailyPractice;
        /** 最常练习的题型 */
        private String preferredQuestionType;
        /** 最常练习的课程 */
        private String preferredCourse;
        /** 最近 7 天每天刷题数列表 */
        private List<Map<String, Object>> weeklyTrend;
        /** 学习频次评价：ACTIVE / MODERATE / INACTIVE */
        private String frequencyLevel;
        /** 学习频次描述 */
        private String frequencyDescription;

        public double getAvgDailyPractice() { return avgDailyPractice; }
        public void setAvgDailyPractice(double avgDailyPractice) { this.avgDailyPractice = avgDailyPractice; }
        public String getPreferredQuestionType() { return preferredQuestionType; }
        public void setPreferredQuestionType(String preferredQuestionType) { this.preferredQuestionType = preferredQuestionType; }
        public String getPreferredCourse() { return preferredCourse; }
        public void setPreferredCourse(String preferredCourse) { this.preferredCourse = preferredCourse; }
        public List<Map<String, Object>> getWeeklyTrend() { return weeklyTrend; }
        public void setWeeklyTrend(List<Map<String, Object>> weeklyTrend) { this.weeklyTrend = weeklyTrend; }
        public String getFrequencyLevel() { return frequencyLevel; }
        public void setFrequencyLevel(String frequencyLevel) { this.frequencyLevel = frequencyLevel; }
        public String getFrequencyDescription() { return frequencyDescription; }
        public void setFrequencyDescription(String frequencyDescription) { this.frequencyDescription = frequencyDescription; }
    }

    /**
     * 单题错因分析
     */
    public static class QuestionErrorAnalysis {
        private Long questionId;
        private String questionContent;
        private String questionType;
        private Integer difficulty;
        /** 课程名称 */
        private String courseName;
        /** 知识点名称 */
        private String knowledgePointName;
        /** 总作答次数 */
        private int totalAttempts;
        /** 答对次数 */
        private int correctCount;
        /** 答错次数 */
        private int wrongCount;
        /** 正确率 */
        private double correctRate;
        /** 当前掌握程度 */
        private Integer currentMasteryLevel;
        /** 掌握趋势：IMPROVING / STAGNANT / DECLINING */
        private String masteryTrend;
        /** 趋势描述 */
        private String trendDescription;
        /** 作答历史（按时间正序） */
        private List<AttemptHistory> attempts;
        /** 错误模式描述 */
        private String errorPattern;

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public String getQuestionContent() { return questionContent; }
        public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }
        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
        public Integer getDifficulty() { return difficulty; }
        public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public String getKnowledgePointName() { return knowledgePointName; }
        public void setKnowledgePointName(String knowledgePointName) { this.knowledgePointName = knowledgePointName; }
        public int getTotalAttempts() { return totalAttempts; }
        public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }
        public int getCorrectCount() { return correctCount; }
        public void setCorrectCount(int correctCount) { this.correctCount = correctCount; }
        public int getWrongCount() { return wrongCount; }
        public void setWrongCount(int wrongCount) { this.wrongCount = wrongCount; }
        public double getCorrectRate() { return correctRate; }
        public void setCorrectRate(double correctRate) { this.correctRate = correctRate; }
        public Integer getCurrentMasteryLevel() { return currentMasteryLevel; }
        public void setCurrentMasteryLevel(Integer currentMasteryLevel) { this.currentMasteryLevel = currentMasteryLevel; }
        public String getMasteryTrend() { return masteryTrend; }
        public void setMasteryTrend(String masteryTrend) { this.masteryTrend = masteryTrend; }
        public String getTrendDescription() { return trendDescription; }
        public void setTrendDescription(String trendDescription) { this.trendDescription = trendDescription; }
        public List<AttemptHistory> getAttempts() { return attempts; }
        public void setAttempts(List<AttemptHistory> attempts) { this.attempts = attempts; }
        public String getErrorPattern() { return errorPattern; }
        public void setErrorPattern(String errorPattern) { this.errorPattern = errorPattern; }
    }

    /**
     * 单次作答记录（用于单题错因分析）
     */
    public static class AttemptHistory {
        private Long recordId;
        private String userAnswer;
        private Integer isCorrect;
        /** 作答用时（秒） */
        private Integer answerTime;
        private String createTime;

        public Long getRecordId() { return recordId; }
        public void setRecordId(Long recordId) { this.recordId = recordId; }
        public String getUserAnswer() { return userAnswer; }
        public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
        public Integer getIsCorrect() { return isCorrect; }
        public void setIsCorrect(Integer isCorrect) { this.isCorrect = isCorrect; }
        public Integer getAnswerTime() { return answerTime; }
        public void setAnswerTime(Integer answerTime) { this.answerTime = answerTime; }
        public String getCreateTime() { return createTime; }
        public void setCreateTime(String createTime) { this.createTime = createTime; }
    }

    /**
     * 推荐题目
     */
    public static class RecommendedQuestion {
        private Long questionId;
        /** 推荐原因：SPACED_REVIEW / WEAK_POINT_REINFORCE / ERROR_PRONE */
        private String reason;
        /** 推荐原因描述 */
        private String reasonDescription;
        private String questionContent;
        private String questionType;
        private String courseName;
        private Integer difficulty;
        /** 相关知识点名称 */
        private String knowledgePointName;
        /** 上次答错的答案（如果是错题复习） */
        private String lastWrongAnswer;

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getReasonDescription() { return reasonDescription; }
        public void setReasonDescription(String reasonDescription) { this.reasonDescription = reasonDescription; }
        public String getQuestionContent() { return questionContent; }
        public void setQuestionContent(String questionContent) { this.questionContent = questionContent; }
        public String getQuestionType() { return questionType; }
        public void setQuestionType(String questionType) { this.questionType = questionType; }
        public String getCourseName() { return courseName; }
        public void setCourseName(String courseName) { this.courseName = courseName; }
        public Integer getDifficulty() { return difficulty; }
        public void setDifficulty(Integer difficulty) { this.difficulty = difficulty; }
        public String getKnowledgePointName() { return knowledgePointName; }
        public void setKnowledgePointName(String knowledgePointName) { this.knowledgePointName = knowledgePointName; }
        public String getLastWrongAnswer() { return lastWrongAnswer; }
        public void setLastWrongAnswer(String lastWrongAnswer) { this.lastWrongAnswer = lastWrongAnswer; }
    }
}
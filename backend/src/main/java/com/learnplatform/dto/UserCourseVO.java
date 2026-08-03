package com.learnplatform.dto;

import com.learnplatform.entity.Course;
import com.learnplatform.entity.UserCourse;

import java.time.LocalDateTime;

public class UserCourseVO {

    private Long id;
    private Long courseId;
    private String courseName;
    private String description;
    private String coverImage;
    private String contentKey;
    private String contentSource;
    private LocalDateTime addedAt;

    public static UserCourseVO from(UserCourse userCourse, Course course) {
        UserCourseVO vo = new UserCourseVO();
        vo.setId(userCourse.getId());
        vo.setCourseId(course.getId());
        vo.setCourseName(course.getName());
        vo.setDescription(course.getDescription());
        vo.setCoverImage(course.getCoverImage());
        vo.setContentKey(course.getContentKey());
        vo.setContentSource(course.getContentSource());
        vo.setAddedAt(userCourse.getCreateTime());
        return vo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public String getContentKey() { return contentKey; }
    public void setContentKey(String contentKey) { this.contentKey = contentKey; }
    public String getContentSource() { return contentSource; }
    public void setContentSource(String contentSource) { this.contentSource = contentSource; }
    public LocalDateTime getAddedAt() { return addedAt; }
    public void setAddedAt(LocalDateTime addedAt) { this.addedAt = addedAt; }
}

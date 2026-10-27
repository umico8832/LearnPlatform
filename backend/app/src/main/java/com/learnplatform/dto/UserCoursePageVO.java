package com.learnplatform.dto;

/** 一页个人课程及其当前学习概况。 */
public class UserCoursePageVO extends UserCourseVO {
    private CourseOverviewVO overview;

    public CourseOverviewVO getOverview() {
        return overview;
    }

    public void setOverview(CourseOverviewVO overview) {
        this.overview = overview;
    }

    public static UserCoursePageVO from(UserCourseVO source, CourseOverviewVO overview) {
        UserCoursePageVO value = new UserCoursePageVO();
        value.setId(source.getId());
        value.setCourseId(source.getCourseId());
        value.setCourseName(source.getCourseName());
        value.setDescription(source.getDescription());
        value.setCoverImage(source.getCoverImage());
        value.setContentKey(source.getContentKey());
        value.setContentSource(source.getContentSource());
        value.setAddedAt(source.getAddedAt());
        value.setOverview(overview);
        return value;
    }
}

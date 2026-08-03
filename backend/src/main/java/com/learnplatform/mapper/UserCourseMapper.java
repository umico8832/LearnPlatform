package com.learnplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learnplatform.entity.UserCourse;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserCourseMapper extends BaseMapper<UserCourse> {
}

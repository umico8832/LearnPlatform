package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.KnowledgePointVO;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KnowledgePointService {

    private final KnowledgePointMapper knowledgePointMapper;
    private final CourseMapper courseMapper;

    public KnowledgePointService(KnowledgePointMapper knowledgePointMapper, CourseMapper courseMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
        this.courseMapper = courseMapper;
    }

    /**
     * 获取课程下的知识点树形结构
     */
    public List<KnowledgePointVO> getKnowledgeTree(Long courseId) {
        LambdaQueryWrapper<KnowledgePoint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgePoint::getCourseId, courseId)
               .orderByAsc(KnowledgePoint::getSortOrder);
        List<KnowledgePoint> all = knowledgePointMapper.selectList(wrapper);
        List<KnowledgePointVO> voList = all.stream()
                .map(KnowledgePointVO::fromEntity)
                .collect(Collectors.toList());
        return buildTree(voList);
    }

    private List<KnowledgePointVO> buildTree(List<KnowledgePointVO> all) {
        Map<Long, List<KnowledgePointVO>> grouped = all.stream()
                .filter(vo -> vo.getParentId() != null && vo.getParentId() != 0)
                .collect(Collectors.groupingBy(KnowledgePointVO::getParentId));
        all.forEach(vo -> vo.setChildren(grouped.getOrDefault(vo.getId(), new ArrayList<>())));
        return all.stream()
                .filter(vo -> vo.getParentId() == null || vo.getParentId() == 0)
                .collect(Collectors.toList());
    }

    public KnowledgePointVO createKnowledgePoint(Long courseId, Long parentId, String name,
                                                String description, Integer sortOrder) {
        requireCourse(courseId);
        validateParent(courseId, parentId, null);
        KnowledgePoint kp = new KnowledgePoint();
        kp.setCourseId(courseId);
        kp.setParentId(parentId != null ? parentId : 0L);
        kp.setName(name);
        kp.setDescription(description);
        kp.setContentSource("PLATFORM");
        kp.setSortOrder(sortOrder != null ? sortOrder : 0);
        kp.setDeleted(0);
        knowledgePointMapper.insert(kp);
        return KnowledgePointVO.fromEntity(kp);
    }

    public KnowledgePointVO updateKnowledgePoint(Long id, String name, String description, Integer sortOrder) {
        KnowledgePoint kp = knowledgePointMapper.selectById(id);
        if (kp == null) { throw new BusinessException(ResultCode.NOT_FOUND, "知识点不存在"); }
        requireCourse(kp.getCourseId());
        validateParent(kp.getCourseId(), kp.getParentId(), kp.getId());
        if (name != null) { kp.setName(name); }
        if (description != null) { kp.setDescription(description); }
        if (sortOrder != null) { kp.setSortOrder(sortOrder); }
        knowledgePointMapper.updateById(kp);
        return KnowledgePointVO.fromEntity(kp);
    }

    @Transactional
    public void deleteKnowledgePoint(Long id) {
        KnowledgePoint kp = knowledgePointMapper.selectById(id);
        if (kp == null) { throw new BusinessException(ResultCode.NOT_FOUND, "知识点不存在"); }
        if (knowledgePointMapper.countReferences(id) > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "知识点仍被下游内容引用，无法删除");
        }
        knowledgePointMapper.deleteById(id);
    }

    private void requireCourse(Long courseId) {
        if (courseId == null || courseMapper.selectById(courseId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }
    }

    private void validateParent(Long courseId, Long parentId, Long knowledgePointId) {
        if (parentId == null || parentId == 0L) { return; }
        if (parentId.equals(knowledgePointId)) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "知识点不能作为自身父节点");
        }
        KnowledgePoint parent = knowledgePointMapper.selectById(parentId);
        if (parent == null || !courseId.equals(parent.getCourseId())) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "父知识点不存在或不属于当前课程");
        }
        Long cursor = parent.getParentId();
        Set<Long> visited = new HashSet<>();
        while (cursor != null && cursor != 0L) {
            if (!visited.add(cursor) || cursor.equals(knowledgePointId)) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "知识点父子关系不能形成循环");
            }
            KnowledgePoint ancestor = knowledgePointMapper.selectById(cursor);
            if (ancestor == null || !courseId.equals(ancestor.getCourseId())) {
                throw new BusinessException(ResultCode.VALIDATION_ERROR, "父知识点层级无效");
            }
            cursor = ancestor.getParentId();
        }
    }
}

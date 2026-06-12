package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.KnowledgePointVO;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.mapper.KnowledgePointMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KnowledgePointService {

    private final KnowledgePointMapper knowledgePointMapper;

    public KnowledgePointService(KnowledgePointMapper knowledgePointMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
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

    public KnowledgePointVO createKnowledgePoint(Long courseId, Long parentId, String name, String description, Integer sortOrder) {
        KnowledgePoint kp = new KnowledgePoint();
        kp.setCourseId(courseId);
        kp.setParentId(parentId != null ? parentId : 0L);
        kp.setName(name);
        kp.setDescription(description);
        kp.setSortOrder(sortOrder != null ? sortOrder : 0);
        kp.setDeleted(0);
        knowledgePointMapper.insert(kp);
        return KnowledgePointVO.fromEntity(kp);
    }

    public KnowledgePointVO updateKnowledgePoint(Long id, String name, String description, Integer sortOrder) {
        KnowledgePoint kp = knowledgePointMapper.selectById(id);
        if (kp == null) throw new BusinessException(ResultCode.NOT_FOUND, "知识点不存在");
        if (name != null) kp.setName(name);
        if (description != null) kp.setDescription(description);
        if (sortOrder != null) kp.setSortOrder(sortOrder);
        knowledgePointMapper.updateById(kp);
        return KnowledgePointVO.fromEntity(kp);
    }

    public void deleteKnowledgePoint(Long id) {
        KnowledgePoint kp = knowledgePointMapper.selectById(id);
        if (kp == null) throw new BusinessException(ResultCode.NOT_FOUND, "知识点不存在");
        knowledgePointMapper.deleteById(id);
    }
}
package com.testcase.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testcase.backend.dto.PagedResult;
import com.testcase.backend.entity.OperationLogEntity;
import com.testcase.backend.mapper.OperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Service
public class OperationLogService {
    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);
    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public OperationLogService(OperationLogMapper operationLogMapper, ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    public void log(String objectType, Long objectId, String action, Object before, Object after, String remark) {
        try {
            OperationLogEntity entity = new OperationLogEntity();
            entity.setObjectType(objectType);
            entity.setObjectId(objectId);
            entity.setAction(action);
            entity.setBeforeJson(before == null ? null : objectMapper.writeValueAsString(before));
            entity.setAfterJson(after == null ? null : objectMapper.writeValueAsString(after));
            Operator op = resolveOperator();
            entity.setOperatorId(op.id());
            entity.setOperatorName(op.name());
            entity.setRemark(truncate(remark, 255));
            operationLogMapper.insert(entity);
        } catch (Exception e) {
            OperationLogService.log.error("failed to write operation log, objectType={}, objectId={}", objectType, objectId, e);
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        if (value.length() <= maxLen) return value;
        if (maxLen <= 3) return value.substring(0, maxLen);
        return value.substring(0, maxLen - 3) + "...";
    }

    private Operator resolveOperator() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                Object userId = sra.getRequest().getAttribute("loginUserId");
                Object username = sra.getRequest().getAttribute("loginUsername");
                if (userId instanceof Long id) {
                    String name = username == null ? "unknown" : String.valueOf(username);
                    return new Operator(id, name);
                }
                if (userId instanceof Integer id) {
                    String name = username == null ? "unknown" : String.valueOf(username);
                    return new Operator(id.longValue(), name);
                }
            }
        } catch (Exception ignored) {
        }
        return new Operator(1L, "admin");
    }

    private record Operator(Long id, String name) {
    }

    public PagedResult<OperationLogEntity> search(
            String objectType, String action, int pageNo, int pageSize
    ) {
        var wrapper = new LambdaQueryWrapper<OperationLogEntity>()
                .orderByDesc(OperationLogEntity::getId);

        if (objectType != null && !objectType.isBlank()) {
            wrapper.eq(OperationLogEntity::getObjectType, objectType.trim());
        }
        if (action != null && !action.isBlank()) {
            wrapper.eq(OperationLogEntity::getAction, action.trim());
        }

        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);
        Page<OperationLogEntity> page = operationLogMapper.selectPage(new Page<>(safePageNo, safePageSize), wrapper);
        return new PagedResult<>(page.getRecords(), safePageNo, safePageSize, page.getTotal());
    }
}

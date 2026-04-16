package com.testcase.backend.dto;

import java.util.List;

/**
 * 通用分页包装：与 MyBatis-Plus {@code Page} 查询结果对应，供 API 统一返回。
 *
 * @param records 当前页数据
 * @param pageNo 页码（从 1 起）
 * @param pageSize 每页条数
 * @param total 总记录数
 * @param <T> 行类型
 */
public record PagedResult<T>(
        List<T> records,
        int pageNo,
        int pageSize,
        long total
) {
}

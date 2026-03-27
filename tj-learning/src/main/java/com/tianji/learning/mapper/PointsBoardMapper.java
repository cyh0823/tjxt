package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.PointsBoard;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 学霸天梯榜 Mapper 接口
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-24
 */
public interface PointsBoardMapper extends BaseMapper<PointsBoard> {

    void createTable(@Param("tableName") String tableName);
}

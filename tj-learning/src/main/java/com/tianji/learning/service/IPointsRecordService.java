package com.tianji.learning.service;

import com.tianji.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-24
 */
public interface IPointsRecordService extends IService<PointsRecord> {

    void addPointsRecord(Long userId, int points, PointsRecordType pointsRecordType);

    List<PointsStatisticsVO> queryMyPointsToday();

    List<PointsRecord> getPointsRecords(Long userId, LocalDateTime begin, LocalDateTime end);
}

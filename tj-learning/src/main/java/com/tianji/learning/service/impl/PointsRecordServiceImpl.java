package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.service.IPointsRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 服务实现类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-24
 */
@Service
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord> implements IPointsRecordService {

    @Override
    public void addPointsRecord(Long userId, int points, PointsRecordType pointsRecordType) {
        // 1.查询当前类型积分有没有上限
        int maxPoints = pointsRecordType.getMaxPoints();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime begin = DateUtils.getDayStartTime(now);
        LocalDateTime end = DateUtils.getDayEndTime(now);
        if (maxPoints > 0){
            // 2.有上限，判断当前用户积分是否超过上限
            int currentPoints = queryUserPointsByTypeAndDate(userId,pointsRecordType,begin,end);
            if (currentPoints >= maxPoints){
                // 有上限，且超过上限，不添加
                return;
            }
            // 3.未超过上限，添加积分记录
            if (points + currentPoints > maxPoints) {
                points = maxPoints - currentPoints;
            }
        }
        // 4.无上限，添加积分记录（添加的是记录，不用计算总分）
        PointsRecord p = new PointsRecord();
        p.setPoints(points);
        p.setType(pointsRecordType);
        p.setUserId(userId);
        save(p);
    }

    @Override
    public List<PointsStatisticsVO> queryMyPointsToday() {
        Long userId = UserContext.getUser();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime begin = DateUtils.getDayStartTime(now);
        LocalDateTime end = DateUtils.getDayEndTime(now);
        QueryWrapper<PointsRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(PointsRecord::getUserId,userId)
                .between(PointsRecord::getCreateTime,begin,end);
        List<PointsRecord> pointsRecords = baseMapper.queryUserPointsByDate(queryWrapper);
        if (CollUtils.isEmpty(pointsRecords)) {
            return CollUtils.emptyList();
        }
        List<PointsStatisticsVO> voList = new ArrayList<>(pointsRecords.size());
        for (PointsRecord pointsRecord : pointsRecords) {
            PointsStatisticsVO vo = new PointsStatisticsVO();
            vo.setType(pointsRecord.getType().getDesc());
            vo.setPoints(pointsRecord.getPoints());
            vo.setMaxPoints(pointsRecord.getType().getMaxPoints());
            voList.add(vo);
        }
        return voList;
    }

    private int queryUserPointsByTypeAndDate(Long userId, PointsRecordType pointsRecordType, LocalDateTime begin, LocalDateTime end) {
        QueryWrapper<PointsRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda()
                .eq(PointsRecord::getUserId,userId)
                .eq(pointsRecordType!=null,PointsRecord::getType,pointsRecordType)
                .between(begin!=null&&end!=null,PointsRecord::getCreateTime,begin,end);
        Integer points = baseMapper.queryUserPointsByTypeAndDate(queryWrapper);
        return points == null ? 0 : points;
    }
}

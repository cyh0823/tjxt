package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.tianji.api.dto.remark.LikeTimesDto;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-23
 */
//@Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;

    @Override
    public void addLikeRecord(LikeRecordFormDTO recordDTO) {
        // 判断是点赞还是取消点赞
        boolean success = recordDTO.getLiked() ? like(recordDTO) : unLike(recordDTO);
        if(!success) {
            return;
        }
        // 统计点赞数量
        Integer count = lambdaQuery().eq(LikedRecord::getBizId,recordDTO.getBizId()).count();
        // 通过MQ异步发送点赞数量
        mqHelper.send(MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE,recordDTO.getBizType())
                , LikeTimesDto.of(recordDTO.getBizId(), count));
    }

    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        List<LikedRecord> list = lambdaQuery().in(LikedRecord::getBizId, bizIds).eq(LikedRecord::getUserId, UserContext.getUser()).list();
        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int bizMaxNum) {

    }

    private boolean unLike(LikeRecordFormDTO recordDTO) {
        // 删除点赞记录
        return remove(new QueryWrapper<LikedRecord>().lambda()
                .eq(LikedRecord::getUserId, UserContext.getUser())
                .eq(LikedRecord::getBizId, recordDTO.getBizId()));
    }

    private boolean like(LikeRecordFormDTO recordDTO) {
        // 查询是否已经存在点赞记录
        Long userId = UserContext.getUser();
        Integer count = lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizId, recordDTO.getBizId())
                .count();
        if (count > 0) {
            return false;
        }
        // 保存点赞记录
        LikedRecord likedRecord = new LikedRecord();
        likedRecord.setUserId(userId);
        likedRecord.setBizId(recordDTO.getBizId());
        likedRecord.setBizType(recordDTO.getBizType());
        save(likedRecord);
        return true;
    }
}

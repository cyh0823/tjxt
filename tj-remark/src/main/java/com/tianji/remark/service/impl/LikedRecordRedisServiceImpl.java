package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.dto.remark.LikeTimesDto;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-23
 */
@Service
@RequiredArgsConstructor
public class LikedRecordRedisServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    @Override
    public void addLikeRecord(LikeRecordFormDTO recordDTO) {
        // 判断是点赞还是取消点赞
        boolean success = recordDTO.getLiked() ? like(recordDTO) : unLike(recordDTO);
        if(!success) {
            return;
        }
        // 统计点赞数量
        Long count = redisTemplate.opsForSet().size(RedisConstants.LIKE_BIZ_KEY_PREFIX + recordDTO.getBizId());
        if (count == null) {
            return;
        }
        // 将点赞数添加到redisZSet中
        // 这里的add是新增或修改
        redisTemplate.opsForZSet().add(
                RedisConstants.LIKE_TIMES_KEY_PREFIX + recordDTO.getBizType(),
                recordDTO.getBizId().toString(),
                count
        );
    }

    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        // 查询redis中是否存在点赞记录
        Long userId = UserContext.getUser();
        List<Object> objects = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection src = (StringRedisConnection) connection;
            for (Long bizId : bizIds) {
                String key = RedisConstants.LIKE_BIZ_KEY_PREFIX + bizId;
                src.sMIsMember(key, userId.toString());
            }
            return null;
        });
        /*Set<Long> set = new HashSet<>();
        for (int i = 0; i < objects.size(); i++) {
            boolean o = (boolean) objects.get(i);
            if (o) {
                set.add(bizIds.get(i));
            }
        }*/
        return IntStream.range(0, bizIds.size())
                .filter(i -> (boolean) objects.get(i))
                .mapToObj(bizIds::get)
                .collect(Collectors.toSet());
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int bizMaxNum) {
        String key = RedisConstants.LIKE_TIMES_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(key, bizMaxNum);
        if (CollUtils.isEmpty(tuples)) {
            return;
        }
        List<LikeTimesDto> list = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String bizId = tuple.getValue();
            Double count = tuple.getScore();
            if (bizId == null || count == null) {
                continue;
            }
            list.add(LikeTimesDto.of(Long.valueOf(bizId), count.intValue()));
        }
        // 发送消息
        mqHelper.send(MqConstants.Exchange.LIKE_RECORD_EXCHANGE,
                StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE,bizType),
                list);
    }

    private boolean unLike(LikeRecordFormDTO recordDTO) {
        // 删除点赞记录
        Long userId = UserContext.getUser();
        String bizId = RedisConstants.LIKE_BIZ_KEY_PREFIX + recordDTO.getBizId();
        Long add = redisTemplate.opsForSet().remove(bizId, userId.toString());
        return add != null && add > 0;
    }

    private boolean like(LikeRecordFormDTO recordDTO) {
        // 查询是否已经存在点赞记录
        Long userId = UserContext.getUser();
        String bizId = RedisConstants.LIKE_BIZ_KEY_PREFIX + recordDTO.getBizId();
        Long add = redisTemplate.opsForSet().add(bizId, userId.toString());
        return add != null && add > 0;
    }
}

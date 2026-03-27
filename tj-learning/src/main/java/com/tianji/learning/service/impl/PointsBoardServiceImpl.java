package com.tianji.learning.service.impl;

import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardItemVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.mapper.PointsBoardMapper;
import com.tianji.learning.service.IPointsBoardService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学霸天梯榜 服务实现类
 * </p>
 *
 * @author cyhhyc
 * @since 2026-03-24
 */
@Service
@RequiredArgsConstructor
public class PointsBoardServiceImpl extends ServiceImpl<PointsBoardMapper, PointsBoard> implements IPointsBoardService {

    private final StringRedisTemplate redisTemplate;
    private final UserClient userClient;

    @Override
    public PointsBoardVO getPointsBoardBySeason(PointsBoardQuery pointsBoardQuery) {
        // 判断是否是当前赛季
        boolean isCurrent = pointsBoardQuery.getSeason() == null || pointsBoardQuery.getSeason() == 0;
        // 获取Redis的key
        LocalDateTime now = LocalDateTime.now();
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + now.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);

        // 1.查询我的积分与排名
        PointsBoard pointsBoard = isCurrent ?
                queryMyCurrentBoard(key) : // 查询当前赛季（redis）
                queryMyHistoryBoard(pointsBoardQuery.getSeason());  // 查询历史赛季（MySql）

        // 2.查询榜单
        List<PointsBoard> list = isCurrent ?
                queryCurrentBoardList(key, pointsBoardQuery.getPageNo(), pointsBoardQuery.getPageSize()) : // 查询当前赛季（redis）
                queryHistoryBoardList(pointsBoardQuery);  // 查询历史赛季（MySql）
        PointsBoardVO p = new PointsBoardVO();
        if (BeanUtils.isNotEmpty(pointsBoard)) {
            p.setRank(pointsBoard.getRank() ==  null ? 0 : pointsBoard.getRank());
            p.setPoints(pointsBoard.getPoints() ==  null ? 0 : pointsBoard.getPoints());
        }
        if (CollUtils.isEmpty(list)) {
            return p;
        }
        List<Long> uIds = list.stream().map(PointsBoard::getUserId).collect(Collectors.toList());
        List<UserDTO> userDTOS = userClient.queryUserByIds(uIds);
        Map<Long, String> uMap = userDTOS.stream().collect(Collectors.toMap(UserDTO::getId, UserDTO::getName));
        List<PointsBoardItemVO> itemVOList = new ArrayList<>(list.size());
        for (PointsBoard board : list) {
            PointsBoardItemVO itemVO = new PointsBoardItemVO();
            itemVO.setRank(board.getRank());
            itemVO.setPoints(board.getPoints());
            itemVO.setName(uMap.get(board.getUserId()));
            itemVOList.add(itemVO);
        }
        p.setBoardList(itemVOList);
        return p;
    }

    @Override
    public void createPointBoardTableBySeason(Integer season) {
        getBaseMapper().createTable("points_board_" + season);
    }

    private PointsBoard queryMyCurrentBoard(String key) {
        BoundZSetOperations<String, String> zSet = redisTemplate.boundZSetOps(key);
        String userId = UserContext.getUser().toString();
        Double score = zSet.score(userId);
        Long l = zSet.reverseRank(userId);
        PointsBoard pointsBoard = new PointsBoard();
        pointsBoard.setPoints(score == null ? 0 : score.intValue());
        pointsBoard.setRank(l == null ? 0 : l.intValue());
        return pointsBoard;
    }

    private PointsBoard queryMyHistoryBoard(Long season) {
        return null;
    }

    @Override
    public List<PointsBoard> queryCurrentBoardList(String key, @Min(value = 1, message = "页码不能小于1") Integer pageNo, @Min(value = 1, message = "每页查询数量不能小于1") Integer pageSize) {
        int from = (pageNo - 1) * pageSize;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate
                .opsForZSet().reverseRangeWithScores(key, from, from + pageSize - 1);
        if (CollUtils.isEmpty(tuples)) {
            return CollUtils.emptyList();
        }
        int rank = from + 1;
        List<PointsBoard> list = new ArrayList<>(pageSize);
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple.getScore() == null || tuple.getValue() == null) {
                continue;
            }
            PointsBoard pointsBoard = new PointsBoard();
            pointsBoard.setUserId(Long.valueOf(tuple.getValue()));
            pointsBoard.setPoints(tuple.getScore().intValue());
            // 排名
            pointsBoard.setRank(rank++);
            list.add(pointsBoard);
        }
        return list;
    }

    private List<PointsBoard> queryHistoryBoardList(PointsBoardQuery pointsBoardQuery) {
        return null;
    }
}

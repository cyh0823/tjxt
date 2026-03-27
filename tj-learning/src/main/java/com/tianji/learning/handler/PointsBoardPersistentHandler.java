package com.tianji.learning.handler;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardService pointsBoardService;
    private final IPointsBoardSeasonService seasonService;
    private final StringRedisTemplate redisTemplate;

    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason() {
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        Integer season = seasonService.querySeasonByTime(time);
        if (season == null) {
            return;
        }
        pointsBoardService.createPointBoardTableBySeason(season);
    }

    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB(){
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        int pageNo = 1;
        int pageSize = 1000;
        // 动态查询表名
        Integer season = seasonService.querySeasonByTime(time);
        TableInfoContext.setInfo("points_board_" + season);
        while (true) {
            List<PointsBoard> list = pointsBoardService.queryCurrentBoardList(key, pageNo++, pageSize);
            if (CollUtils.isEmpty(list)) {
                break;
            }
            // 批量保存
            list.forEach(p -> {
                p.setId(p.getRank().longValue());
                p.setRank(null);
            });
            pointsBoardService.saveBatch(list);
        }

        TableInfoContext.clear();
    }

    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 删除大量数据时，建议使用 unlink 方法，而不是 delete 方法，因为 unlink 方法会立即释放内存，异步删除数据防止阻塞
        redisTemplate.unlink(key);
    }
}

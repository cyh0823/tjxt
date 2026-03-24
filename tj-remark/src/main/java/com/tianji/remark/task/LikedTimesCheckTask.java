package com.tianji.remark.task;

import com.tianji.remark.service.ILikedRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LikedTimesCheckTask {

    private static List<String> BIZ_TYPES = List.of("QA","NOTE");
    private static int BIZ_MAX_NUM = 50;   // 每次最多处理50条数据

    private final ILikedRecordService recordService;

    @Scheduled(fixedDelay = 20000)
    public void checkLikedTimes() {
        for (String bizType : BIZ_TYPES) {
            recordService.readLikedTimesAndSendMessage(bizType,BIZ_MAX_NUM);
        }
    }
}

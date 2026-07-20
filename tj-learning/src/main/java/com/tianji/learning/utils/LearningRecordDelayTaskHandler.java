package com.tianji.learning.utils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;

@Slf4j
@RequiredArgsConstructor
public class LearningRecordDelayTaskHandler {

    private final StringRedisTemplate redisTemplate;
    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";
    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    // 防止依赖循环
    private final LearningRecordMapper learningRecordMapper;
    private final ILearningLessonService learningLessonService;
    private static volatile boolean begin = true;   // volatile：保证对所有线程可见

    // 项目启动后就需要异步地执行这个任务
    @PostConstruct
    public void init(){
        CompletableFuture.runAsync(this::handleDelayTask);
    }

    @PreDestroy
    public void destroy(){
        begin = false;
    }

    public void handleDelayTask(){
        // 当获取不到队列时会阻塞休眠，不会占用CPU的线程
        while (begin){
            try {
                // 1. 获取数据，对比20秒前存入的延时数据跟缓存数据的内容是否一致
                DelayTask<RecordTaskData> task = queue.take();
                RecordTaskData data = task.getData();
                LearningRecord record = readRecordCache(data.getLessonId(), data.getSectionId());
                if (data == null){
                    continue;
                }
                if (!Objects.equals(data.getMoment(), record.getMoment())){
                    // 2.如果不等，放弃数据
                    continue;
                }else {
                    // 3.如果相等，需要更新数据库
                    // 更新学习记录数据库
                    record.setMoment(null);
                    learningRecordMapper.updateById(record);
                    // 更新课程数据库
                    LearningLesson lesson = new LearningLesson();
                    lesson.setId(data.getLessonId());
                    lesson.setLatestSectionId(data.getSectionId());
                    lesson.setLatestLearnTime(LocalDateTime.now());
                    learningLessonService.updateById(lesson);
                }
            } catch (Exception e) {
                log.error("处理延时事务失败！",e);
            }
        }
    }

    public void addLearningRecordTask(LearningRecord record){
        // 写缓存
        writeRecordCache(record);
        // 添加延时队列
        queue.add(new DelayTask<>(Duration.ofSeconds(20),new RecordTaskData(record)));
    }

    public void writeRecordCache(LearningRecord record) {
        log.debug("添加学习记录缓存");
        try {
            // 写入数据
            String json = JsonUtils.toJsonStr(new RecordCacheData(record));
            String key = StringUtils.format(RECORD_KEY_TEMPLATE,record.getLessonId());
            redisTemplate.opsForHash().put(key,record.getSectionId().toString(),json);
            // 添加过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("添加缓存失败:",e);
        }
    }

    public LearningRecord readRecordCache(Long lessonId, Long sectionId) {
        try {
            String key = StringUtils.format(RECORD_KEY_TEMPLATE,lessonId);
            Object o = redisTemplate.opsForHash().get(key, sectionId.toString());
            if (o == null) {
                return null;
            }
            return JsonUtils.toBean(o.toString(), LearningRecord.class);
        } catch (Exception e) {
            log.error("缓存数据异常",e);
            return null;
        }
    }

    public void removeRecordCache(Long lessonId, Long sectionId) {
        try {
            String key = StringUtils.format(RECORD_KEY_TEMPLATE,lessonId);
            redisTemplate.opsForHash().delete(key,sectionId.toString());
        } catch (Exception e) {
            log.error("删除缓存失败",e);
        }
    }

    @Data
    @NoArgsConstructor
    private static class RecordCacheData{
        private Long id;
        private Integer moment;
        private boolean finished;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = record.getFinished();
        }
    }

    @Data
    @NoArgsConstructor
    private static class RecordTaskData{
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.sectionId = record.getSectionId();
            this.moment = record.getMoment();
        }
    }
}

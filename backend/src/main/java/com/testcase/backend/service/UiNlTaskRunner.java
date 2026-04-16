package com.testcase.backend.service;

import com.testcase.backend.entity.UiNlTaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * UI NL 后台调度：定时拉取 QUEUED 任务做规划、轮询 RUNNING 任务向 {@link UiRunnerClient} 对齐状态（互斥锁防重入）。
 */
@Component
public class UiNlTaskRunner {
    private static final Logger log = LoggerFactory.getLogger(UiNlTaskRunner.class);
    private final UiNlService uiNlService;
    private final AtomicBoolean planningLock = new AtomicBoolean(false);
    private final AtomicBoolean runningLock = new AtomicBoolean(false);

    public UiNlTaskRunner(UiNlService uiNlService) {
        this.uiNlService = uiNlService;
    }

    @Scheduled(fixedDelay = 2000)
    public void planQueuedTasks() {
        if (!planningLock.compareAndSet(false, true)) {
            return;
        }
        try {
            uiNlService.planNextQueuedTask();
        } catch (Exception e) {
            log.warn("plan queued ui nl tasks failed: {}", e.getMessage());
        } finally {
            planningLock.set(false);
        }
    }

    @Scheduled(fixedDelay = 2000)
    public void pollRunningTasks() {
        if (!runningLock.compareAndSet(false, true)) {
            return;
        }
        try {
            List<UiNlTaskEntity> running = uiNlService.listRunningTasks();
            for (UiNlTaskEntity task : running) {
                try {
                    uiNlService.pollRunnerAndUpdateTask(task);
                } catch (Exception e) {
                    log.warn("poll ui nl task failed, taskId={}, err={}", task.getId(), e.getMessage());
                }
            }
        } finally {
            runningLock.set(false);
        }
    }
}

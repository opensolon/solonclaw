package com.jimuqu.solon.claw.scheduler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jimuqu.solon.claw.config.AppConfig;
import com.jimuqu.solon.claw.core.enums.PlatformType;
import com.jimuqu.solon.claw.core.model.CronJobRecord;
import com.jimuqu.solon.claw.core.model.DeliveryRequest;
import com.jimuqu.solon.claw.core.model.GatewayMessage;
import com.jimuqu.solon.claw.core.model.GatewayReply;
import com.jimuqu.solon.claw.core.model.HomeChannelRecord;
import com.jimuqu.solon.claw.core.repository.CronJobRepository;
import com.jimuqu.solon.claw.core.repository.GatewayPolicyRepository;
import com.jimuqu.solon.claw.core.service.ConversationOrchestrator;
import com.jimuqu.solon.claw.core.service.DeliveryService;
import com.jimuqu.solon.claw.support.CronSupport;
import com.jimuqu.solon.claw.support.SourceKeySupport;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.noear.solon.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** DefaultCronScheduler 实现。 */
@RequiredArgsConstructor
public class DefaultCronScheduler {
    private static final Logger log = LoggerFactory.getLogger(DefaultCronScheduler.class);

    private final AppConfig appConfig;
    private final CronJobRepository cronJobRepository;
    private final ConversationOrchestrator conversationOrchestrator;
    private final DeliveryService deliveryService;
    private final GatewayPolicyRepository gatewayPolicyRepository;
    private ScheduledExecutorService executorService;

    public void start() {
        if (!appConfig.getScheduler().isEnabled()) {
            return;
        }
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleWithFixedDelay(
                this::tickSafe, 5, appConfig.getScheduler().getTickSeconds(), TimeUnit.SECONDS);
    }

    public void stop() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    public void shutdown() {
        stop();
    }

    public void tickSafe() {
        try {
            tick();
        } catch (Exception e) {
            log.warn("Cron tick failed", e);
        }
    }

    public void tick() throws Exception {
        File lockFile = new File(appConfig.getRuntime().getHome(), "jobs/cron.tick.lock");
        FileUtil.mkParentDirs(lockFile);
        FileOutputStream outputStream = new FileOutputStream(lockFile, true);
        FileChannel channel = outputStream.getChannel();
        FileLock lock = channel.tryLock();
        if (lock == null) {
            channel.close();
            outputStream.close();
            return;
        }
        try {
            tickLocked();
        } finally {
            lock.release();
            channel.close();
            outputStream.close();
        }
    }

    private void tickLocked() throws Exception {
        long now = System.currentTimeMillis();
        List<CronJobRecord> jobs = cronJobRepository.listDue(now);
        Map<String, List<CronJobRecord>> grouped = groupBySource(jobs);
        if (grouped.isEmpty()) {
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(4, grouped.size()));
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (final List<CronJobRecord> sourceJobs : grouped.values()) {
            futures.add(
                    executor.submit(
                            new Runnable() {
                                @Override
                                public void run() {
                                    for (CronJobRecord job : sourceJobs) {
                                        try {
                                            execute(job, System.currentTimeMillis());
                                        } catch (Exception e) {
                                            log.warn(
                                                    "Cron job failed: jobId={}, sourceKey={}",
                                                    job.getJobId(),
                                                    job.getSourceKey(),
                                                    e);
                                        }
                                    }
                                }
                            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();
    }

    public void runNow(String jobId) throws Exception {
        CronJobRecord job = cronJobRepository.findById(jobId);
        if (job != null) {
            execute(job, System.currentTimeMillis());
        }
    }

    private void execute(CronJobRecord job, long now) throws Exception {
        long nextRunAt = CronSupport.nextRunAt(job.getCronExpr(), now);
        cronJobRepository.markRun(job.getJobId(), now, nextRunAt);
        String[] parts = SourceKeySupport.split(job.getSourceKey());
        GatewayMessage synthetic =
                new GatewayMessage(
                        PlatformType.fromName(parts[0]), parts[1], parts[2], job.getPrompt());
        GatewayReply reply = conversationOrchestrator.runScheduled(synthetic);
        deliver(job, reply);
    }

    private void deliver(CronJobRecord job, GatewayReply reply) throws Exception {
        if (reply == null || StrUtil.isBlank(reply.getContent())) {
            return;
        }
        String platformName =
                Utils.isNotEmpty(job.getDeliverPlatform()) ? job.getDeliverPlatform() : "local";
        String chatId = job.getDeliverChatId();
        if ("local".equalsIgnoreCase(platformName)) {
            String[] parts = SourceKeySupport.split(job.getSourceKey());
            platformName = parts[0];
            HomeChannelRecord home =
                    gatewayPolicyRepository.getHomeChannel(PlatformType.fromName(platformName));
            if (home == null || StrUtil.isBlank(home.getChatId())) {
                return;
            }
            chatId = home.getChatId();
        }

        PlatformType platform = PlatformType.fromName(platformName);
        if (platform == null) {
            log.warn(
                    "Cron deliver skipped because platform is unknown: jobId={}, platform={}",
                    job.getJobId(),
                    platformName);
            return;
        }
        if (StrUtil.isBlank(chatId)) {
            HomeChannelRecord home = gatewayPolicyRepository.getHomeChannel(platform);
            if (home == null || StrUtil.isBlank(home.getChatId())) {
                return;
            }
            chatId = home.getChatId();
        }
        DeliveryRequest request = new DeliveryRequest();
        request.setPlatform(platform);
        request.setChatId(chatId);
        request.setText(reply.getContent());
        deliveryService.deliver(request);
    }

    private Map<String, List<CronJobRecord>> groupBySource(List<CronJobRecord> jobs) {
        Map<String, List<CronJobRecord>> grouped = new LinkedHashMap<String, List<CronJobRecord>>();
        for (CronJobRecord job : jobs) {
            String sourceKey = StrUtil.blankToDefault(job.getSourceKey(), "__default__");
            List<CronJobRecord> sourceJobs = grouped.get(sourceKey);
            if (sourceJobs == null) {
                sourceJobs = new ArrayList<CronJobRecord>();
                grouped.put(sourceKey, sourceJobs);
            }
            sourceJobs.add(job);
        }
        return grouped;
    }
}

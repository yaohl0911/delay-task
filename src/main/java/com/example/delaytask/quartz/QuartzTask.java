package com.example.delaytask.quartz;

import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;


// TODO: 2022/2/7 补充Quartz更好的使用方式
@Slf4j
public class QuartzTask implements Job {

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        log.info("start to scan db to get task...");
    }

    public static void main(String[] args) throws Exception {
        // 创建任务
        JobDetail jobDetail = JobBuilder
            .newJob(QuartzTask.class)
            .withIdentity("quartz-job", "group1")
            .build();

        // 创建触发器 每3秒钟执行一次
        Trigger trigger = TriggerBuilder
            .newTrigger()
            .withIdentity("trigger1", "group3")
            .withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(3).repeatForever())
            .build();
        Scheduler scheduler = new StdSchedulerFactory().getScheduler();

        // 将任务及其触发器放入调度器
        scheduler.scheduleJob(jobDetail, trigger);
        // 调度器开始调度任务
        scheduler.start();
    }

}
/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.siddhi.core.trigger;

import org.apache.log4j.Logger;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.wso2.siddhi.core.config.ExecutionPlanContext;
import org.wso2.siddhi.core.event.Event;
import org.wso2.siddhi.core.stream.StreamJunction;
import org.wso2.siddhi.query.api.definition.TriggerDefinition;

/**
 * Created on 5/13/15.
 */
public class CronEventTrigger implements EventTrigger, Job {

    protected static final Logger log = Logger.getLogger(CronEventTrigger.class);

    private TriggerDefinition triggerDefinition;
    private ExecutionPlanContext executionPlanContext;
    private StreamJunction streamJunction;
    private Scheduler scheduler;
    private String jobName;
    private String jobGroup = "TriggerGroup";

    @Override
    public void init(TriggerDefinition triggerDefinition, ExecutionPlanContext executionPlanContext, StreamJunction streamJunction) {

        this.triggerDefinition = triggerDefinition;
        this.executionPlanContext = executionPlanContext;
        this.streamJunction = streamJunction;
    }

    @Override
    public TriggerDefinition getTriggerDefinition() {
        return triggerDefinition;
    }

    @Override
    public String getId() {
        return triggerDefinition.getId();
    }

    /**
     * This will be called only once and this can be used to acquire
     * required resources for the processing element.
     * This will be called after initializing the system and before
     * starting to process the events.
     */
    @Override
    public void start() {
        scheduleCronJob(triggerDefinition.getAt(), triggerDefinition.getId());
    }

    /**
     * This will be called only once and this can be used to release
     * the acquired resources for processing.
     * This will be called before shutting down the system.
     */
    @Override
    public void stop() {
        try {
            if (scheduler != null) {
                scheduler.deleteJob(new JobKey(jobName, jobGroup));
            }
        } catch (SchedulerException e) {
            log.error("Error while removing the cron trigger job, " + e.getMessage(), e);
        }
    }

    private void scheduleCronJob(String cronString, String elementId) {
        try {
            SchedulerFactory schedulerFactory = new StdSchedulerFactory();
            scheduler = schedulerFactory.getScheduler();
            scheduler.start();

            JobDataMap dataMap = new JobDataMap();
            dataMap.put("trigger", this);

            jobName = "TriggerJob_" + elementId;
            JobDetail job = org.quartz.JobBuilder.newJob(CronEventTrigger.class)
                    .withIdentity(jobName, jobGroup)
                    .usingJobData(dataMap)
                    .build();

            Trigger trigger = org.quartz.TriggerBuilder.newTrigger()
                    .withIdentity("TriggerJob_" + elementId, jobGroup)
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronString))
                    .build();

            scheduler.scheduleJob(job, trigger);

        } catch (SchedulerException e) {
            log.error("Error while instantiating quartz scheduler for trigger '" + triggerDefinition.getId() + "'," + e.getMessage(), e);
        }
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {


        JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        CronEventTrigger cronEventTrigger = (CronEventTrigger) dataMap.get("trigger");
        if (log.isDebugEnabled()) {
            log.debug("Running Trigger Job '" + cronEventTrigger.getId() + "'");
        }
        cronEventTrigger.sendEvent();
    }

    private void sendEvent() {
        long currentTime = executionPlanContext.getTimestampGenerator().currentTime();
        streamJunction.sendEvent(new Event(currentTime, new Object[]{currentTime}));
    }
}
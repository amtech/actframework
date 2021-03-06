package act.job;

/*-
 * #%L
 * ACT Framework
 * %%
 * Copyright (C) 2014 - 2017 ActFramework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import act.cli.*;
import act.event.OnEvent;
import act.util.JsonView;
import act.util.*;
import act.ws.*;
import com.alibaba.fastjson.JSONObject;
import org.osgl.$;
import org.osgl.mvc.annotation.GetAction;
import org.osgl.util.C;
import org.osgl.util.S;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.inject.Inject;

/**
 * Provide admin service to act {@link JobManager}
 */
@SuppressWarnings("unused")
public class JobAdmin {

    private static Comparator<Job> _UNIQ_JOB_FILTER = new Comparator<Job>() {
        @Override
        public int compare(Job o1, Job o2) {
            return o1.id().compareTo(o2.id());
        }
    };

    /**
     * List all jobs in the job manager
     * @return a list of {@link Job jobs}
     */
    @Command(value = "act.job.list", help = "List jobs")
    @PropertySpec(Job.BRIEF_VIEW)
    @TableView
    public List<Job> listJobs(@Optional(lead = "-q") final String q, JobManager jobManager) {
        C.List<Job> jobs = jobManager.jobs().append(jobManager.virtualJobs()).unique(_UNIQ_JOB_FILTER);
        if (S.notBlank(q)) {
            jobs = jobs.filter(new $.Predicate<Job>() {
                @Override
                public boolean test(Job job) {
                    return job.toString().contains(q);
                }
            });
        }
        return jobs;
    }

    @Command(value = "act.job.show", help = "Show job details")
    @JsonView
    @PropertySpec(Job.DETAIL_VIEW)
    public Job getJob(@Required("specify job id") final String id, JobManager jobManager) {
        return jobManager.jobById(id);
    }

    @Command(value = "act.job.progress", help = "Show job progress")
    public int getJobProgress(@Required("specify job id") final String id, JobManager jobManager) {
        Job job = jobManager.jobById(id);
        return null == job ? -1 : job.getProgressInPercent();
    }

    @Command(name = "act.job.cancel", help = "Cancel a job")
    public void cancel(@Required("specify job id") String id, JobManager jobManager) {
        jobManager.cancel(id);
    }

    @Command(value = "act.job.scheduler", help = "Show Job manager scheduler status")
    public String getSchedulerStatus(JobManager jobManager) {
        ScheduledThreadPoolExecutor executor = jobManager.executor();
        JSONObject json = new JSONObject();
        json.put("is terminating", executor.isTerminating());
        json.put("is terminated", executor.isTerminated());
        json.put("is shutdown", executor.isShutdown());
        json.put("# of runnable in the queue", executor.getQueue().size());
        json.put("active count", executor.getActiveCount());
        json.put("# of completed tasks", executor.getActiveCount());
        json.put("core pool size", executor.getCorePoolSize());
        json.put("pool size", executor.getPoolSize());
        return json.toJSONString();
    }

    @GetAction("jobs/{id}/progress")
    public ProgressGauge jobProgress(String id, JobManager jobManager) {
        Job job = jobManager.jobById(id);
        if (null == job) {
            return SimpleProgressGauge.NULL;
        }
        ProgressGauge gauge = job.progress();
        return null == gauge ? SimpleProgressGauge.NULL : gauge;
    }

    @WsEndpoint("/~/ws/jobs/{id}/progress")
    public static class WsProgress {
        @Inject
        private WebSocketConnectionManager connectionManager;

        @OnEvent
        public void onConnect(WebSocketConnectEvent event) {
            WebSocketContext context = event.source();
            String jobId = context.actionContext().paramVal("id");
            String tag = SimpleProgressGauge.wsJobProgressTag(jobId);
            connectionManager.subscribe(context.actionContext().session(), tag);
        }
    }

}

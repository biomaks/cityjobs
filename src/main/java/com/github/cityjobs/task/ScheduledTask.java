package com.github.cityjobs.task;

import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.github.cityjobs.model.Job;
import com.github.cityjobs.model.Run;
import com.github.cityjobs.service.JobService;
import com.github.cityjobs.service.Requester;
import com.github.cityjobs.service.RunService;
import com.github.cityjobs.util.JobParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ScheduledTask {


    private static final Logger log = LoggerFactory.getLogger(ScheduledTask.class);

    private final Requester requester;

    private final JobService jobService;

    private final JobParser jobParser;

    private final RunService runService;

    @Autowired
    public ScheduledTask(Requester requester, JobService jobService, JobParser jobParser, RunService runService) {
        this.requester = requester;
        this.jobService = jobService;
        this.jobParser = jobParser;
        this.runService = runService;
    }

    @Value("${spring.startpage}")
    private String startPage;


    @Scheduled(fixedDelayString = "${spring.schedule.rate}")
    public void gatheringJobs() {
        Run run = new Run();
        run.setTimeStarted(new Date());
        run.setProcessed(false);
        run = runService.saveRun(run);
        try {
            List<HtmlElement> jobBlocks = requester.getJobBlocks(startPage);
            List<Job> jobs = jobParser.getJobsFromBlock(jobBlocks);
            for (Job job : jobs) {
                job.setRun(run);
            }
            List<Job> j = jobService.saveJobs(jobs);
            System.out.println();
        } catch (Exception e) {
            run.setComment(e.getMessage());
        } finally {
            run.setTimeFinished(new Date());
            runService.saveRun(run);
        }
    }

    @Scheduled(fixedDelayString = "${spring.schedule.ratejobs}")
    public void processJobs() {
        List<Run> runs = runService.findAllByIsProcessedOrderByIdAsc(false);
        if (runs.size() > 1) {
            for (int i = 0; i < runs.size(); i++) {
                if (i == 0) {
                    Run currentRun = runs.get(i);
                    currentRun.setProcessed(true);
                    runService.saveRun(currentRun);
                    continue;
                }
                Run currentRun = runs.get(i);
                Run previousRun = runs.get(i - 1);
                Set<Job> currentRunJobs = currentRun.getJobs();
                Set<Job> previousRunJobs = previousRun.getJobs();
                Set<Integer> currentRunJobIds = convertJobSetToJobIdSet(currentRunJobs);
                Set<Integer> previousRunJobIds = convertJobSetToJobIdSet(previousRunJobs);

                Set<Integer> expiredJobIds = getExpiredJobIds(previousRunJobIds, currentRunJobIds);
                Set<Integer> newJobIds = getNewJobIds(previousRunJobIds, currentRunJobIds);

                Set<Job> expiredJobs = convertJobIdsToJobs(expiredJobIds, previousRun.getId());
                expiredJobs.forEach(job -> job.setActive(false));
                jobService.saveJobs(new ArrayList<>(expiredJobs));
            }

        } else {
            Run run = runs.get(0);
            Set<Job> activeJobSet = new HashSet<>(jobService.findAllJobsByIsActive(true));
            Set<Job> currentJobs = run.getJobs();
            activeJobSet.removeAll(currentJobs);
            System.out.println();
        }
    }

    private Set<Job> convertJobIdsToJobs(Set<Integer> jobIds, Long runId){
        Set<Job> jobs = new HashSet<>();
        jobIds.forEach(jobId-> jobs.add(jobService.findJobByJobIdAndRunId(jobId, runId)));
        return jobs;
    }

    private Set<Integer> getExpiredJobIds(Set<Integer> previousJobs, Set<Integer> currentJobs) {
        Set<Integer> prevSet = new HashSet<>(previousJobs);
        Set<Integer> currSet = new HashSet<>(currentJobs);
        prevSet.removeAll(currSet);
        return prevSet;
    }

    private Set<Integer> getNewJobIds(Set<Integer> previousJobs, Set<Integer> currentJobs) {
        Set<Integer> prevSet = new HashSet<>(previousJobs);
        Set<Integer> currSet = new HashSet<>(currentJobs);
        currSet.removeAll(prevSet);
        return currSet;
    }

    private Set<Integer> convertJobSetToJobIdSet(Set<Job> jobSet) {
        Set<Integer> jobIdSet = new HashSet<>();
        jobSet.forEach(job -> jobIdSet.add(job.getJobId()));
        return jobIdSet;
    }
}

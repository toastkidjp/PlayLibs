package play.jobs;

import play.jobs.Job;
import play.jobs.On;

/**
 * 動作検証用On Job.
 * @author Toast kid
 */
@On("*/1 * * * * ?")
public final class VerificationOnJob extends Job {
    /** カウンタ. */
    int i = 0;
    @Override
    public void doJob() {
        i++;
    }
}
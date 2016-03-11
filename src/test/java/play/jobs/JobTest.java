package play.jobs;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import play.jobs.JobScheduler;

/**
 * Job 関連の動作検証.
 * @author Toast kid
 *
 */
public final class JobTest {
    /** 何回カウントアップさせるか. */
    private static final int TESTING_COUNT = 2;

    /**
     * Countup するだけのJobが正しく動作することを確認する.
     * @throws InterruptedException
     */
    @Test
    public void test() throws InterruptedException {
        final VerificationOnJob    onJob    = new VerificationOnJob();
        final VerificationEveryJob everyJob = new VerificationEveryJob();
        JobScheduler.schedule(onJob);
        JobScheduler.schedule(everyJob);
        while (true) {
            Thread.sleep(1000l);
            if (onJob.i >= TESTING_COUNT && everyJob.i >= TESTING_COUNT) {
                break;
            }
        }
        assertTrue(true);
    }
}

package play.jobs;


/**
 * 検証用EveryJob.
 * @author Toast kid
 *
 */
@Every("1s")
public final class VerificationEveryJob extends Job {
    /** カウンタ. */
    public int i;

    @Override
    public void doJob() throws Exception {
        i++;
    }
}

package play.jobs;

import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import play.libs.Time;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;

/**
 * 非同期実行ジョブ.
 * このクラスを継承した独自のJobクラスを作り、
 * 定期実行したい処理を doJob() あるいは doJobWithResult() に記述し、
 * JobScheduler に set する.
 * @author Toast kid
 * @param <V>
 */
public abstract class Job<V> implements Callable<V>, Runnable {
    protected ExecutorService executor;
    protected long lastRun = 0;
    protected boolean wasError = false;
    public Date nextPlannedExecution;

    /**
     * Here you do the job
     */
    public void doJob() throws Exception {
    }

    /**
     * Here you do the job and return a result
     */
    public V doJobWithResult() throws Exception {
        doJob();
        return null;
    }

    /**
     * Run this job every n seconds.
     * @param delay 3min, 2s
     */
    public void every(String delay) {
        every(Time.parseDuration(delay));
    }

    /**
     * Run this job every n seconds.
     * @param seconds seconds
     */
    public void every(int seconds) {
        JobScheduler.executor.scheduleWithFixedDelay(this, seconds, seconds, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        call();
    }
    @Override
    public V call() {
        Monitor monitor = null;
        try {
            V result = null;
            lastRun = System.currentTimeMillis();
            monitor = MonitorFactory.start(getClass().getName()+".doJob()");
            result = doJobWithResult();
            monitor.stop();
            monitor = null;
            wasError = false;
            return result;
        } catch (Exception e) {
            System.err.println(e);;
        } finally {
            if(monitor != null) {
                monitor.stop();
            }
            _finally();
        }
        return null;
    }
    /**
     * 次のCRON実行Jobをセットする.
     * set cron to JobScheduler.executor.
     */
    public void _finally() {
        if (executor == JobScheduler.executor) {
            JobScheduler.scheduleForCRON(this);
        }
    }

}

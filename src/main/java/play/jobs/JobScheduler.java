package play.jobs;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import play.libs.Time;
import play.libs.Time.CronExpression;


/**
 * 非同期ジョブの管理.
 * @author Toast kid
 *
 */
public final class JobScheduler {
    /** ScheduledThreadPoolExecutor. */
    public static ScheduledThreadPoolExecutor executor = null;
    /** 登録されている Job.*/
    private static List<Job> scheduledJobs = null;
    /** ${....}を探す正規表現. */
    private static Pattern expression = Pattern.compile("^\\$\\{(.*)\\}$");
    /** 同時に動作させる Job 数. */
    private static int core = 10;
    static {
        scheduledJobs = new ArrayList<Job>();
        executor = new ScheduledThreadPoolExecutor(
                core,
                new JobThreadFactory("jobs"),
                new ThreadPoolExecutor.AbortPolicy()
                );
        /*
        // Job の自動登録機能.
        List<Class<?>> jobs = new ArrayList<Class<?>>();
        for (Class clazz : Play.classloader.getAllClasses()) {
            if (Job.class.isAssignableFrom(clazz)) {
                jobs.add(clazz);
            }
        }
        for (final Class<?> clazz : jobs) {
            try {
                Job<?> job = ((Job<?>) clazz.newInstance());
                schedule(job);
            } catch (IllegalAccessException | InstantiationException ex) {
                System.err.println("Cannot instanciate Job " + clazz.getName());
                ex.printStackTrace();
            }
        }
        //*/
    }
    /**
     * 登録されているジョブのステータスを表示する.
     * @return
     */
    public String getStatus() {
        final StringWriter sw = new StringWriter();
        final PrintWriter out = new PrintWriter(sw);
        if (executor == null) {
            out.println("Jobs execution pool:");
            out.println("~~~~~~~~~~~~~~~~~~~");
            out.println("(not yet started)");
            return sw.toString();
        }
        out.println("Jobs execution pool:");
        out.println("~~~~~~~~~~~~~~~~~~~");
        out.println("Pool size: " + executor.getPoolSize());
        out.println("Active count: " + executor.getActiveCount());
        out.println("Scheduled task count: " + executor.getTaskCount());
        out.println("Queue size: " + executor.getQueue().size());
        final SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        if (!scheduledJobs.isEmpty()) {
            out.println();
            out.println("Scheduled jobs ("+scheduledJobs.size()+"):");
            out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~");
            for (final Job job : scheduledJobs) {
                out.print(job.getClass().getName());
                if( job.getClass().isAnnotationPresent(On.class)) {

                    final String cron = job.getClass().getAnnotation(On.class).value();
                    out.print(" run with cron expression " + cron + ".");
                }
                if (job.getClass().isAnnotationPresent(Every.class)) {
                    out.print(
                            " run every " + job.getClass().getAnnotation(Every.class).value() + "."
                            );
                }
                if (job.lastRun > 0) {
                    out.print(" (last run at " + df.format(new Date(job.lastRun)));
                    if(job.wasError) {
                        out.print(" with error)");
                    } else {
                        out.print(")");
                    }
                } else {
                    out.print(" (has never run)");
                }
                out.println();
            }
        }
        if (!executor.getQueue().isEmpty()) {
            out.println();
            out.println("Waiting jobs:");
            out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            for (final Object o : executor.getQueue()) {
                final ScheduledFuture task = (ScheduledFuture)o;
                out.println(
                        extractUnderlyingCallable((FutureTask)task)
                        + " will run in " + task.getDelay(TimeUnit.SECONDS) + " seconds");
            }
        }
        return sw.toString();
    }
    /**
     * Cron式のJobを登録する.
     * @param job
     */
    protected static <V> void scheduleForCRON(final Job<V> job) {
        if (!job.getClass().isAnnotationPresent(On.class)) {
            return;
        }
        String cron = job.getClass().getAnnotation(On.class).value();
        /*if (cron.startsWith("cron.")) {
            cron = Play.configuration.getProperty(cron);
        }
        //*/
        cron = evaluate(cron, cron).toString();
        if (cron == null || "".equals(cron) || "never".equalsIgnoreCase(cron)) {
            System.out.printf(
                    "Skipping job %s, cron expression is not defined",
                    job.getClass().getName()
                    );
            return;
        }
        try {
            final Date now = new Date();
            cron = evaluate(cron, cron).toString();
            final CronExpression cronExp = new CronExpression(cron);
            Date nextDate = cronExp.getNextValidTimeAfter(now);
            if (nextDate == null) {
                System.err.printf(
                        "The cron expression for job %s doesn't have any match in the future,"
                                + " will never be executed",
                        job.getClass().getName()
                        );
                return;
            }
            if (nextDate.equals(job.nextPlannedExecution)) {
                // Bug #13: avoid running the job twice for the same time
                // (happens when we end up running the job a few minutes before the planned time)
                final Date nextInvalid = cronExp.getNextInvalidTimeAfter(nextDate);
                nextDate = cronExp.getNextValidTimeAfter(nextInvalid);
            }
            job.nextPlannedExecution = nextDate;
            executor.schedule(
                    (Callable<V>)job,
                    nextDate.getTime() - now.getTime(),
                    TimeUnit.MILLISECONDS
                    );
            job.executor = executor;
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * Try to discover what is hidden under a FutureTask (hack).
     * @param futureTask
     * @return
     */
    public static Object extractUnderlyingCallable(final FutureTask<?> futureTask) {
        try {
            final Field syncField = FutureTask.class.getDeclaredField("sync");
            syncField.setAccessible(true);
            final Object sync = syncField.get(futureTask);
            final Field callableField = sync.getClass().getDeclaredField("callable");
            callableField.setAccessible(true);
            final Object callable = callableField.get(sync);
            if (callable.getClass().getSimpleName().equals("RunnableAdapter")) {
                final Field taskField = callable.getClass().getDeclaredField("task");
                taskField.setAccessible(true);
                return taskField.get(callable);
            }
            return callable;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * 文字列の評価を実行する.
     * @param value
     * @param defaultValue
     * @return
     */
    public static Object evaluate(
            final String value,
            final String defaultValue
            ) {
        final Matcher matcher = expression.matcher(value);
        if (matcher.matches()) {
            return matcher.group(1);
            //Play.configuration.getProperty(matcher.group(1), defaultValue);
        }
        return value;
    }
    /**
     * Jobを登録する.
     * @param job Job オブジェクト.
     */
    public static void schedule(final Job job) {
        final Class c = job.getClass();
        // @On
        if (c.isAnnotationPresent(On.class)) {
            scheduledJobs.add(job);
            scheduleForCRON(job);
        }
        // @Every
        if (c.isAnnotationPresent(Every.class)) {
            scheduledJobs.add(job);
            String value = job.getClass().getAnnotation(Every.class).value();
            /*
            if (value.startsWith("cron.")) {
                value = Play.configuration.getProperty(value);
            }
            //*/
            value = evaluate(value, value).toString();
            if(!"never".equalsIgnoreCase(value)){
                executor.scheduleWithFixedDelay(
                        job,
                        Time.parseDuration(value),
                        Time.parseDuration(value),
                        TimeUnit.SECONDS
                        );
            }
        }
    }
}

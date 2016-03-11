package play.jobs;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Job 管理用の ThreadFactory.
 * @author Toast kid
 *
 */
public final class JobThreadFactory implements ThreadFactory {
    /** Thread group. */
    final ThreadGroup group;
    /** Thread number. */
    final AtomicInteger threadNumber = new AtomicInteger(1);
    /** prefix. */
    final String namePrefix;
    /**
     * 与えられたプール名で初期化する.
     * @param poolName 設定するプール名
     */
    public JobThreadFactory(final String poolName) {
        final SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        namePrefix = poolName + "-thread-";
    }
    @Override
    public Thread newThread(final Runnable r) {
        Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
        if (t.isDaemon()) {
            t.setDaemon(false);
        }
        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }
        return t;
    }
}

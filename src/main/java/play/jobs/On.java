package play.jobs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Run the job using a Cron expression.
 * We use the Quartz CRON trigger.
 * <pre>
 * @On("10 * * * * ?")
 * </pre>
 * @see <a href="http://www.opensymphony.com/quartz/wikidocs/CronTriggers%20Tutorial.html">
 * Quartz CRON trigger</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface On {
    String value();
}

package play.jobs;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import play.libs.Time;

/**
 * Time's behavior test.
 * @author Toast kid
 *
 */
public class TimeTest {

    /**
     * check parseDuration().
     */
    @Test
    public void testParseDuration() {
        assertEquals(86400, Time.parseDuration("1d"));
        assertEquals(120, Time.parseDuration("2min"));
        assertEquals(120, Time.parseDuration("2mn"));
        assertEquals(3, Time.parseDuration("3s"));
        assertEquals(14400, Time.parseDuration("4h"));
        assertEquals(2592000, Time.parseDuration(null));
    }
}

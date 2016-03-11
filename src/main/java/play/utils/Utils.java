package play.utils;

import java.lang.annotation.Annotation;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Generic utils.
 */
public class Utils {

    public static <T> String join(final Iterable<T> values, final String separator) {
        if (values == null) {
            return "";
        }
        final Iterator<T> iter = values.iterator();
        if (!iter.hasNext()) {
            return "";
        }
        final StringBuffer toReturn = new StringBuffer(String.valueOf(iter.next()));
        while (iter.hasNext()) {
            toReturn.append(separator + String.valueOf(iter.next()));
        }
        return toReturn.toString();
    }

    public static String join(final String[] values, final String separator) {
        return (values == null) ? "" : join(Arrays.asList(values), separator);
    }

    public static String join(final Annotation[] values, final String separator) {
        return (values == null) ? "" : join(Arrays.asList(values), separator);
    }

    public static String getSimpleNames(final Annotation[] values) {
        if (values == null) {
            return "";
        }
        final List<Annotation> a = Arrays.asList(values);
        final Iterator<Annotation> iter = a.iterator();
        if (!iter.hasNext()) {
            return "";
        }
        final StringBuffer toReturn = new StringBuffer("@" + iter.next().annotationType().getSimpleName());
        while (iter.hasNext()) {
            toReturn.append(", @" + iter.next().annotationType().getSimpleName());
        }
        return toReturn.toString();
    }

    /**
     * for java.util.Map
     */
    public static class Maps {

        public static void mergeValueInMap(final Map<String, String[]> map, final String name, final String value) {
            String[] newValues = null;
            final String[] oldValues = map.get(name);
            if (oldValues == null) {
                newValues = new String[1];
                newValues[0] = value;
            } else {
                newValues = new String[oldValues.length + 1];
                System.arraycopy(oldValues, 0, newValues, 0, oldValues.length);
                newValues[oldValues.length] = value;
            }
            map.put(name, newValues);
        }

        public static void mergeValueInMap(final Map<String, String[]> map, final String name, final String[] values) {
            for (final String value : values) {
                mergeValueInMap(map, name, value);
            }
        }

        public static <K, V> Map<K, V> filterMap(final Map<K, V> map, final String keypattern) {
            try {
                @SuppressWarnings("unchecked")
                final
                Map<K, V> filtered = map.getClass().newInstance();
                for (final Map.Entry<K, V> entry : map.entrySet()) {
                    final K key = entry.getKey();
                    if (key.toString().matches(keypattern)) {
                        filtered.put(key, entry.getValue());
                    }
                }
                return filtered;
            } catch (final Exception iex) {
                return null;
            }
        }
    }
    private static ThreadLocal<SimpleDateFormat> httpFormatter = new ThreadLocal<SimpleDateFormat>();

    public static SimpleDateFormat getHttpDateFormatter() {
        if (httpFormatter.get() == null) {
            httpFormatter.set(new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US));
            httpFormatter.get().setTimeZone(TimeZone.getTimeZone("GMT"));
        }
        return httpFormatter.get();
    }

    public static Map<String, String[]> filterMap(final Map<String, String[]> map, final String prefix) {
        final Map<String, String[]> newMap = new HashMap<String, String[]>();
        for (final String key : map.keySet()) {
            if (!key.startsWith(prefix + ".")) {
                newMap.put(key, map.get(key));
            }
        }
        return newMap;
    }

    public static Map<String, String> filterParams(final Map<String, String[]> params, String prefix, final String separator) {
        final Map<String, String> filteredMap = new LinkedHashMap<String, String>();
        prefix += ".";
        for(final Map.Entry<String, String[]> e: params.entrySet()){
            if(e.getKey().startsWith(prefix)) {
                filteredMap.put(
                        e.getKey().substring(prefix.length()),
                        Utils.join(e.getValue(), separator)
                );
            }
        }
        return filteredMap;
    }

    public static Map<String, String> filterParams(final Map<String, String[]> params, final String prefix) {
        return filterParams(params, prefix, ", ");
    }

    /**
     * kill process by pid.
     * @param pid Process ID.
     * @throws Exception
     */
    public static void kill(final String pid) throws Exception {
        final String os = System.getProperty("os.name");
        final String command = (os.startsWith("Windows"))
                       ? "taskkill /F /PID " + pid
                       : "kill " + pid;
        Runtime.getRuntime().exec(command).waitFor();
    }

    public static class AlternativeDateFormat {

        Locale locale;
        List<SimpleDateFormat> formats = new ArrayList<SimpleDateFormat>();

        public AlternativeDateFormat(final Locale locale, final String... alternativeFormats) {
            super();
            this.locale = locale;
            setFormats(alternativeFormats);
        }

        public void setFormats(final String... alternativeFormats) {
            for (final String format : alternativeFormats) {
                formats.add(new SimpleDateFormat(format, locale));
            }
        }

        public Date parse(final String source) throws ParseException {
            for (final SimpleDateFormat dateFormat : formats) {
                if (source.length() == dateFormat.toPattern().replace("\'", "").length()) {
                    try {
                        return dateFormat.parse(source);
                    } catch (final ParseException ex) {
                    }
                }
            }
            throw new ParseException("Date format not understood", 0);
        }
        static ThreadLocal<AlternativeDateFormat> dateformat = new ThreadLocal<AlternativeDateFormat>();

        public static AlternativeDateFormat getDefaultFormatter() {
            if (dateformat.get() == null) {
                dateformat.set(new AlternativeDateFormat(Locale.US,
                        "yyyy-MM-dd'T'HH:mm:ss'Z'", // ISO8601 + timezone
                        "yyyy-MM-dd'T'HH:mm:ss", // ISO8601
                        "yyyy-MM-dd HH:mm:ss",
                        "yyyyMMdd HHmmss",
                        "yyyy-MM-dd",
                        "yyyyMMdd'T'HHmmss",
                        "yyyyMMddHHmmss",
                        "dd'/'MM'/'yyyy",
                        "dd-MM-yyyy",
                        "dd'/'MM'/'yyyy HH:mm:ss",
                        "dd-MM-yyyy HH:mm:ss",
                        "ddMMyyyy HHmmss",
                        "ddMMyyyy"
                        )
                );
            }
            return dateformat.get();
        }
    }


    public static String urlDecodePath(final String enc) {
        try {
          return URLDecoder.decode(enc.replaceAll("\\+", "%2B"), "UTF-8");
        } catch(final Exception e) {
            return enc;
        }
    }

}

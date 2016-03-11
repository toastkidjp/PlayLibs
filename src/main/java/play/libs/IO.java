package play.libs;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.IOUtils;

/**
 * Play 1.x の IO を移植.
 * IO utils.
 */
public class IO {

    /**
     * Read a properties file with the utf-8 encoding.
     * @param is Stream to properties file
     * @return The Properties object
     */
    public static Properties readUtf8Properties(final InputStream is) {
        final Properties properties = new Properties();//new OrderSafeProperties();
        try {
            properties.load(is);
            is.close();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        return properties;
    }

    /**
     * Read the Stream content as a string (use utf-8)
     * @param is The stream to read
     * @return The String content
     */
    public static String readContentAsString(final InputStream is) {
        return readContentAsString(is, "utf-8");
    }

    /**
     * Read the Stream content as a string
     * @param is The stream to read
     * @return The String content
     */
    public static String readContentAsString(final InputStream is, final String encoding) {
        String res = null;
        try {
            res = IOUtils.toString(is, encoding);
        } catch(final Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                is.close();
            } catch(final Exception e) {
                //
            }
        }
        return res;
    }
    /**
     * Read file content to a String (always use utf-8)
     * @param file The file to read
     * @return The String content
     */
    public static String readContentAsString(final File file) {
        return readContentAsString(file, "utf-8");
    }

    /**
     * Read file content to a String
     * @param file The file to read
     * @return The String content
     */
    public static String readContentAsString(final File file, final String encoding) {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            final StringWriter result = new StringWriter();
            final PrintWriter out = new PrintWriter(result);
            final BufferedReader reader = new BufferedReader(new InputStreamReader(is, encoding));
            String line = null;
            while ((line = reader.readLine()) != null) {
                out.println(line);
            }
            return result.toString();
        } catch(final IOException e) {
            //throw e;
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch(final Exception e) {
                    //
                }
            }
        }
        return null;
    }

    public static List<String> readLines(final InputStream is) {
        return readLines(is, "utf-8");
    }


    public static List<String> readLines(final InputStream is, final String encoding) {
        List<String> lines = null;
        try {
            lines = IOUtils.readLines(is, encoding);
        } catch (final IOException ex) {
            //throw new UnexpectedException(ex);
        }
        return lines;
    }

    public static List<String> readLines(final File file, final String encoding) {
        List<String> lines = null;
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            lines = IOUtils.readLines(is, encoding);
        } catch (final IOException ex) {
            //throw new UnexpectedException(ex);
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch(final Exception e) {
                    //
                }
            }
        }
        return lines;
    }

    public static List<String> readLines(final File file) {
        return readLines(file, "utf-8");
    }

    /**
     * Read binary content of a file (warning does not use on large file !)
     * @param file The file te read
     * @return The binary data
     */
    public static byte[] readContent(final File file) {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            final byte[] result = new byte[(int) file.length()];
            is.read(result);
            return result;
        } catch(final IOException e) {
            //throw new UnexpectedException(e);
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch(final Exception e) {
                    //
                }
            }
        }
        return null;
    }

    /**
     * Read binary content of a stream (warning does not use on large file !)
     * @param is The stream to read
     * @return The binary data
     */
    public static byte[] readContent(final InputStream is) {
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int read = 0;
            final byte[] buffer = new byte[8096];
            while ((read = is.read(buffer)) > 0) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        } catch(final IOException e) {
            //throw new UnexpectedException(e);
        }
        return null;
    }

    /**
     * Write String content to a stream (always use utf-8)
     * @param content The content to write
     * @param os The stream to write
     */
    public static void writeContent(final CharSequence content, final OutputStream os) {
        writeContent(content, os, "utf-8");
    }

    /**
     * Write String content to a stream (always use utf-8)
     * @param content The content to write
     * @param os The stream to write
     */
    public static void writeContent(final CharSequence content, final OutputStream os, final String encoding) {
        try {
            final PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(os, encoding));
            printWriter.println(content);
            printWriter.flush();
            os.flush();
        } catch(final IOException e) {
            //throw new UnexpectedException(e);
        } finally {
            try {
                os.close();
            } catch(final Exception e) {
                //
            }
        }
    }

    /**
     * Write String content to a file (always use utf-8)
     * @param content The content to write
     * @param file The file to write
     */
    public static void writeContent(final CharSequence content, final File file) {
        writeContent(content, file, "utf-8");
    }

    /**
     * Write String content to a file (always use utf-8)
     * @param content The content to write
     * @param file The file to write
     */
    public static void writeContent(final CharSequence content, final File file, final String encoding) {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            final PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(os, encoding));
            printWriter.println(content);
            printWriter.flush();
            os.flush();
        } catch(final IOException e) {
            //throw new UnexpectedException(e);
        } finally {
            try {
                if(os != null) os.close();
            } catch(final Exception e) {
                //
            }
        }
    }

    /**
     * Write binay data to a file
     * @param data The binary data to write
     * @param file The file to write
     */
    public static void write(final byte[] data, final File file) {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            os.write(data);
            os.flush();
        } catch(final IOException e) {
            //throw new UnexpectedException(e);
        } finally {
            try {
                if(os != null) os.close();
            } catch(final Exception e) {
                //
            }
        }
    }

    /**
     * Copy an stream to another one.
     */
    public static void copy(final InputStream is, final OutputStream os) {
        try {
            int read = 0;
            final byte[] buffer = new byte[8096];
            while ((read = is.read(buffer)) > 0) {
                os.write(buffer, 0, read);
            }
        } catch(final IOException e) {
            //throw new UnexpectedException(e);
        } finally {
            try {
                is.close();
            } catch(final Exception e) {
                //
            }
        }
    }

    /**
     * Copy an stream to another one.
     */
    public static void write(final InputStream is, final OutputStream os) {
        try {
            int read = 0;
            final byte[] buffer = new byte[8096];
            while ((read = is.read(buffer)) > 0) {
                os.write(buffer, 0, read);
            }
        } catch(final IOException e) {
            //throw new UnexpectedException(e);
        } finally {
            try {
                is.close();
            } catch(final Exception e) {
                //
            }
            try {
                os.close();
            } catch(final Exception e) {
                //
            }
        }
    }

   /**
     * Copy an stream to another one.
     */
    public static void write(final InputStream is, final File f) {
        OutputStream os = null;
        try {
            os = new FileOutputStream(f);
            int read = 0;
            final byte[] buffer = new byte[8096];
            while ((read = is.read(buffer)) > 0) {
                os.write(buffer, 0, read);
            }
        } catch(final IOException e) {
            //throw new UnexpectedException(e);
        } finally {
            try {
                is.close();
            } catch(final Exception e) {
                //
            }
            try {
                if(os != null) os.close();
            } catch(final Exception e) {
                //
            }
        }
    }

    // If targetLocation does not exist, it will be created.
    public static void copyDirectory(final File source, final File target) {
        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdir();
            }
            for (final String child: source.list()) {
                copyDirectory(new File(source, child), new File(target, child));
            }
        } else {
            try {
                write(new FileInputStream(source),  new FileOutputStream(target));
            } catch (final IOException e) {
                //throw new UnexpectedException(e);
            }
        }
    }

}

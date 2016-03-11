
package play.exceptions;

import java.util.List;

/**
 * Error while sending an email
 */
public class MailException extends PlayException implements SourceAttachment {

    /**
     * serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    private String sourceFile;
    private List<String> source;
    private Integer line;

    public MailException(final String message) {
        super(message, null);
    }

    public MailException(final String message, final Throwable cause) {
        super(message, cause);
        final StackTraceElement element = getInterestingStrackTraceElement(cause);
        /*
        if(element != null) {
            ApplicationClass applicationClass = Play.classes.getApplicationClass(element.getClassName());
            sourceFile = applicationClass.javaFile.relativePath();
            source = Arrays.asList(applicationClass.javaSource.split("\n"));
            line = element.getLineNumber();
        }
        */
    }

    @Override
    public String getErrorTitle() {
        return "Mail error";
    }

    @Override
    public String getErrorDescription() {
        return String.format("A mail error occured : <strong>%s</strong>", getMessage());
    }

    @Override
    public String getSourceFile() {
        return sourceFile;
    }

    @Override
    public List<String> getSource() {
        return source;
    }

    @Override
    public Integer getLineNumber() {
        return line;
    }

    @Override
    public boolean isSourceAvailable() {
        return sourceFile != null;
    }

}

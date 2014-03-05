/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package glassfrog.tools;

/**
 * An error handler for the XML Validator.  Dumps all output to stderr
 * @author jdavidso
 */
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

public class XMLErrorHandler implements ErrorHandler {

    private String logPath = "logs/";
    private static final String ERROR_LOG = "errorLog.log";
    private static final String ERROR_LOGGER = "glassfrog.errorlogger";

    public void error(SAXParseException exception) {
        System.err.println("error: " + exception.getMessage());
    }

    public void fatalError(SAXParseException exception) {
        System.err.println("fatalError: " + exception.getMessage());
    }

    public void warning(SAXParseException exception) {
        System.err.println("warning: " + exception.getMessage());
    }
}


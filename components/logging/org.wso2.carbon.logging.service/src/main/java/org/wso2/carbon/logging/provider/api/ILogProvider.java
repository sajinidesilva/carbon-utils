package org.wso2.carbon.logging.provider.api;

import org.wso2.carbon.logging.service.LogViewerException;
import org.wso2.carbon.logging.service.data.LogEvent;
import org.wso2.carbon.logging.service.data.LoggingConfig;

/**
 * Created by shameera on 6/24/14.
 */
public interface ILogProvider {

    /**
     * Initialize the log provider by reading the property comes with logging configuration file
     * This will be called immediate after create new instance of ILogProvider
     * @param loggingConfig
     */
    public void init(LoggingConfig loggingConfig);

    public String[] getApplicationNames(String domain, String serverKey) throws LogViewerException;

//    public LogEvent[] getApplicationLogs(String type, String keyword, String appName, String domain, String serverKey) throws LogViewerException;

    public LogEvent[] getSystemLogs() throws LogViewerException;

    public LogEvent[] getAllLogs(String domain, String serverKey) throws LogViewerException;

    public LogEvent[] getLogsByAppName(String appName, String domain, String serverKey) throws LogViewerException;

    public LogEvent[] getLogs(String type, String keyword, String appName, String domain, String serverKey) throws LogViewerException;

    public int logsCount(String domain, String serverKey) throws LogViewerException;

    public boolean clearLogs();

}

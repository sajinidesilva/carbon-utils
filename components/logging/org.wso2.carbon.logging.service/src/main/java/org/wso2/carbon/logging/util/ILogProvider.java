package org.wso2.carbon.logging.util;

import org.wso2.carbon.logging.service.LogViewerException;
import org.wso2.carbon.logging.service.data.LogEvent;
import org.wso2.carbon.logging.service.data.LogInfo;
import org.wso2.carbon.logging.service.data.LoggingConfig;

import javax.activation.DataHandler;

/**
 * Created by shameera on 6/24/14.
 */
public interface ILogProvider {

    /**
     * Initialize the log provider by reading the property comes with logging configuration file
     * This will be called immediate after create new instance of ILogProvider
     * @param loggingConfig
     */
    public void initLogProvider(LoggingConfig loggingConfig);

    public String[] getApplicationNames(String domain, String serverKey) throws LogViewerException;

    public LogEvent[] getApplicationLogs(String type, String keyword, String appName, String domain, String serverKey) throws LogViewerException;

    public LogEvent[] getSystemLogs() throws LogViewerException;

    public LogEvent[] getLogEvents(String domain, String serverKey) throws LogViewerException;

    public LogEvent[] getLogEvents(String appName, String domain, String serverKey) throws LogViewerException;

    public LogEvent[] searchLogEvents(String type, String keyword, String appName, String domain, String serverKey) throws LogViewerException;

    public LogInfo[] getLogInfo(String tenantDomain, String serviceName) throws LogViewerException;

    public int logsCount(String domain, String serverKey) throws LogViewerException;

    public boolean clearLogs();

    public LogInfo[] getLogsIndex(String tenantDomain, String serviceName) throws Exception;   //loggingReader

    public DataHandler downloadLogFile(String logFile, String tenantDomain, String serviceName)throws LogViewerException ;

}

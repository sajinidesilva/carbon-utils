/*
 * Copyright 2004,2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.logging.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Logger;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.logging.service.appender.LogEventAppender;
import org.wso2.carbon.logging.service.config.LoggingConfigManager;
import org.wso2.carbon.logging.service.config.ServiceConfigManager;
import org.wso2.carbon.logging.service.data.LogEvent;
import org.wso2.carbon.logging.service.data.LogInfo;
import org.wso2.carbon.logging.service.data.LoggingConfig;
import org.wso2.carbon.logging.service.data.PaginatedLogEvent;
import org.wso2.carbon.logging.service.data.PaginatedLogInfo;
import org.wso2.carbon.logging.service.provider.api.LogFileProvider;
import org.wso2.carbon.logging.service.provider.api.LogProvider;
import org.wso2.carbon.logging.service.util.LoggingUtil;
import org.wso2.carbon.utils.DataPaginator;

import javax.activation.DataHandler;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

/**
 * This is the Log Viewer service used for obtaining Log messages from locally
 * and from a remote configured syslog server.
 */
public class LogViewer {

	private static final Log log = LogFactory.getLog(LogViewer.class);
    private static LoggingConfig loggingConfig = LoggingConfigManager.loadLoggingConfiguration();
    private static LogFileProvider logFileProvider;
    private static LogProvider logProvider;

    static {
        // initiate Log provider instance
        String lpClass = loggingConfig.getLogProviderImplClassName();
        try {
            if (lpClass != null && !lpClass.equals("")) {
                Class logProviderClass = Class.forName(lpClass);
                Constructor constructor = logProviderClass.getConstructor();
                logProvider = (LogProvider) constructor.newInstance();
                logProvider.init(loggingConfig);
            } else {
                log.error("Log provider is not defined in logging configuration file");
            }
        } catch (Exception e) {
            log.error("Error while loading log provider implementation class");
        }

        // initiate log file provider instance
        String lfpClass = loggingConfig.getLogFileProviderImplClassName();
        try {
            if (lfpClass != null && ! lfpClass.equals("")) {
                Class logFileProviderClass = Class.forName(lfpClass);
                Constructor constructor = logFileProviderClass.getConstructor();
                logFileProvider = (LogFileProvider) constructor.newInstance();
                logFileProvider.init(loggingConfig);
            } else {
                log.error("Log file provider is not defined in logging configuration file");
            }
        } catch (Exception e) {
            log.error("Error while loading log file provider implementation class");
        }

    }

	public PaginatedLogInfo getPaginatedLogInfo(int pageNumber, String tenantDomain,
			String serviceName) throws LogViewerException {
		List<LogInfo> logInfoList = logFileProvider.getLogInfo(tenantDomain, serviceName);
		if (logInfoList != null && logInfoList.size() > 0) {
			// Pagination
			PaginatedLogInfo paginatedLogInfo = new PaginatedLogInfo();
			DataPaginator.doPaging(pageNumber, logInfoList, paginatedLogInfo);
			return paginatedLogInfo;
		} else {
			return null;
		}
	}

	public PaginatedLogInfo getLocalLogFiles(int pageNumber, String tenantDomain, String serverKey) throws LogViewerException {
        List<LogInfo> logInfoList = logFileProvider.getLogInfo(tenantDomain, serverKey);
        if (logInfoList != null && logInfoList.size() > 0) {
            PaginatedLogInfo paginatedLogInfo = new PaginatedLogInfo();
            DataPaginator.doPaging(pageNumber, logInfoList, paginatedLogInfo);
            return paginatedLogInfo;
        } else {
            return null;
        }
    }

	public DataHandler downloadArchivedLogFiles(String logFile, String tenantDomain, String serverKey) throws Exception {
		return logFileProvider.downloadLogFile(logFile, tenantDomain, serverKey);
	}

	 public boolean isValidTenantDomain(String tenantDomain) {
	 return LoggingUtil.isValidTenantDomain(tenantDomain);
	 }

	public String[] getServiceNames() throws LogViewerException {
		return ServiceConfigManager.getServiceNames();
	}

	
	 public boolean isManager() {
	    return LoggingUtil.isManager();
	 }

    public boolean isValidTenant(String tenantDomain) {
        return LoggingUtil.isValidTenant(tenantDomain);
    }

	public int getLineNumbers(String logFile) throws Exception {
		return LoggingUtil.getLineNumbers(logFile);
	}

	public String[] getLogLinesFromFile(String logFile, int maxLogs, int start, int end)
			throws LogViewerException {
		return LoggingUtil.getLogLinesFromFile(logFile, maxLogs, start, end);
	}

	public String[] getApplicationNames(String tenantDomain, String serverKey) throws LogViewerException {
        List<String> appNameList = logProvider.getApplicationNames(tenantDomain, serverKey);
        return appNameList.toArray(new String[appNameList.size()]);
    }

	public boolean isLogEventReciverConfigured() {
		Logger rootLogger = Logger.getRootLogger();
		LogEventAppender logger = (LogEventAppender) rootLogger.getAppender("LOGEVENT");
		if (logger != null) {
			return true;
		} else {
			return false;
		}
	}

	public boolean isFileAppenderConfiguredForST() {
		Logger rootLogger = Logger.getRootLogger();
		DailyRollingFileAppender logger = (DailyRollingFileAppender) rootLogger
				.getAppender("CARBON_LOGFILE");
		if (logger != null
				&& CarbonContext.getCurrentContext().getTenantId() == MultitenantConstants.SUPER_TENANT_ID) {
			return true;
		} else {
			return false;
		}
	}

	public LogEvent[] getAllSystemLogs() throws LogViewerException {
        List<LogEvent> logEventList = logProvider.getSystemLogs();
        return logEventList.toArray(new LogEvent[logEventList.size()]);
    }

    public PaginatedLogEvent getPaginatedLogEvents(int pageNumber, String type, String keyword, String tenantDomain, String serverKey)
            throws LogViewerException {

        List<LogEvent> logMsgList = logProvider.getLogs(type, keyword, null, tenantDomain, serverKey);
        if (logMsgList != null && logMsgList.size() > 0) {
            PaginatedLogEvent paginatedLogEvent = new PaginatedLogEvent();
            DataPaginator.doPaging(pageNumber, logMsgList, paginatedLogEvent);
            return paginatedLogEvent;
        } else {
            return null;
        }

    }

	public int getNoOfLogEvents(String tenantDomain, String serverKey) throws LogViewerException {
        return logProvider.logsCount(tenantDomain, serverKey);
    }

	public PaginatedLogEvent getPaginatedApplicationLogEvents(int pageNumber, String type,
			String keyword, String applicationName, String tenantDomain, String serverKey) throws Exception {
        List<LogEvent> logMsgList = logProvider.getLogs(type, keyword, applicationName, tenantDomain, serverKey);
        if (logMsgList != null  && logMsgList.size() > 0) {
            PaginatedLogEvent paginatedLogEvent = new PaginatedLogEvent();
            DataPaginator.doPaging(pageNumber, logMsgList, paginatedLogEvent);
            return paginatedLogEvent;
        } else {
            return null;
        }
    }

    public LogEvent[] getLogs(String type, String keyword, String tenantDomain,
                                  String serverKey) throws LogViewerException {
        List<LogEvent> logEventList = logProvider.getLogs(type, keyword, null, tenantDomain, serverKey);
        return logEventList.toArray(new LogEvent[logEventList.size()]);
    }

    public LogEvent[] getApplicationLogs(String type, String keyword, String appName, String tenantDomain,
                                        String serverKey) throws LogViewerException {
        List<LogEvent> logEventList = logProvider.getLogs(type, keyword, appName, tenantDomain, serverKey);
        return logEventList.toArray(new LogEvent[logEventList.size()]);
    }

	public boolean clearLogs() {
        return logProvider.clearLogs();
    }
}

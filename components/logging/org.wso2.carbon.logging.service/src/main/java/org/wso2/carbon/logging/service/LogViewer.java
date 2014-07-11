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
import org.wso2.carbon.logging.appender.LogEventAppender;
import org.wso2.carbon.logging.config.LoggingConfigManager;
import org.wso2.carbon.logging.config.ServiceConfigManager;
import org.wso2.carbon.logging.service.data.LogEvent;
import org.wso2.carbon.logging.service.data.LogInfo;
import org.wso2.carbon.logging.service.data.LoggingConfig;
import org.wso2.carbon.logging.service.data.PaginatedLogEvent;
import org.wso2.carbon.logging.service.data.PaginatedLogInfo;
import org.wso2.carbon.logging.provider.api.ILogFileProvider;
import org.wso2.carbon.logging.provider.api.ILogProvider;
import org.wso2.carbon.logging.util.LoggingUtil;
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
    private static ILogFileProvider logFileProvider;
    private static ILogProvider logProvider;

    static {
        // initiate Log provider instance
        String lpClass = loggingConfig.getLogProviderImplClassName();
        try {
            if (lpClass != null && !lpClass.equals("")) {
                Class logProviderClass = Class.forName(lpClass);
                Constructor constructor = logProviderClass.getConstructor();
                logProvider = (ILogProvider) constructor.newInstance();
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
                logFileProvider = (ILogFileProvider) constructor.newInstance();
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
		LogInfo[] logs = logFileProvider.getLogInfo(tenantDomain, serviceName);
		if (logs != null) {
			List<LogInfo> logInfoList = Arrays.asList(logs);
			// Pagination
			PaginatedLogInfo paginatedLogInfo = new PaginatedLogInfo();
			DataPaginator.doPaging(pageNumber, logInfoList, paginatedLogInfo);
			return paginatedLogInfo;
		} else {
			return null;
		}
	}

	public PaginatedLogInfo getLocalLogFiles(int pageNumber, String domain, String serverKey) throws LogViewerException {
        LogInfo[] logs = logFileProvider.getLogInfo(domain, serverKey);
        if (logs != null) {
            List<LogInfo> logInfoList = Arrays.asList(logs);
            PaginatedLogInfo paginatedLogInfo = new PaginatedLogInfo();
            DataPaginator.doPaging(pageNumber, logInfoList, paginatedLogInfo);
            return paginatedLogInfo;
        } else {
            return null;
        }
    }

	public DataHandler downloadArchivedLogFiles(String logFile, String domain, String serverKey) throws Exception {
		return logFileProvider.downloadLogFile(logFile, domain, serverKey);
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

    public boolean isValidTenant(String domain) {
        return LoggingUtil.isValidTenant(domain);
    }

	public int getLineNumbers(String logFile) throws Exception {
		return LoggingUtil.getLineNumbers(logFile);
	}

	public String[] getLogLinesFromFile(String logFile, int maxLogs, int start, int end)
			throws LogViewerException {
		return LoggingUtil.getLogLinesFromFile(logFile, maxLogs, start, end);
	}

	public String[] getApplicationNames(String domain, String serverKey) throws LogViewerException {
        return logProvider.getApplicationNames(domain, serverKey);
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
        return logProvider.getSystemLogs();
	}

    public PaginatedLogEvent getPaginatedLogEvents(int pageNumber, String type, String keyword, String domain, String serverKey)
            throws LogViewerException {

        LogEvent[] list = logProvider.getLogs(type, keyword, null, domain, serverKey);
        if (list != null) {
            List<LogEvent> logMsgList = Arrays.asList(list);
            PaginatedLogEvent paginatedLogEvent = new PaginatedLogEvent();
            DataPaginator.doPaging(pageNumber, logMsgList, paginatedLogEvent);
            return paginatedLogEvent;
        } else {
            return null;
        }

    }

	public int getNoOfLogEvents(String domain, String serverKey) throws LogViewerException {
        return logProvider.logsCount(domain, serverKey);
    }

	public PaginatedLogEvent getPaginatedApplicationLogEvents(int pageNumber, String type,
			String keyword, String applicationName, String domain, String serverKey) throws Exception {
        LogEvent[] list = logProvider.getLogs(type, keyword, applicationName, domain, serverKey);
        if (list != null) {
            List<LogEvent> logMsgList = Arrays.asList(list);
            PaginatedLogEvent paginatedLogEvent = new PaginatedLogEvent();
            DataPaginator.doPaging(pageNumber, logMsgList, paginatedLogEvent);
            return paginatedLogEvent;
        } else {
            return null;
        }
    }

    public LogEvent[] getLogs(String type, String keyword, String domain, String serverKey) throws LogViewerException {
        return logProvider.getLogs(type, keyword, null, domain, serverKey);
    }

    public LogEvent[] getApplicationLogs(String type, String keyword, String appName, String domain, String serverKey) throws LogViewerException {
        return logProvider.getLogs(type, keyword, appName, domain, serverKey);
    }

	public boolean clearLogs() {
        return logProvider.clearLogs();
        /*Appender appender = Logger.getRootLogger().getAppender(
				LoggingConstants.WSO2CARBON_MEMORY_APPENDER);
		if (appender instanceof CarbonMemoryAppender) {
			try {
				CarbonMemoryAppender memoryAppender = (CarbonMemoryAppender) appender;
				if (memoryAppender.getCircularQueue() != null) {
					memoryAppender.getCircularQueue().clear();
				}
				return true;
			} catch (Exception e) {
				return false;
			}
		} else {
			return false;
		}*/
    }
}

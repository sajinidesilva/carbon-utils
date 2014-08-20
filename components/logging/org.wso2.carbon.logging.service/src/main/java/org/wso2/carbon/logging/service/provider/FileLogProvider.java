/*
*  Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.logging.service.provider;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.base.ServerConfiguration;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.logging.service.LogViewerException;
import org.wso2.carbon.logging.service.config.ServiceConfigManager;
import org.wso2.carbon.logging.service.config.SyslogConfigManager;
import org.wso2.carbon.logging.service.config.SyslogConfiguration;
import org.wso2.carbon.logging.service.data.LogInfo;
import org.wso2.carbon.logging.service.data.LoggingConfig;
import org.wso2.carbon.logging.service.data.SyslogData;
import org.wso2.carbon.logging.service.provider.api.LogFileProvider;
import org.wso2.carbon.logging.service.util.LoggingConstants;
import org.wso2.carbon.logging.service.util.LoggingUtil;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import javax.activation.DataHandler;
import javax.mail.util.ByteArrayDataSource;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class FileLogProvider implements LogFileProvider {


    private static Log log = LogFactory.getLog(FileLogProvider.class);

    /**
     * Initialize the log provider by reading the property comes with logging configuration file
     * This will be called immediate after create new instance of ILogProvider
     *
     * @param loggingConfig -
     */
    @Override
    public void init(LoggingConfig loggingConfig) {
    }

    @Override
    public List<LogInfo> getLogInfo(String tenantDomain, String serverKey) throws LogViewerException {
        String folderPath = CarbonUtils.getCarbonLogsPath();
        List<LogInfo> logs = new ArrayList<LogInfo>();
        LogInfo log;
        if ((((tenantDomain == null || tenantDomain.equals("")) && isSuperTenantUser()) ||
                (tenantDomain != null && tenantDomain.equalsIgnoreCase(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME))) &&
                (serverKey == null || serverKey.equals("") || serverKey.equalsIgnoreCase(getCurrentServerName()))) {

            File folder = new File(folderPath);
            FileFilter fileFilter = new WildcardFileFilter(LoggingConstants.RegexPatterns.LOCAL_CARBON_LOG_PATTERN);
            File[] listOfFiles = folder.listFiles(fileFilter);
            for (File file : listOfFiles) {
                String filename = file.getName();
                String fileDates[] = filename.split(LoggingConstants.RegexPatterns.LOG_FILE_DATE_SEPARATOR);
                String filePath = CarbonUtils.getCarbonLogsPath() + LoggingConstants.URL_SEPARATOR + filename;
                File logfile = new File(filePath);
                if (fileDates.length == 2) {
                    log = new LogInfo(filename, fileDates[1], getFileSize(logfile));
                } else {
                    log = new LogInfo(filename, LoggingConstants.RegexPatterns.CURRENT_LOG,
                            getFileSize(logfile));
                }
                logs.add(log);
            }
        }
        return getSortedLogInfo(logs);
    }

    @Override
    public DataHandler downloadLogFile(String logFile, String tenantDomain, String serverKey) throws LogViewerException {
        InputStream is;
        int tenantId = LoggingUtil.getTenantIdForDomain(tenantDomain);
        try {
            is = getInputStream(logFile, tenantId, serverKey);
        } catch (LogViewerException e) {
            throw new LogViewerException("Cannot read InputStream from the file " + logFile, e);
        }
        try {
            ByteArrayDataSource bytArrayDS = new ByteArrayDataSource(is, "application/zip");
            return new DataHandler(bytArrayDS);
        } catch (IOException e) {
            throw new LogViewerException("Cannot read file size from the " + logFile, e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                log.error("Error while closing inputStream of log file");
            }
        }
    }

    private boolean isSyslogOn() {
        SyslogConfiguration syslogConfig = SyslogConfigManager.loadSyslogConfiguration();
        return syslogConfig.isSyslogOn();
    }


    /**
     * Get Log file index from log collector server.
     *
     * @param tenantId  -
     * @param serverKey -
     * @return LogInfo {Log Name, Date, Size}
     * @throws org.wso2.carbon.logging.service.LogViewerException
     */
    private List<LogInfo> getLogInfo(int tenantId, String serverKey) throws LogViewerException {
        InputStream logStream;
        try {
            logStream = getLogDataStream("", tenantId, serverKey);
        } catch (HttpException e) {
            throw new LogViewerException("Cannot establish the connection to the syslog server", e);
        } catch (IOException e) {
            throw new LogViewerException("Cannot find the specified file location to the log file",
                    e);
        } catch (Exception e) {
            throw new LogViewerException("Cannot find the specified file location to the log file",
                    e);
        }
        BufferedReader dataInput = new BufferedReader(new InputStreamReader(logStream));
        String line;
        List<LogInfo> logs = new ArrayList<LogInfo>();
        Pattern pattern = Pattern.compile(LoggingConstants.RegexPatterns.SYS_LOG_FILE_NAME_PATTERN);
        try {
            while ((line = dataInput.readLine()) != null) {
                String fileNameLinks[] = line
                        .split(LoggingConstants.RegexPatterns.LINK_SEPARATOR_PATTERN);
                String fileDates[] = line
                        .split((LoggingConstants.RegexPatterns.SYSLOG_DATE_SEPARATOR_PATTERN));
                String dates[] = null;
                String sizes[] = null;
                if (fileDates.length == 3) {
                    dates = fileDates[1]
                            .split(LoggingConstants.RegexPatterns.COLUMN_SEPARATOR_PATTERN);
                    sizes = fileDates[2]
                            .split(LoggingConstants.RegexPatterns.COLUMN_SEPARATOR_PATTERN);
                }
                if (fileNameLinks.length == 2) {
                    String logFileName[] = fileNameLinks[1]
                            .split(LoggingConstants.RegexPatterns.GT_PATTARN);
                    Matcher matcher = pattern.matcher(logFileName[0]);
                    if (matcher.find()) {
                        if (dates != null) {
                            String logName = logFileName[0].replace(
                                    LoggingConstants.RegexPatterns.BACK_SLASH_PATTERN, "");
                            logName = logName.replaceAll("%20", " ");
                            LogInfo log = new LogInfo(logName, dates[0], sizes[0]);
                            logs.add(log);
                        }
                    }
                }
            }
            dataInput.close();
        } catch (IOException e) {
            throw new LogViewerException("Cannot find the specified file location to the log file", e);
        }
        return getSortedLogInfo(logs);
    }

    private List<LogInfo> getSortedLogInfo(List<LogInfo> logs) {
        if (logs == null || logs.size() == 0) {
            return getDefaultLogInfo();
        } else {
            Collections.sort(logs, new Comparator<Object>() {
                public int compare(Object o1, Object o2) {
                    LogInfo log1 = (LogInfo) o1;
                    LogInfo log2 = (LogInfo) o2;
                    return log1.getLogName().compareToIgnoreCase(log2.getLogName());
                }

            });
            return logs;
        }
    }

    private InputStream getLogDataStream(String logFile, int tenantId, String productName)
            throws Exception {
        SyslogData syslogData = getSyslogData();
        String url;
        // manager can view all the products tenant log information
        url = getLogsServerURLforTenantService(syslogData.getUrl(), logFile, tenantId, productName);
        String password = syslogData.getPassword();
        String userName = syslogData.getUserName();
        int port = Integer.parseInt(syslogData.getPort());
        String realm = syslogData.getRealm();
        URI uri = new URI(url);
        String host = uri.getHost();
        HttpClient client = new HttpClient();
        client.getState().setCredentials(new AuthScope(host, port, realm),
                new UsernamePasswordCredentials(userName, password));
        GetMethod get = new GetMethod(url);
        get.setDoAuthentication(true);
        client.executeMethod(get);
        return get.getResponseBodyAsStream();
    }

    private SyslogData getSyslogData() throws Exception {
        return LoggingUtil.getSyslogData();
    }

    /*
     * get logs from the local file system.
     */
    private List<LogInfo> getLocalLogInfo(String tenantDomain, String serverKey) {
        String folderPath = CarbonUtils.getCarbonLogsPath();
        List<LogInfo> logs = new ArrayList<LogInfo>();
        LogInfo log;
        if ((((tenantDomain.equals("")) && isSuperTenantUser()) || tenantDomain.equalsIgnoreCase(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME))
                && (serverKey == null || serverKey.equals("") || serverKey.equalsIgnoreCase(getCurrentServerName()))) {

            File folder = new File(folderPath);
            FileFilter fileFilter = new WildcardFileFilter(LoggingConstants.RegexPatterns.LOCAL_CARBON_LOG_PATTERN);
            File[] listOfFiles = folder.listFiles(fileFilter);
            for (File file : listOfFiles) {
                String filename = file.getName();
                String fileDates[] = filename.split(LoggingConstants.RegexPatterns.LOG_FILE_DATE_SEPARATOR);
                String filePath = CarbonUtils.getCarbonLogsPath() + LoggingConstants.URL_SEPARATOR + filename;
                File logfile = new File(filePath);
                if (fileDates.length == 2) {
                    log = new LogInfo(filename, fileDates[1], getFileSize(logfile));
                } else {
                    log = new LogInfo(filename, LoggingConstants.RegexPatterns.CURRENT_LOG, getFileSize(logfile));
                }
                logs.add(log);
            }
        }
        return getSortedLogInfo(logs);

    }

    private String getLogsServerURLforTenantService(String syslogServerURL, String logFile,
                                                    int tenantId, String serverKey) throws LogViewerException {
        String serverUrl;
        String lastChar = String.valueOf(syslogServerURL.charAt(syslogServerURL.length() - 1));
        if (lastChar.equals(LoggingConstants.URL_SEPARATOR)) { // http://my.log.server/logs/stratos/
            syslogServerURL = syslogServerURL.substring(0, syslogServerURL.length() - 1);
        }
        if (isSuperTenantUser()) { // ST can view tenant specific log files.
            if (isManager()) { // manager can view different services log
                // messages.
                if (serverKey != null && serverKey.length() > 0) {
                    serverUrl = syslogServerURL + LoggingConstants.URL_SEPARATOR + tenantId
                            + LoggingConstants.URL_SEPARATOR + serverKey + LoggingConstants.URL_SEPARATOR;
                } else {
                    serverUrl = syslogServerURL + LoggingConstants.URL_SEPARATOR + tenantId
                            + LoggingConstants.URL_SEPARATOR + LoggingConstants.WSO2_STRATOS_MANAGER
                            + LoggingConstants.URL_SEPARATOR;
                }
                try {
                    if (!isStratosService()) { // stand-alone apps.
                        serverUrl = syslogServerURL + LoggingConstants.URL_SEPARATOR + tenantId
                                + LoggingConstants.URL_SEPARATOR
                                + ServerConfiguration.getInstance().getFirstProperty("ServerKey")
                                + LoggingConstants.URL_SEPARATOR;
                    }
                } catch (LogViewerException e) {
                    throw new LogViewerException("Cannot get log ServerURL for Tenant Service", e);
                }
            } else { // for other stratos services can view only their relevant
                // logs.
                serverUrl = syslogServerURL + LoggingConstants.URL_SEPARATOR + tenantId
                        + LoggingConstants.URL_SEPARATOR
                        + ServerConfiguration.getInstance().getFirstProperty("ServerKey")
                        + LoggingConstants.URL_SEPARATOR;
            }

        } else { // tenant level logging
            if (isManager()) {
                if (serverKey != null && serverKey.length() > 0) {
                    serverUrl = syslogServerURL + LoggingConstants.URL_SEPARATOR
                            + CarbonContext.getThreadLocalCarbonContext().getTenantId()
                            + LoggingConstants.URL_SEPARATOR + serverKey
                            + LoggingConstants.URL_SEPARATOR;
                } else {
                    serverUrl = syslogServerURL + LoggingConstants.URL_SEPARATOR
                            + CarbonContext.getThreadLocalCarbonContext().getTenantId()
                            + LoggingConstants.URL_SEPARATOR
                            + LoggingConstants.WSO2_STRATOS_MANAGER
                            + LoggingConstants.URL_SEPARATOR;
                }
            } else {
                serverUrl = syslogServerURL + LoggingConstants.URL_SEPARATOR
                        + CarbonContext.getThreadLocalCarbonContext().getTenantId()
                        + LoggingConstants.URL_SEPARATOR
                        + ServerConfiguration.getInstance().getFirstProperty("ServerKey")
                        + LoggingConstants.URL_SEPARATOR;
            }
        }
        serverUrl = serverUrl.replaceAll("\\s", "%20");
        logFile = logFile.replaceAll("\\s", "%20");
        return serverUrl + logFile;
    }

    public boolean isStratosService() throws LogViewerException {
        String serverKey = ServerConfiguration.getInstance().getFirstProperty("ServerKey");
        return ServiceConfigManager.isStratosService(serverKey);
    }

    public boolean isManager() {
        return LoggingConstants.WSO2_STRATOS_MANAGER.equalsIgnoreCase(ServerConfiguration.getInstance()
                .getFirstProperty("ServerKey"));
    }

    public boolean isSuperTenantUser() {
        CarbonContext carbonContext = CarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        return tenantId == MultitenantConstants.SUPER_TENANT_ID;
    }

    private String getCurrentServerName() {
        return ServerConfiguration.getInstance().getFirstProperty("ServerKey");
    }

    private String getFileSize(File file) {
        long bytes = file.length();
        int unit = 1024;
        if (bytes < unit)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    private InputStream getInputStream(String logFile, int tenantId, String serverKey)
            throws LogViewerException {
        InputStream inputStream;
        try {
            if (isSyslogOn()) {
                inputStream = getLogDataStream(logFile, tenantId, serverKey);
            } else {
                if (isSuperTenantUser()) {
                    inputStream = getLocalInputStream(logFile);
                } else {
                    throw new LogViewerException("Syslog Properties are not properly configured");
                }
            }
            return inputStream;
        } catch (Exception e) {
            throw new LogViewerException("Error getting the file inputstream", e);
        }

    }

    private InputStream getLocalInputStream(String logFile) throws FileNotFoundException {
        logFile = logFile.substring(logFile.lastIndexOf(System.getProperty("file.separator")) + 1);
        String fileName = CarbonUtils.getCarbonLogsPath() + LoggingConstants.URL_SEPARATOR
                + logFile;
        return new BufferedInputStream(new FileInputStream(fileName));
    }


    private List<LogInfo> getDefaultLogInfo() {
        List<LogInfo> defaultLoginfoList = new ArrayList<LogInfo>();
        defaultLoginfoList.add(new LogInfo("NO_LOG_FILES",
                "---", "---"));
        return defaultLoginfoList;
    }

}

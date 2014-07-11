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
package org.wso2.carbon.logging.util;

import me.prettyprint.cassandra.model.ConfigurableConsistencyLevel;
import me.prettyprint.cassandra.serializers.BytesArraySerializer;
import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.cassandra.service.CassandraHostConfigurator;
import me.prettyprint.hector.api.Cluster;
import me.prettyprint.hector.api.HConsistencyLevel;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hector.api.beans.OrderedRows;
import me.prettyprint.hector.api.beans.Row;
import me.prettyprint.hector.api.ddl.ColumnFamilyDefinition;
import me.prettyprint.hector.api.ddl.KeyspaceDefinition;
import me.prettyprint.hector.api.factory.HFactory;
import me.prettyprint.hector.api.query.QueryResult;
import me.prettyprint.hector.api.query.RangeSlicesQuery;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.base.ServerConfiguration;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.logging.api.ILogProvider;
import org.wso2.carbon.logging.config.LoggingConfigManager;
import org.wso2.carbon.logging.service.LogViewerException;
import org.wso2.carbon.logging.service.data.LogEvent;
import org.wso2.carbon.logging.service.data.LoggingConfig;
import org.wso2.carbon.logging.sort.LogEventSorter;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by shameera on 6/24/14.
 */
public class CassandraLogProvider implements ILogProvider {

    private static Log log = LogFactory.getLog(CassandraLogReader.class);
    private final static StringSerializer stringSerializer = StringSerializer.get();
    private static final int MAX_NO_OF_EVENTS = 40000;
    private ExecutorService executorService = Executors.newFixedThreadPool(1);

    /**
     * Initialize the log provider by reading the property comes with logging configuration file
     * This will be called immediate after create new instance of ILogProvider
     *
     * @param loggingConfig
     */
    @Override
    public void init(LoggingConfig loggingConfig) {

    }

    @Override
    public String[] getApplicationNames(String domain, String serverKey) throws LogViewerException {
        List<String> appList = new ArrayList<String>();
        LogEvent allLogs[];
        try {
            allLogs = getAllLogs(domain, serverKey);
        } catch (LogViewerException e) {
            log.error("Error retrieving application logs", e);
            throw new LogViewerException("Error retrieving application logs", e);
        }
        for (LogEvent event : allLogs) {
            if (event.getAppName() != null && !event.getAppName().equals("")  && !event.getAppName().equals("NA")
                    && !LoggingUtil.isAdmingService(event.getAppName()) && !appList.contains(event.getAppName()) &&
                    !event.getAppName().equals("STRATOS_ROOT")) {
                appList.add(event.getAppName());
            }
        }
        return getSortedApplicationNames(appList);
    }

/*    public LogEvent[] getApplicationLogs(String type, String keyword, String appName, String domain, String serverKey) throws LogViewerException {
        LogEvent[] events = getSortedLogsFromCassandra(appName, domain, serverKey);
        if (keyword == null || keyword.equals("")) {
            // keyword is null
            if (type == null || type.equals("") || type.equalsIgnoreCase("ALL")) {
                return events;
            } else {
                // type is NOT null and NOT equal to ALL Application Name is not
                // needed
                return getLogsForType(events, type);
            }
        } else {
            // keyword is NOT null
            if (type == null || type.equals("")) {
                // type is null
                return getLogsForKey(events, keyword);
            } else {
                // type is NOT null and keyword is NOT null, but type can be
                // equal to ALL
                return searchLog(events, type, keyword);
            }
        }
    }*/

    @Override
    public LogEvent[] getSystemLogs() throws LogViewerException {
        return new LogEvent[0];
    }

    @Override
    public LogEvent[] getAllLogs(String domain, String serverKey) throws LogViewerException {

        // int tenantId = getCurrentTenantId(tenantDomain);
        // serviceName = getCurrentServerName(serviceName);
        Keyspace currKeyspace = getCurrentCassandraKeyspace();
        LoggingConfig config;
        try {
            config = LoggingConfigManager.loadLoggingConfiguration();
        } catch (Exception e) {
            throw new LogViewerException("Cannot load cassandra configuration", e);
        }
        String colFamily = getCFName(config, domain, serverKey);
        if (!isCFExsist(config.getLogProviderProperty(CassandraConfigProperties.KEYSPACE), colFamily)) {
            return new LogEvent[0];
        }
        RangeSlicesQuery<String, String, byte[]> rangeSlicesQuery = HFactory
                .createRangeSlicesQuery(currKeyspace, stringSerializer, stringSerializer,
                        BytesArraySerializer.get());
        rangeSlicesQuery.setColumnFamily(colFamily);
        rangeSlicesQuery.setRowCount(MAX_NO_OF_EVENTS);

        rangeSlicesQuery.setColumnNames(LoggingConstants.HColumn.TENANT_ID,
                LoggingConstants.HColumn.SERVER_NAME, LoggingConstants.HColumn.APP_NAME,
                LoggingConstants.HColumn.LOG_TIME, LoggingConstants.HColumn.LOGGER,
                LoggingConstants.HColumn.PRIORITY, LoggingConstants.HColumn.MESSAGE,
                LoggingConstants.HColumn.IP, LoggingConstants.HColumn.STACKTRACE,
                LoggingConstants.HColumn.INSTANCE);
        rangeSlicesQuery.setRange("", "", false, 30);

        List<LogEvent> resultList = getLoggingResultList(rangeSlicesQuery);
        return resultList.toArray(new LogEvent[resultList.size()]);
    }

    @Override
    public LogEvent[] getLogsByAppName(String appName, String domain, String serverKey) throws LogViewerException {
        return getAllLogs(domain, serverKey);
    }

    @Override
    public LogEvent[] getLogs(String type, String keyword, String appName, String domain, String serverKey) throws LogViewerException {
        LogEvent[] events = getSortedLogsFromCassandra("", domain, serverKey);
        if (keyword == null || keyword.equals("")) {
            // keyword is null
            if (type == null || type.equals("") || type.equalsIgnoreCase("ALL")) {
                return events;
            } else {
                // type is NOT null and NOT equal to ALL Application Name is not
                // needed
                return getLogsForType(events, type);
            }
        } else {
            // keyword is NOT null
            if (type == null || type.equals("")) {
                // type is null
                return getLogsForKey(events, keyword);
            } else {
                // type is NOT null and keyword is NOT null, but type can be
                // equal to ALL
                return searchLog(events, type, keyword);
            }
        }
    }

    @Override
    public int logsCount(String domain, String serverKey) throws LogViewerException {
        Keyspace currKeyspace = getCurrentCassandraKeyspace();
        LoggingConfig config;
        try {
            config = LoggingConfigManager.loadLoggingConfiguration();
        } catch (Exception e) {
            throw new LogViewerException("Cannot load cassandra configuration", e);
        }
        String colFamily = getCFName(config, domain, serverKey);

        RangeSlicesQuery<String, String, String> rangeSlicesQuery = HFactory
                .createRangeSlicesQuery(currKeyspace, stringSerializer, stringSerializer,
                        stringSerializer);
        rangeSlicesQuery.setColumnFamily(colFamily);
        rangeSlicesQuery.setKeys("", "");
        rangeSlicesQuery.setRowCount(Integer.MAX_VALUE);
        rangeSlicesQuery.setReturnKeysOnly();
        QueryResult<OrderedRows<String, String, String>> result = rangeSlicesQuery.execute();
        return result.get().getCount();
    }

    @Override
    public boolean clearLogs() {
        return false;
    }

    private Cluster retrieveCassandraCluster(String clusterName, String connectionUrl,
                                             Map<String, String> credentials) throws LogViewerException {
        LoggingConfig config;
        try {
            config = LoggingConfigManager.loadLoggingConfiguration();
        } catch (Exception e) {
            throw new LogViewerException("Cannot read the log config file", e);
        }
        CassandraHostConfigurator hostConfigurator = new CassandraHostConfigurator(connectionUrl);
        String prop = config.getLogProviderProperty(CassandraConfigProperties.RETRY_DOWNED_HOSTS_ENABLE);
        hostConfigurator.setRetryDownedHosts((prop == null || prop.equals("")) ? false : Boolean.valueOf(prop));
        prop = config.getLogProviderProperty(CassandraConfigProperties.RETRY_DOWNED_HOSTS_QUEUE);
        hostConfigurator.setRetryDownedHostsQueueSize((prop == null || prop.equals("")) ? -1 : Integer.valueOf(prop));
        prop = config.getLogProviderProperty(CassandraConfigProperties.AUTO_DISCOVERY_DELAY);
        hostConfigurator.setAutoDiscoverHosts((prop == null || prop.equals("")) ? false : Boolean.valueOf(prop));
        prop = config.getLogProviderProperty(CassandraConfigProperties.AUTO_DISCOVERY_DELAY);
        hostConfigurator.setAutoDiscoveryDelayInSeconds((prop == null || prop.equals("")) ? -1 : Integer.valueOf(prop));
        Cluster cluster = HFactory.createCluster(clusterName, hostConfigurator, credentials);
        return cluster;
    }

    private Cluster getCluster(String clusterName, String connectionUrl,
                               Map<String, String> credentials) throws LogViewerException {
        //removed getting cluster from session, since we dont aware of BAM shutting down time
        return retrieveCassandraCluster(clusterName, connectionUrl, credentials);
    }

    private Keyspace getCurrentCassandraKeyspace() throws LogViewerException {
        LoggingConfig config;
        try {
            config = LoggingConfigManager.loadLoggingConfiguration();
        } catch (Exception e) {
            throw new LogViewerException("Cannot read the log config file", e);
        }
        String keySpaceName = config.getLogProviderProperty(CassandraConfigProperties.KEYSPACE);
        String consistencyLevel = config.getLogProviderProperty(CassandraConfigProperties.CONSISTENCY_LEVEL);
        Cluster cluster;
        cluster = getCurrentCassandraCluster();
        // Create a customized Consistency Level
        ConfigurableConsistencyLevel configurableConsistencyLevel = new ConfigurableConsistencyLevel();
        configurableConsistencyLevel.setDefaultReadConsistencyLevel(HConsistencyLevel.valueOf(consistencyLevel));
        return HFactory.createKeyspace(keySpaceName, cluster, configurableConsistencyLevel);
    }

    private Cluster getCurrentCassandraCluster() throws LogViewerException {
        LoggingConfig config;
        try {
            config = LoggingConfigManager.loadLoggingConfiguration();
        } catch (Exception e) {
            throw new LogViewerException("Cannot read the log config file", e);
        }
        String connectionUrl = config.getLogProviderProperty(CassandraConfigProperties.URL);
        String userName = config.getLogProviderProperty(CassandraConfigProperties.USER_NAME);
        String password = config.getLogProviderProperty(CassandraConfigProperties.PASSWORD);
        String clusterName = config.getLogProviderProperty(CassandraConfigProperties.CLUSTER);
        Map<String, String> credentials = new HashMap<String, String>();
        credentials.put(LoggingConstants.USERNAME_KEY, userName);
        credentials.put(LoggingConstants.PASSWORD_KEY, password);
        return getCluster(clusterName, connectionUrl, credentials);
    }



    private byte[] longToByteArray(long data) {
        return new byte[] { (byte) ((data >> 56) & 0xff), (byte) ((data >> 48) & 0xff),
                (byte) ((data >> 40) & 0xff), (byte) ((data >> 32) & 0xff),
                (byte) ((data >> 24) & 0xff), (byte) ((data >> 16) & 0xff),
                (byte) ((data >> 8) & 0xff), (byte) ((data >> 0) & 0xff), };

    }

    private String convertByteToString(byte[] array) {
        return new String(array);
    }

    private String convertLongToString(Long longval) {
        Date date = new Date(longval);
        DateFormat formatter = new SimpleDateFormat(LoggingConstants.DATE_TIME_FORMATTER);
        String formattedDate = formatter.format(date);
        return formattedDate;
    }

    private long convertByteToLong(byte[] array, int offset) {
        return ((long) (array[offset] & 0xff) << 56) | ((long) (array[offset + 1] & 0xff) << 48)
                | ((long) (array[offset + 2] & 0xff) << 40)
                | ((long) (array[offset + 3] & 0xff) << 32)
                | ((long) (array[offset + 4] & 0xff) << 24)
                | ((long) (array[offset + 5] & 0xff) << 16)
                | ((long) (array[offset + 6] & 0xff) << 8) | ((long) (array[offset + 7] & 0xff));

    }

    private List<LogEvent> getLoggingResultList(
            RangeSlicesQuery<String, String, byte[]> rangeSlicesQuery) {
        List<LogEvent> resultList = new ArrayList<LogEvent>();
        QueryResult<OrderedRows<String, String, byte[]>> result = rangeSlicesQuery.execute();
        for (Row<String, String, byte[]> row : result.get().getList()) {
            LogEvent event = new LogEvent();
            event.setKey(row.getKey());
            for (HColumn<String, byte[]> hc : row.getColumnSlice().getColumns()) {
                if (hc.getName().equals(LoggingConstants.HColumn.TENANT_ID)) {
                    event.setTenantId(convertByteToString(hc.getValue()));
                    continue;
                }
                if (hc.getName().equals(LoggingConstants.HColumn.SERVER_NAME)) {
                    event.setServerName(convertByteToString(hc.getValue()));
                    continue;
                }
                if (hc.getName().equals(LoggingConstants.HColumn.APP_NAME)) {
                    event.setAppName(convertByteToString(hc.getValue()));
                    continue;
                }
                if (hc.getName().equals(LoggingConstants.HColumn.LOG_TIME)) {
                    event.setLogTime(convertLongToString(convertByteToLong(hc.getValue(), 0)));
                    continue;
                }
                if (hc.getName().equals(LoggingConstants.HColumn.LOGGER)) {
                    event.setLogger(convertByteToString(hc.getValue()));
                    continue;
                }
                if (hc.getName().equals(LoggingConstants.HColumn.PRIORITY)) {
                    event.setPriority(convertByteToString(hc.getValue()));
                    continue;
                }
                if (hc.getName().equals(LoggingConstants.HColumn.MESSAGE)) {
                    event.setMessage(convertByteToString(hc.getValue()));
                    continue;
                }
                if (hc.getName().equals(LoggingConstants.HColumn.IP)) {
                    event.setIp(convertByteToString(hc.getValue()));
                    continue;
                }
                if (hc.getName().equals(LoggingConstants.HColumn.STACKTRACE)) {
                    event.setStacktrace(convertByteToString(hc.getValue()));
                    continue;
                }
                if (hc.getName().equals(LoggingConstants.HColumn.INSTANCE)) {
                    event.setIp(convertByteToString(hc.getValue()));
                    continue;
                }

            }
            resultList.add(event);
        }
        return resultList;
    }

    private String getCurrentServerName() {
        String serverName = ServerConfiguration.getInstance().getFirstProperty("ServerKey");
        return serverName;
    }

    private String getCurrentDate() {
        Date cirrDate = new Date();
        DateFormat formatter = new SimpleDateFormat(LoggingConstants.DATE_FORMATTER);
        String formattedDate = formatter.format(cirrDate);
        return formattedDate.replace("-", "_");
    }

    private String getCFName(LoggingConfig config, String domain, String serverKey) {
        int tenantId = MultitenantConstants.SUPER_TENANT_ID;
        String serverName = "";
        if(domain == null || domain.equals("")) {
            tenantId  = CarbonContext.getCurrentContext().getTenantId();
        } else {
            try {
                tenantId = LoggingUtil.getTenantIdForDomain(domain);
            } catch (LogViewerException e) {

            }
        }
        String currTenantId = "";
        if (tenantId == MultitenantConstants.INVALID_TENANT_ID
                || tenantId == MultitenantConstants.SUPER_TENANT_ID) {
            currTenantId = "0";
        } else {
            currTenantId = String.valueOf(tenantId);
        }
        if( serverKey == null || serverKey.equals("")) {
            serverName = getCurrentServerName();
        } else {
            serverName = serverKey;
        }
        String currDateStr = getCurrentDate();
        String colFamily = config.getLogProviderProperty(CassandraConfigProperties.COLUMN_FAMILY) + "_" + currTenantId + "_" + serverName + "_"
                + currDateStr;
        return colFamily;
    }

    private LogEvent[] getLogsForType(LogEvent[] events, String type) {
        List<LogEvent> resultList = new ArrayList<LogEvent>();
        for (LogEvent event : events) {
            if (event.getPriority().equals(type)) {
                resultList.add(event);
            }
        }
        return resultList.toArray(new LogEvent[resultList.size()]);
    }

    private LogEvent[] getLogsForKey(LogEvent[] events, String keyword) {
        List<LogEvent> resultList = new ArrayList<LogEvent>();
        for (LogEvent event : events) {
            boolean isInLogMessage = event.getMessage() != null
                    && (event.getMessage().toLowerCase().indexOf(keyword.toLowerCase()) > -1);
            boolean isInLogger = event.getLogger() != null
                    && (event.getLogger().toLowerCase().indexOf(keyword.toLowerCase()) > -1);
            boolean isInStacktrace = event.getStacktrace() != null
                    && (event.getStacktrace().toLowerCase().indexOf(keyword.toLowerCase()) > -1);
            if (isInLogger || isInLogMessage || isInStacktrace) {
                resultList.add(event);
            }
        }
        return resultList.toArray(new LogEvent[resultList.size()]);
    }

    private LogEvent[] getSortedLogsFromCassandra(String applicationName, String domain, String serverKey) throws LogViewerException {
        Future<LogEvent[]> task = this.getExecutorService().submit(
                new LogEventSorter(this.getAllLogs(domain, serverKey), ""));
        List<LogEvent> resultList = new ArrayList<LogEvent>();
        try {
            if (applicationName.equals("")) {
                return task.get();
            } else {
                LogEvent events[] = task.get();
                for (LogEvent e : events) {
                    if (applicationName.equals(e.getAppName())) {
                        resultList.add(e);
                    }
                }
                return resultList.toArray(new LogEvent[resultList.size()]);
            }

        } catch (InterruptedException e) {
            log.error("Error occurred while retrieving the sorted log event list", e);
            throw new LogViewerException(
                    "Error occurred while retrieving the sorted log event list");
        } catch (ExecutionException e) {
            log.error("Error occurred while retrieving the sorted log event list", e);
            throw new LogViewerException(
                    "Error occurred while retrieving the sorted log event list");
        }

    }

    private LogEvent[] searchLog(LogEvent[] sortedLogs, String type, String keyword)
            throws LogViewerException {
        if ("ALL".equalsIgnoreCase(type)) {
            return getLogsForKey(sortedLogs, keyword);
        } else {
            LogEvent[] filerByType = getLogsForType(sortedLogs, type);
            List<LogEvent> resultList = new ArrayList<LogEvent>();
            if (filerByType != null) {
                for (int i = 0; i < filerByType.length; i++) {
                    String logMessage = filerByType[i].getMessage();
                    String logger = filerByType[i].getLogger();
                    if (logMessage != null
                            && logMessage.toLowerCase().indexOf(keyword.toLowerCase()) > -1) {
                        resultList.add(filerByType[i]);
                    } else if (logger != null
                            && logger.toLowerCase().indexOf(keyword.toLowerCase()) > -1) {
                        resultList.add(filerByType[i]);
                    }
                }
            }
            if (resultList.isEmpty()) {
                return null;
            }
            return resultList.toArray(new LogEvent[resultList.size()]);
        }

    }

    private ExecutorService getExecutorService() {
        return executorService;
    }

    private boolean isCFExsist(String keyspaceName, String columnFamilyName) throws LogViewerException {
        KeyspaceDefinition keyspaceDefinition;
        try {
            keyspaceDefinition = getCurrentCassandraCluster().describeKeyspace(keyspaceName);
            if (keyspaceDefinition != null && !keyspaceDefinition.equals("")) {
                List<ColumnFamilyDefinition> columnFamilyDefinitionList = keyspaceDefinition
                        .getCfDefs();
                for (ColumnFamilyDefinition cfd : columnFamilyDefinitionList) {
                    if (cfd.getName().equals(columnFamilyName)) {
                        return true;
                    }
                }
            }
        } catch (LogViewerException e) {
            log.error("Error occurred while retrieving column families", e);
            throw new LogViewerException("Error occurred while retrieving column families");
        }
        return false;
    }

    private  String[] getSortedApplicationNames(List<String> applicationNames) {
        Collections.sort(applicationNames, new Comparator<String>() {
            public int compare(String s1, String s2) {
                return s1.toLowerCase().compareTo(s2.toLowerCase());
            }

        });
        return (String[]) applicationNames.toArray(new String[applicationNames.size()]);
    }

    public static final class CassandraConfigProperties {
        public static final String URL = "url";
        public static final String USER_NAME ="userName";
        public static final String PASSWORD ="password";
        public static final String PUBLISHER_URL = "publisherURL";
        public static final String PUBLISHER_USER = "publisherUser";
        public static final String PUBLISHER_PASSWORD = "publisherPassword";
        public static final String ARCHIVED_HOST = "archivedHost";
        public static final String ARCHIVED_USER = "archivedUser";
        public static final String ARCHIVED_PASSWORD = "archivedPassword";
        public static final String ARCHIVED_PORT = "archivedPort";
        public static final String ARCHIVED_REALM = "archivedRealm";
        public static final String ARCHIVED_HDFS_PATH = "archivedHDFSPath";
        public static final String HIVE_QUERY = "hiveQuery";

        public static final String KEYSPACE = "keyspace";
        public static final String COLUMN_FAMILY ="columnFamily";
        public static final String IS_CASSANDRA_AVAILABLE ="isDataFromCassandra";
        public static final String CLUSTER ="cluster";
        public static final String CONSISTENCY_LEVEL = "cassandraConsistencyLevel";
        public static final String AUTO_DISCOVERY_ENABLE = "enable";
        public static final String AUTO_DISCOVERY_DELAY = "delay";
        public static final String RETRY_DOWNED_HOSTS = "retryDownedHosts";
        public static final String RETRY_DOWNED_HOSTS_ENABLE = "enable";
        public static final String RETRY_DOWNED_HOSTS_QUEUE = "queueSize";
        public static final String AUTO_DISCOVERY = "cassandraAutoDiscovery";

    }
}

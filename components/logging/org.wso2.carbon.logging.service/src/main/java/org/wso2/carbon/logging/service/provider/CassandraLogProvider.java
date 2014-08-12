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
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.logging.service.LogViewerException;
import org.wso2.carbon.logging.service.data.LogEvent;
import org.wso2.carbon.logging.service.data.LoggingConfig;
import org.wso2.carbon.logging.service.provider.api.LogProvider;
import org.wso2.carbon.logging.service.sort.LogEventSorter;
import org.wso2.carbon.logging.service.util.LoggingConstants;
import org.wso2.carbon.logging.service.util.LoggingUtil;
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

public class CassandraLogProvider implements LogProvider {

    private final static StringSerializer stringSerializer = StringSerializer.get();
    private static final int MAX_NO_OF_EVENTS = 40000;
    private static Log log = LogFactory.getLog(CassandraLogProvider.class);
    private ExecutorService executorService = Executors.newFixedThreadPool(1);
    private LoggingConfig config;

    /**
     * Initialize the log provider by reading the property comes with logging configuration file
     * This will be called immediate after create new instance of ILogProvider
     *
     * @param loggingConfig - configuration class which read and keep configurations
     */
    @Override
    public void init(LoggingConfig loggingConfig) {
        this.config = loggingConfig;
    }

    @Override
    public List<String> getApplicationNames(String tenantDomain, String serverKey) throws LogViewerException {
        List<String> appList = new ArrayList<String>();
        List<LogEvent> allLogs;
        try {
            allLogs = getAllLogs(tenantDomain, serverKey);
        } catch (LogViewerException e) {
            log.error("Error retrieving application logs", e);
            throw new LogViewerException("Error retrieving application logs", e);
        }
        for (LogEvent event : allLogs) {
            if (event.getAppName() != null && !event.getAppName().equals("") && !event.getAppName().equals("NA")
                    && !LoggingUtil.isAdmingService(event.getAppName()) && !appList.contains(event.getAppName()) &&
                    !event.getAppName().equals("STRATOS_ROOT")) {
                appList.add(event.getAppName());
            }
        }
        return getSortedApplicationNames(appList);
    }

    @Override
    public List<LogEvent> getSystemLogs() throws LogViewerException {
        return new ArrayList<LogEvent>();
    }

    @Override
    public List<LogEvent> getAllLogs(String tenantDomain, String serverKey) throws LogViewerException {

        // int tenantId = getCurrentTenantId(tenantDomain);
        // serviceName = getCurrentServerName(serviceName);
        Keyspace currKeyspace = getCurrentCassandraKeyspace();
        String colFamily = getCFName(config, tenantDomain, serverKey);
        if (!isCFExsist(config.getLogProviderProperty(CassandraConfigProperties.KEYSPACE), colFamily)) {
            return new ArrayList<LogEvent>();
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

        return getLoggingResultList(rangeSlicesQuery);
    }

    @Override
    public List<LogEvent> getLogsByAppName(String appName, String tenantDomain, String serverKey) throws LogViewerException {
        return getAllLogs(tenantDomain, serverKey);
    }

    @Override
    public List<LogEvent> getLogs(String type, String keyword, String appName, String tenantDomain, String serverKey) throws LogViewerException {
        List<LogEvent> events = getSortedLogsFromCassandra("", tenantDomain, serverKey);
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
    public int logsCount(String tenantDomain, String serverKey) throws LogViewerException {
        Keyspace currKeyspace = getCurrentCassandraKeyspace();
        String colFamily = getCFName(config, tenantDomain, serverKey);

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
        CassandraHostConfigurator hostConfigurator = new CassandraHostConfigurator(connectionUrl);
        String prop = config.getLogProviderProperty(CassandraConfigProperties.RETRY_DOWNED_HOSTS_ENABLE);
        hostConfigurator.setRetryDownedHosts((prop == null || prop.equals("")) ? false : Boolean.valueOf(prop));
        prop = config.getLogProviderProperty(CassandraConfigProperties.RETRY_DOWNED_HOSTS_QUEUE_SIZE);
        hostConfigurator.setRetryDownedHostsQueueSize((prop == null || prop.equals("")) ? -1 : Integer.valueOf(prop));
        prop = config.getLogProviderProperty(CassandraConfigProperties.AUTO_DISCOVERY_ENABLE);
        hostConfigurator.setAutoDiscoverHosts((prop == null || prop.equals("")) ? false : Boolean.valueOf(prop));
        prop = config.getLogProviderProperty(CassandraConfigProperties.AUTO_DISCOVERY_DELAY);
        hostConfigurator.setAutoDiscoveryDelayInSeconds((prop == null || prop.equals("")) ? -1 : Integer.valueOf(prop));
        return HFactory.createCluster(clusterName, hostConfigurator, credentials);
    }

    private Cluster getCluster(String clusterName, String connectionUrl,
                               Map<String, String> credentials) throws LogViewerException {
        //removed getting cluster from session, since we dont aware of BAM shutting down time
        return retrieveCassandraCluster(clusterName, connectionUrl, credentials);
    }

    private Keyspace getCurrentCassandraKeyspace() throws LogViewerException {
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
        String connectionUrl = config.getLogProviderProperty(CassandraConfigProperties.CASSANDRA_HOST);
        String userName = config.getLogProviderProperty(CassandraConfigProperties.USER_NAME);
        String password = config.getLogProviderProperty(CassandraConfigProperties.PASSWORD);
        String clusterName = config.getLogProviderProperty(CassandraConfigProperties.CLUSTER);
        Map<String, String> credentials = new HashMap<String, String>();
        credentials.put(LoggingConstants.USERNAME_KEY, userName);
        credentials.put(LoggingConstants.PASSWORD_KEY, password);
        return getCluster(clusterName, connectionUrl, credentials);
    }


    private String convertByteToString(byte[] array) {
        return new String(array);
    }

    private String convertLongToString(Long longval) {
        Date date = new Date(longval);
        DateFormat formatter = new SimpleDateFormat(LoggingConstants.DATE_TIME_FORMATTER);
        return formatter.format(date);
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
                } else if (hc.getName().equals(LoggingConstants.HColumn.SERVER_NAME)) {
                    event.setServerName(convertByteToString(hc.getValue()));
                } else if (hc.getName().equals(LoggingConstants.HColumn.APP_NAME)) {
                    event.setAppName(convertByteToString(hc.getValue()));
                } else if (hc.getName().equals(LoggingConstants.HColumn.LOG_TIME)) {
                    event.setLogTime(convertLongToString(convertByteToLong(hc.getValue(), 0)));
                } else if (hc.getName().equals(LoggingConstants.HColumn.LOGGER)) {
                    event.setLogger(convertByteToString(hc.getValue()));
                } else if (hc.getName().equals(LoggingConstants.HColumn.PRIORITY)) {
                    event.setPriority(convertByteToString(hc.getValue()));
                } else if (hc.getName().equals(LoggingConstants.HColumn.MESSAGE)) {
                    event.setMessage(convertByteToString(hc.getValue()));
                } else if (hc.getName().equals(LoggingConstants.HColumn.IP)) {
                    event.setIp(convertByteToString(hc.getValue()));
                } else if (hc.getName().equals(LoggingConstants.HColumn.STACKTRACE)) {
                    event.setStacktrace(convertByteToString(hc.getValue()));
                } else if (hc.getName().equals(LoggingConstants.HColumn.INSTANCE)) {
                    event.setIp(convertByteToString(hc.getValue()));
                }

            }
            resultList.add(event);
        }
        return resultList;
    }

    private String getCurrentServerName() {
        return ServerConfiguration.getInstance().getFirstProperty("ServerKey");
    }

    private String getCurrentDate() {
        Date cirrDate = new Date();
        DateFormat formatter = new SimpleDateFormat(LoggingConstants.DATE_FORMATTER);
        String formattedDate = formatter.format(cirrDate);
        return formattedDate.replace("-", "_");
    }

    private String getCFName(LoggingConfig config, String tenantDomain, String serverKey) {
        int tenantId;
        String serverName;
        if (tenantDomain == null || tenantDomain.equals("")) {
            tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
        } else {
            try {
                tenantId = LoggingUtil.getTenantIdForDomain(tenantDomain);
            } catch (LogViewerException e) {
                log.warn("Error while getting tenantId for tenantDomain " + tenantDomain);
                tenantId = MultitenantConstants.SUPER_TENANT_ID;
            }
        }
        String currTenantId;
        if (tenantId == MultitenantConstants.INVALID_TENANT_ID
                || tenantId == MultitenantConstants.SUPER_TENANT_ID) {
            currTenantId = "0";
        } else {
            currTenantId = String.valueOf(tenantId);
        }
        if (serverKey == null || serverKey.equals("")) {
            serverName = getCurrentServerName();
        } else {
            serverName = serverKey;
        }
        String currDateStr = getCurrentDate();
        return config.getLogProviderProperty(CassandraConfigProperties.COLUMN_FAMILY) + "_" + currTenantId + "_" + serverName + "_"
                + currDateStr;
    }

    private List<LogEvent> getLogsForType(List<LogEvent> logEvents, String type) {
        List<LogEvent> resultList = new ArrayList<LogEvent>();
        for (LogEvent event : logEvents) {
            if (event.getPriority().equals(type)) {
                resultList.add(event);
            }
        }
        return resultList;
    }

    private List<LogEvent> getLogsForKey(List<LogEvent> logEvents, String keyword) {
        List<LogEvent> resultList = new ArrayList<LogEvent>();
        for (LogEvent event : logEvents) {
            boolean isInLogMessage = event.getMessage() != null
                    && (event.getMessage().toLowerCase().contains(keyword.toLowerCase()));
            boolean isInLogger = event.getLogger() != null
                    && (event.getLogger().toLowerCase().contains(keyword.toLowerCase()));
            boolean isInStacktrace = event.getStacktrace() != null
                    && (event.getStacktrace().toLowerCase().contains(keyword.toLowerCase()));
            if (isInLogger || isInLogMessage || isInStacktrace) {
                resultList.add(event);
            }
        }
        return resultList;
    }

    private List<LogEvent> getSortedLogsFromCassandra(String appName, String tenantDomain, String serverKey) throws LogViewerException {
        Future<List<LogEvent>> task = this.getExecutorService().submit(
                new LogEventSorter(this.getAllLogs(tenantDomain, serverKey)));
        List<LogEvent> resultList = new ArrayList<LogEvent>();
        try {
            if (appName.equals("")) {
                return task.get();
            } else {
                List<LogEvent> events = task.get();
                for (LogEvent e : events) {
                    if (appName.equals(e.getAppName())) {
                        resultList.add(e);
                    }
                }
                return resultList;
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

    private List<LogEvent> searchLog(List<LogEvent> sortedLogs, String type, String keyword)
            throws LogViewerException {
        if ("ALL".equalsIgnoreCase(type)) {
            return getLogsForKey(sortedLogs, keyword);
        } else {
            List<LogEvent> filerByType = getLogsForType(sortedLogs, type);
            List<LogEvent> resultList = new ArrayList<LogEvent>();
            if (filerByType != null) {
                for (LogEvent aFilerByType : filerByType) {
                    String logMessage = aFilerByType.getMessage();
                    String logger = aFilerByType.getLogger();
                    if (logMessage != null
                            && logMessage.toLowerCase().contains(keyword.toLowerCase())) {
                        resultList.add(aFilerByType);
                    } else if (logger != null
                            && logger.toLowerCase().contains(keyword.toLowerCase())) {
                        resultList.add(aFilerByType);
                    }
                }
            }
            return (resultList.isEmpty() ? null : resultList);
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

    private List<String> getSortedApplicationNames(List<String> appNames) {
        Collections.sort(appNames, new Comparator<String>() {
            public int compare(String s1, String s2) {
                return s1.toLowerCase().compareTo(s2.toLowerCase());
            }

        });
        return appNames;
    }

    public static final class CassandraConfigProperties {
        public static final String URL = "url";
        public static final String USER_NAME = "userName";
        public static final String PASSWORD = "password";
        public static final String CASSANDRA_HOST = "cassandraHost";
/*        public static final String PUBLISHER_URL = "publisherURL";
        public static final String PUBLISHER_USER = "publisherUser";
        public static final String PUBLISHER_PASSWORD = "publisherPassword";
        public static final String ARCHIVED_HOST = "archivedHost";
        public static final String ARCHIVED_USER = "archivedUser";
        public static final String ARCHIVED_PASSWORD = "archivedPassword";
        public static final String ARCHIVED_PORT = "archivedPort";
        public static final String ARCHIVED_REALM = "archivedRealm";
        public static final String ARCHIVED_HDFS_PATH = "archivedHDFSPath";
        public static final String HIVE_QUERY = "hiveQuery";*/

        public static final String KEYSPACE = "keyspace";
        public static final String COLUMN_FAMILY = "columnFamily";
        //        public static final String IS_CASSANDRA_AVAILABLE = "isDataFromCassandra";
        public static final String CLUSTER = "cluster";
        public static final String CONSISTENCY_LEVEL = "cassandraConsistencyLevel";
        public static final String AUTO_DISCOVERY_ENABLE = "cassandraAutoDiscovery.enable";
        public static final String AUTO_DISCOVERY_DELAY = "cassandraAutoDiscovery.delay";
        //        public static final String RETRY_DOWNED_HOSTS = "retryDownedHosts";
        public static final String RETRY_DOWNED_HOSTS_ENABLE = "retryDownedHosts.enable";
        public static final String RETRY_DOWNED_HOSTS_QUEUE_SIZE = "retryDownedHosts.queueSize";
//        public static final String AUTO_DISCOVERY = "cassandraAutoDiscovery";

    }
}

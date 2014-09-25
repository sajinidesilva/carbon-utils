/*
 * Copyright 2005,2014 WSO2, Inc. http://www.wso2.org
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.logging.service.config;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.wso2.carbon.logging.service.data.LoggingConfig;
import org.wso2.carbon.logging.service.util.LoggingConstants;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.utils.CarbonUtils;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;

public class LoggingConfigManager {

    private static final Log log = LogFactory.getLog(LoggingConfigManager.class);
    private static LoggingConfigManager cassandraConfig;
    private static BundleContext bundleContext;
    private static LoggingConfig loggingConfig;

    public static LoggingConfigManager getCassandraConfig() {
        return cassandraConfig;
    }

    public static void setCassandraConfig(LoggingConfigManager syslogConfig) {
        LoggingConfigManager.cassandraConfig = syslogConfig;
    }

    public static void setBundleContext(BundleContext bundleContext) {
        LoggingConfigManager.bundleContext = bundleContext;
    }

    public static Log getLog() {
        return log;
    }

    /**
     * Returns the configurations from the Cassandra configuration file.
     *
     * @return cassandra configurations
     */
    public static LoggingConfig loadLoggingConfiguration() {
        if (loggingConfig != null) {
            return loggingConfig;
        }

        // gets the configuration file name from the cassandra-config.xml.
        String cassandraConfigFileName = CarbonUtils.getCarbonConfigDirPath()
                + RegistryConstants.PATH_SEPARATOR
                + LoggingConstants.ETC_DIR
                + RegistryConstants.PATH_SEPARATOR
                + LoggingConstants.LOGGING_CONF_FILE;
        loggingConfig = loadLoggingConfiguration(cassandraConfigFileName);
        return loggingConfig;
    }

    /**
     * Loads the given Syslog Configuration file.
     *
     * @param configFilename Name of the configuration file
     * @return the syslog configuration data. null will be return if there any issue while loading configurations
     */
    private static LoggingConfig loadLoggingConfiguration(String configFilename) {
        InputStream inputStream = null;
        try {
            inputStream = new LoggingConfigManager()
                    .getInputStream(configFilename);
        } catch (IOException e1) {
            log.error("Could not close the Configuration File "
                    + configFilename);
        }
        if (inputStream != null) {
            try {
                XMLStreamReader parser = XMLInputFactory.newInstance()
                        .createXMLStreamReader(inputStream);
                StAXOMBuilder builder = new StAXOMBuilder(parser);
                OMElement documentElement = builder.getDocumentElement();
                LoggingConfig config = new LoggingConfig();
                @SuppressWarnings("rawtypes")
                OMElement logProviderConfig = documentElement.getFirstChildWithName(
                        getQName(LoggingConstants.LogConfigProperties.LOG_PROVIDER_CONFIG));
                loadLogProviderProperties(config, logProviderConfig);
                // load log file provider configurations
                OMElement logFileProviderConfig = documentElement.getFirstChildWithName(
                        getQName(LoggingConstants.LogConfigProperties.LOG_FILE_PROVIDER_CONFIG));
                loadLogFileProviderProperties(config, logFileProviderConfig);
                return config;
            } catch (Exception e) {
                String msg = "Error in loading Stratos Configurations File: "
                        + configFilename + ". Default Settings will be used.";
                log.error(msg, e);
                return null;
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        log.error("Could not close the Configuration File "
                                + configFilename);
                    }
                }
            }
        }
        log.error("Unable to locate the stratos configurations file. "
                + "Default Settings will be used.");
        return null;
        // if the file not found.
    }

    private static void loadLogFileProviderProperties(LoggingConfig config, OMElement logFileProviderConfig) {
        String implClass = logFileProviderConfig.getAttributeValue(new QName("", LoggingConstants.LogConfigProperties.CLASS_ATTRIBUTE));
        if (implClass != null) {
            config.setLogFileProviderImplClassName(implClass);
            OMElement propElement = logFileProviderConfig.getFirstChildWithName(getQName(LoggingConstants.LogConfigProperties.PROPERTIES));
            Object ntEle;
            OMElement propEle;
            if (propElement != null) {
                Iterator it = propElement.getChildrenWithLocalName(LoggingConstants.LogConfigProperties.PROPERTY);
                while (it.hasNext()) {
                    ntEle = it.next();
                    if (ntEle instanceof OMElement) {
                        propEle = (OMElement) ntEle;
                        config.setLogFileProviderProperty(
                                propEle.getAttributeValue(new QName(LoggingConstants.LogConfigProperties.PROPERTY_NAME)),
                                propEle.getAttributeValue(new QName(LoggingConstants.LogConfigProperties.PROPERTY_VALUE)));
                    }
                }
            } else {
                log.error("Error loading log file provider properties, check the logging configuration file");
            }
        } else {
            log.error("LogFileProvider implementation class name is null, check the logging configuration file");
        }
    }

    private static void loadLogProviderProperties(LoggingConfig config, OMElement logProviderConfig) {
        String implClass = logProviderConfig.getAttributeValue(new QName("", LoggingConstants.LogConfigProperties.CLASS_ATTRIBUTE));
        if (implClass != null) {
            config.setLogProviderImplClassName(implClass);
            // load log provider configuration
            OMElement propElement = logProviderConfig.getFirstChildWithName(getQName(LoggingConstants.LogConfigProperties.PROPERTIES));
            if (propElement != null) {
                Iterator it = propElement.getChildrenWithLocalName(LoggingConstants.LogConfigProperties.PROPERTY);
                OMElement propEle;
                Object ntEle;
                while (it.hasNext()) {
                    ntEle = it.next();
                    if (ntEle instanceof OMElement) {
                        propEle = (OMElement) ntEle;
                        config.setLogProviderProperty(
                                propEle.getAttributeValue(new QName(LoggingConstants.LogConfigProperties.PROPERTY_NAME)),
                                propEle.getAttributeValue(new QName(LoggingConstants.LogConfigProperties.PROPERTY_VALUE)));
                    }
                }
            } else {
                log.error("Error loading log provider properties, check the logging configuration file ");
            }
        } else {
            log.error("LogProvider implementation class name is null, check the loggging configuration file");
        }

    }

    private static void loadLogProviderProperties(OMElement element, LoggingConfig config) {
        Iterator it = element.getChildElements();
        while (it.hasNext()) {
            OMElement propEle = (OMElement) it.next();
            config.setLogProviderProperty(
                    propEle.getAttributeValue(getQName(LoggingConstants.LogConfigProperties.PROPERTY_NAME)),
                    propEle.getAttributeValue(getQName(LoggingConstants.LogConfigProperties.PROPERTY_VALUE)));
        }

    }

    private static void loadLogFileProviderProperties(OMElement element, LoggingConfig config) {
        Iterator it = element.getChildElements();
        while (it.hasNext()) {
            OMElement propEle = (OMElement) it.next();
            config.setLogFileProviderProperty(
                    propEle.getAttributeValue(getQName(LoggingConstants.LogConfigProperties.PROPERTY_NAME)),
                    propEle.getAttributeValue(getQName(LoggingConstants.LogConfigProperties.PROPERTY_VALUE)));
        }

    }

    private static QName getQName(String localName) {
        return new QName(LoggingConstants.LogConfigProperties.DEFAULT_LOGGING_CONFIG_NAMESPACE, localName);
    }

    public LoggingConfig getSyslogData() {
        return null;
    }

    private InputStream getInputStream(String configFilename)
            throws IOException {
        InputStream inStream = null;
        File configFile = new File(configFilename);
        if (configFile.exists()) {
            inStream = new FileInputStream(configFile);
        }
        String warningMessage = "";
        if (inStream == null) {
            URL url;
            if (bundleContext != null) {
                if ((url = bundleContext.getBundle().getResource(
                        LoggingConstants.LOGGING_CONF_FILE)) != null) {
                    inStream = url.openStream();
                } else {
                    warningMessage = "Bundle context could not find resource "
                            + LoggingConstants.LOGGING_CONF_FILE
                            + " or user does not have sufficient permission to access the resource.";
                    log.warn(warningMessage);
                }

            } else {
                if ((url = this.getClass().getClassLoader()
                        .getResource(LoggingConstants.LOGGING_CONF_FILE)) != null) {
                    inStream = url.openStream();
                } else {
                    warningMessage = "Could not find resource "
                            + LoggingConstants.LOGGING_CONF_FILE
                            + " or user does not have sufficient permission to access the resource.";
                    log.warn(warningMessage);
                }
            }
        }
        return inStream;
    }
}

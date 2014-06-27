package org.wso2.carbon.logging.config;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.wso2.carbon.logging.service.data.LoggingConfig;
import org.wso2.carbon.logging.util.LoggingConstants;
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

	public static void setBundleContext(BundleContext bundleContext) {
		LoggingConfigManager.bundleContext = bundleContext;
	}

	public static void setCassandraConfig(LoggingConfigManager syslogConfig) {
		LoggingConfigManager.cassandraConfig = syslogConfig;
	}

	public static Log getLog() {
		return log;
	}

	public LoggingConfig getSyslogData() {
		return null;
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
        return loadLoggingConfiguration(cassandraConfigFileName);
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


    /**
     * Loads the given Syslog Configuration file.
     *
     * @param configFilename
     *            Name of the configuration file
     * @return the syslog configuration data. null will be return if there any issue while loading configurations
     */
    private static LoggingConfig loadLoggingConfiguration(String configFilename){
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
                String implClass = documentElement.getAttributeValue(new QName("", "class"));
                LoggingConfig config = new LoggingConfig(implClass);
//                Class.forName(implClass);
                @SuppressWarnings("rawtypes")
                Iterator it = documentElement.getChildElements();
                while (it.hasNext()) {
                    OMElement element = (OMElement) it.next();
                    if (element.getText().equals(LoggingConstants.LogProviderProperties.PROPERTIES)) {
                        loadProperties(element, config);
                    }
                }
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

    private static void loadProperties(OMElement element, LoggingConfig config) {
        Iterator it = element.getChildElements();
        while (it.hasNext()) {
            OMElement propEle = (OMElement) it.next();
            config.setProperty(propEle.getAttributeValue(getQName("name")), propEle.getAttributeValue(getQName("value")));
        }

    }

    private static QName getQName(String localName) {
        return new QName("http://wso2.org/projects/carbon/carbon.xml", localName);
    }
}

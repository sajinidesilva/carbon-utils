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

import org.testng.annotations.Test;
import org.wso2.carbon.logging.service.data.LoggingConfig;

import java.io.File;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Test loading the configuration file names from the config file.
 */
public class LoggingConfigManagerTest {

    @Test(groups = {"org.wso2.carbon.logging.service.config"},
          description = "Test loading a configuration from the config file")
    public void testLoadLoggingConfiguration() throws Exception {
        String configFileNameWithPath = "." + File.separator + "src" + File.separator + "test" +
                                        File.separator + "resources" + File.separator +
                                        "logging-config.xml";
        LoggingConfig loggingConfig = LoggingConfigManager
                .loadLoggingConfiguration(configFileNameWithPath);
        assertEquals(loggingConfig.getLogProviderImplClassName(),
                     "org.wso2.carbon.logging.service.provider.InMemoryLogProvider",
                     "Unexpected LogProvider implementation class name was returned.");
        assertEquals(loggingConfig.getLogFileProviderImplClassName(),
                     "org.wso2.carbon.logging.service.provider.FileLogProvider",
                     "Unexpected LogFileProvider implementation class name was returned.");
    }

    @Test(groups = {"org.wso2.carbon.logging.service.config"},
          description = "Test loading an invalid configuration, should throw an exception",
          expectedExceptions = org.apache.axiom.om.OMException.class)
    public void testLoadInvalidConfiguration() {
        String configFileNameWithPath = "." + File.separator + "src" + File.separator + "test" +
                                        File.separator + "resources" + File.separator +
                                        "invalid-config.xml";
        LoggingConfig loggingConfig = LoggingConfigManager
                .loadLoggingConfiguration(configFileNameWithPath);
        // Even in failure, an empty configuration should be returned, instead of a null.
        assertNotNull(loggingConfig, "Logging config was null.");
    }
}

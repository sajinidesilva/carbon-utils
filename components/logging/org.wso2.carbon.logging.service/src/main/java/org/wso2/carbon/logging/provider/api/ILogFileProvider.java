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
package org.wso2.carbon.logging.provider.api;

import org.wso2.carbon.logging.service.LogViewerException;
import org.wso2.carbon.logging.service.data.LogInfo;
import org.wso2.carbon.logging.service.data.LoggingConfig;

import javax.activation.DataHandler;

/**
 * All log file providers must inherit this interface. Log viewer use this to get all the details related to log file.
 */
public interface ILogFileProvider {

    /**
     * Initialize the file log provider by reading the property comes with logging configuration file
     * This will be called immediate after create new instance of ILogFileProvider
     * @param loggingConfig - logging configuration
     */
    public void init(LoggingConfig loggingConfig);

    /**
     * Return array of LogInfo, which is available under given tenant domain and serviceName
     * @param tenantDomain - Tenant domain eg: t1.com
     * @param serviceName - Service name or Server key
     * @return array of LogInfo, which is available under given tenant domain and serviceName, <code>null</code>
     * if there  is no LogInfo available.
     * @throws LogViewerException
     */
    public LogInfo[] getLogInfo(String tenantDomain, String serviceName) throws LogViewerException;

    /**
     * Download the file
     * @param logFile - File name which need to download, this should not be null.
     * @param tenantDomain - Tenant domain eg: t1.com
     * @param serviceName - Service name or Server key
     * @return DataHandler for the given logfile, return <code>null</code> if there is not such logfile.
     * @throws LogViewerException
     */
    public DataHandler downloadLogFile(String logFile, String tenantDomain, String serviceName)throws LogViewerException;


}

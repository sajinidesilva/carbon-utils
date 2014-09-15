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

package org.wso2.carbon.event.core.sharedmemory;

import org.wso2.carbon.event.core.delivery.MatchingManagerFactory;
import org.wso2.carbon.event.core.delivery.MatchingManager;
import org.wso2.carbon.event.core.exception.EventBrokerException;
import org.wso2.carbon.event.core.exception.EventBrokerConfigurationException;
import org.apache.axiom.om.OMElement;
import org.wso2.carbon.event.core.sharedmemory.util.SharedMemoryCacheUtil;

import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;
import java.util.concurrent.TimeUnit;

public class SharedMemoryMatchingManagerFactory implements MatchingManagerFactory {

    private static Cache<Integer, SharedMemoryMatchingManager> getInMemoryMatchingCache() {
        return SharedMemoryCacheUtil.getInMemoryMatchingCache();
//        return Caching.getCacheManagerFactory().getCacheManager("inMemoryEventCacheManager").getCache("inMemoryEventCache");
    }

    public MatchingManager getMatchingManager(OMElement config) throws EventBrokerConfigurationException {
        SharedMemoryMatchingManager inMemoryMatchingManager = null ;

        if(getInMemoryMatchingCache().get(1) == null) {
            inMemoryMatchingManager = new SharedMemoryMatchingManager();
            getInMemoryMatchingCache().put(1, inMemoryMatchingManager);
        } else {
        }

        try {
            //call initialize tenant for super tenant
            inMemoryMatchingManager.initializeTenant();
        } catch (EventBrokerException e) {
            throw new EventBrokerConfigurationException("Can not initialize the in memory mathing manager");
        }
        return inMemoryMatchingManager;
    }
}

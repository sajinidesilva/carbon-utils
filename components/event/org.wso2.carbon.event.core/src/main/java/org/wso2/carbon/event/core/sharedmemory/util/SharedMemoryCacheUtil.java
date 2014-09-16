/*
 * Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.event.core.sharedmemory.util;

import org.wso2.carbon.event.core.sharedmemory.SharedMemoryMatchingManager;

import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;
import java.util.concurrent.TimeUnit;

public class SharedMemoryCacheUtil {
    private static boolean cacheInit = false;

    private SharedMemoryCacheUtil(){}
    
    public static Cache<Integer, SharedMemoryMatchingManager> getInMemoryMatchingCache() {
        if (cacheInit) {
            return Caching.getCacheManagerFactory().getCacheManager("inMemoryEventCacheManager").getCache("inMemoryEventCache");
        } else {
            CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager("inMemoryEventCacheManager");
            String cacheName = "inMemoryEventCache";
            cacheInit = true;
            return cacheManager.<Integer, SharedMemoryMatchingManager>createCacheBuilder(cacheName).
                    setExpiry(CacheConfiguration.ExpiryType.MODIFIED, new CacheConfiguration.Duration(TimeUnit.SECONDS, 1000 * 24 * 3600)).
                    setExpiry(CacheConfiguration.ExpiryType.ACCESSED, new CacheConfiguration.Duration(TimeUnit.SECONDS, 1000 * 24 * 3600)).
                    setStoreByValue(false).build();

        }
    }
}

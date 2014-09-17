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

package org.wso2.carbon.event.core.sharedmemory;


import org.wso2.carbon.event.core.subscription.Subscription;
import org.wso2.carbon.event.core.util.EventBrokerConstants;

import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

/**
 * Subscription container is used to keep track of caches related to a given topic name,
 * across the nodes in the cluster.
 */

@SuppressWarnings("serial")
class SubscriptionContainer implements Serializable {
    //variable to store the cache name related to this container (this container's topic name)
    private String topicCacheName = null;
    private boolean topicCacheInit = false;

    public SubscriptionContainer(String topicName) {
        this.topicCacheName = topicName.replace("*", EventBrokerConstants.STAR_CHARACTER_FOR_CACHE_NAMES);
    }

    public Cache<String, Subscription> getSubscriptionsCache() {
        if (topicCacheInit) {
            return Caching.getCacheManagerFactory().getCacheManager("inMemoryEventCacheManager").getCache(topicCacheName);
        } else {
            CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager("inMemoryEventCacheManager");
            String cacheName = topicCacheName;
            Cache<String, Subscription> newCache = cacheManager.<String, Subscription>createCacheBuilder(cacheName).
                    setExpiry(CacheConfiguration.ExpiryType.MODIFIED, new CacheConfiguration.Duration(TimeUnit.SECONDS, 1000 * 24 * 3600)).
                    setExpiry(CacheConfiguration.ExpiryType.ACCESSED, new CacheConfiguration.Duration(TimeUnit.SECONDS, 1000 * 24 * 3600)).
                    setStoreByValue(false).build();
            topicCacheInit = true;

            return newCache;
        }
    }
}
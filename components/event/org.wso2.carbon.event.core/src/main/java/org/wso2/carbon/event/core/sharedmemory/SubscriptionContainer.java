package org.wso2.carbon.event.core.sharedmemory;


import org.wso2.carbon.event.core.subscription.Subscription;

import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;
import java.io.Serializable;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("serial")
class SubscriptionContainer implements Serializable {
    private String topicName = null;
    private boolean topicCacheInit = false;

    public SubscriptionContainer(String topicName) {
        this.topicName = topicName;
    }

    public Cache<String, Subscription> getSubscriptionsCache() {
        if (topicCacheInit) {
            return Caching.getCacheManagerFactory().getCacheManager("inMemoryEventCacheManager").getCache(topicName);
        } else {
            CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager("inMemoryEventCacheManager");
            String cacheName = topicName;
            topicCacheInit = true;
            return cacheManager.<String, Subscription>createCacheBuilder(cacheName).
                    setExpiry(CacheConfiguration.ExpiryType.MODIFIED, new CacheConfiguration.Duration(TimeUnit.SECONDS, 1000 * 24 * 3600)).
                    setExpiry(CacheConfiguration.ExpiryType.ACCESSED, new CacheConfiguration.Duration(TimeUnit.SECONDS, 1000 * 24 * 3600)).
                    setStoreByValue(false).build();
        }
    }
}
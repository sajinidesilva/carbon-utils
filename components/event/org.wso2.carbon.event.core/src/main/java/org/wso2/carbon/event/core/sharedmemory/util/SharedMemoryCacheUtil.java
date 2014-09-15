package org.wso2.carbon.event.core.sharedmemory.util;

import org.wso2.carbon.event.core.sharedmemory.SharedMemoryMatchingManager;

import javax.cache.Cache;
import javax.cache.CacheConfiguration;
import javax.cache.CacheManager;
import javax.cache.Caching;
import java.util.concurrent.TimeUnit;

public class SharedMemoryCacheUtil {
    private static Cache<Integer, SharedMemoryMatchingManager> cache = null;
    private static boolean CacheInit = false;

    public static Cache<Integer, SharedMemoryMatchingManager> getInMemoryMatchingCache() {
        if (CacheInit) {
            return Caching.getCacheManagerFactory().getCacheManager("inMemoryEventCacheManager").getCache("inMemoryEventCache");
        } else {
            CacheManager cacheManager = Caching.getCacheManagerFactory().getCacheManager("inMemoryEventCacheManager");
            String cacheName = "inMemoryEventCache";
            CacheInit = true;
            return cacheManager.<Integer, SharedMemoryMatchingManager>createCacheBuilder(cacheName).
                    setExpiry(CacheConfiguration.ExpiryType.MODIFIED, new CacheConfiguration.Duration(TimeUnit.SECONDS, 1000 * 24 * 3600)).
                    setExpiry(CacheConfiguration.ExpiryType.ACCESSED, new CacheConfiguration.Duration(TimeUnit.SECONDS, 1000 * 24 * 3600)).
                    setStoreByValue(false).build();

        }
//        return Caching.getCacheManagerFactory().getCacheManager("inMemoryEventCacheManager").getCache("inMemoryEventCache");
    }
}

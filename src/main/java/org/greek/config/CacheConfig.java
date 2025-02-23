package org.greek.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.cache.jcache.config.JCacheConfigurerSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.cache.Caching;
import javax.cache.spi.CachingProvider;
import java.net.URI;

@Configuration
@EnableCaching
public class CacheConfig extends JCacheConfigurerSupport {

    @Bean
    public JCacheCacheManager cacheManager() {
        try {
            CachingProvider cachingProvider = Caching.getCachingProvider();
            URI cacheConfigUri = getClass().getResource("/ehcache.xml").toURI();
            javax.cache.CacheManager cacheManager = cachingProvider.getCacheManager(cacheConfigUri, getClass().getClassLoader());
            return new JCacheCacheManager(cacheManager);
        } catch (Exception e) {
            throw new RuntimeException("Failed to configure cache manager", e);
        }
    }
}


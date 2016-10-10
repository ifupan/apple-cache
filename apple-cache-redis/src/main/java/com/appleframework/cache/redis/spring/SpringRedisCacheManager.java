package com.appleframework.cache.redis.spring;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractCacheManager;

import redis.clients.jedis.JedisPool;

public class SpringRedisCacheManager extends AbstractCacheManager {

	private ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<String, Cache>();
	private Map<String, Integer> expireMap = new HashMap<String, Integer>();
	private Map<String, Boolean> openMap = new HashMap<String, Boolean>();

	private JedisPool jedisPool;

	public SpringRedisCacheManager() {
	}

	@Override
	protected Collection<? extends Cache> loadCaches() {
		Collection<Cache> values = cacheMap.values();
		return values;
	}

	@Override
	public Cache getCache(String name) {
		Cache cache = cacheMap.get(name);
		if (cache == null) {
			Integer expire = expireMap.get(name);
			if (expire == null) {
				expire = 0;
				expireMap.put(name, expire);
			}
			Boolean isOpen = openMap.get(name);
			if (isOpen == null) {
				isOpen = true;
				openMap.put(name, isOpen);
			}
			if(openMap.get(name)) {
				cache = new SpringRedisCache(jedisPool, name, expire.intValue(), true);
			}
			else {
				cache = new SpringRedisCache(jedisPool, name, expire.intValue());
			}
			
			cacheMap.put(name, cache);
		}
		return cache;
	}


	public void setExpireConfig(Map<String, Integer> expireConfig) {
		this.expireMap = expireConfig;
	}

	public void setOpenConfig(Map<String, Boolean> openConfig) {
		this.openMap = openConfig;
	}

	public void setJedisPool(JedisPool jedisPool) {
		this.jedisPool = jedisPool;
	}

}
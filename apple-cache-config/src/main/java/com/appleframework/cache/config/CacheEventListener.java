package com.appleframework.cache.config;

import java.util.Properties;

import com.appleframework.cache.core.config.CacheConfig;
import com.appleframework.config.core.event.ConfigListener;

public class CacheEventListener implements ConfigListener {

	private static String KEY_CACHE_ENABLE = "spring.cache.enable";

	private static String KEY_CACHE_OBJECT = "spring.cache.object";

	@Override
	public void receiveConfigInfo(Properties props) {
		Object cacheEnable = props.get(KEY_CACHE_ENABLE);
		if (null != cacheEnable) {
			CacheConfig.setCacheEnable(Boolean.valueOf(cacheEnable.toString()));
		}
		Object cacheObject = props.get(KEY_CACHE_OBJECT);
		if (null != cacheEnable) {
			CacheConfig.setCacheObject(Boolean.valueOf(cacheObject.toString()));
		}
	}

}

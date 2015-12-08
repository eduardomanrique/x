package br.com.jesm.x;

import java.io.Serializable;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

public class XCache {

	private static CacheManager cacheManager;

	private static synchronized final void startCacheManager() {
		if (cacheManager == null) {
			try {
				cacheManager = CacheManager.create();
			} catch (CacheException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static final Cache getCache(final String cacheName) {
		startCacheManager();
		if (cacheManager.getCache(cacheName) == null) {
			Cache cache = new Cache(cacheName, 100, false, false, 600, 600, false, 0);
			try {
				cacheManager.addCache(cache);
			} catch (CacheException e) {
				throw new RuntimeException(e);
			}
		}

		return cacheManager.getCache(cacheName);
	}

	private static final void addCache(final Cache cache) {
		try {
			cacheManager.addCache(cache);
		} catch (CacheException e) {
			throw new RuntimeException(e);
		}
	}

	public static final void clearAll() {
		try {
			for (String cacheName : cacheManager.getCacheNames()) {
				cacheManager.getCache(cacheName).removeAll();
				;
			}
		} catch (CacheException e) {
			throw new RuntimeException(e);
		}
	}

	public static final Object getCaheValue(final String cacheName, final String key) {
		try {
			if (getCache(cacheName).get(key) != null)
				return getCache(cacheName).get(key).getValue();
		} catch (CacheException e) {
			throw new RuntimeException(e);
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public static final <T extends Serializable> T getCaheValue(final String cacheName, final String key,
			CacheAction<T> actionOnNotFound) {

		T value = null;

		try {
			if (getCache(cacheName).get(key) != null) {
				value = (T) getCache(cacheName).get(key).getValue();
			} else {
				value = actionOnNotFound.execute();
				if (value != null) {
					addCaheValue(cacheName, key, value);
				}
			}
		} catch (CacheException e) {
			throw new RuntimeException(e);
		}
		return value;
	}

	public static final void addCaheValue(final String cacheName, final String key, final Serializable value) {
		getCache(cacheName).put(new Element(key, value));
	}

	public static final void removeCaheValue(final String cacheName, final String key) {
		getCache(cacheName).remove(key);
	}

	public static abstract class CacheAction<T> {
		public abstract T execute();
	}
}
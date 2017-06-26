package com.appleframework.cache.codis.lock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.appleframework.cache.codis.CodisResourcePool;
import com.appleframework.cache.core.lock.Lock;

import redis.clients.jedis.Jedis;

/**
 * Redis distributed lock implementation.
 *
 * @author cruise.xu
 */
public class CodisLock implements Lock {

	private CodisResourcePool codisResourcePool;

	private static final int DEFAULT_ACQUIRY_RESOLUTION_MILLIS = 100;

	/**
	 * ����ʱʱ�䣬��ֹ�߳��������Ժ����޵�ִ�еȴ�
	 */
	private int expireMsecs = 60 * 1000;

	/**
	 * ���ȴ�ʱ�䣬��ֹ�̼߳���
	 */
	private int timeoutMsecs = 10 * 1000;

	//private volatile boolean locked = false;
	
	private Map<String, Boolean> lockedMap = new ConcurrentHashMap<String, Boolean>();

	/**
	 * Detailed constructor with default acquire timeout 10000 msecs and lock
	 * expiration of 60000 msecs.
	 *
	 * @param lockKey
	 *            lock key (ex. account:1, ...)
	 */
	public CodisLock(CodisResourcePool codisResourcePool) {
		this.codisResourcePool = codisResourcePool;
	}

	/**
	 * Detailed constructor with default lock expiration of 60000 msecs.
	 *
	 */
	public CodisLock(CodisResourcePool codisResourcePool, int timeoutMsecs) {
		this(codisResourcePool);
		this.timeoutMsecs = timeoutMsecs;
	}

	/**
	 * Detailed constructor.
	 *
	 */
	public CodisLock(CodisResourcePool codisResourcePool, int timeoutMsecs, int expireMsecs) {
		this(codisResourcePool, timeoutMsecs);
		this.expireMsecs = expireMsecs;
	}

	private String get(final String key) {
		try (Jedis jedis = codisResourcePool.getResource()) {
			return jedis.get(key);
		} 
	}

	private boolean setNX(final String key, final String value) {
		try (Jedis jedis = codisResourcePool.getResource()) {
			Long result = jedis.setnx(key, value);
			return result == 0 ? false : true;
		}
	}

	private String getSet(final String key, final String value) {
		try (Jedis jedis = codisResourcePool.getResource()) {
			Object obj = jedis.getSet(key, value);
			return obj != null ? (String) obj : null;
		}
	}

	/**
	 * ��� lock. ʵ��˼·: ��Ҫ��ʹ����redis ��setnx����,��������. reids�����key������key,���еĹ���,
	 * value�����ĵ���ʱ��(ע��:����ѹ���ʱ�����value��,û��ʱ���������䳬ʱʱ��) ִ�й���:
	 * 1.ͨ��setnx��������ĳ��key��ֵ,�ɹ�(��ǰû�������)�򷵻�,�ɹ������
	 * 2.���Ѿ��������ȡ���ĵ���ʱ��,�͵�ǰʱ��Ƚ�,��ʱ�Ļ�,�������µ�ֵ
	 *
	 * @return true if lock is acquired, false acquire timeouted
	 * @throws InterruptedException
	 *             in case of thread interruption
	 */
	public synchronized boolean lock(String lockKey) throws InterruptedException {
		int timeout = timeoutMsecs;
		while (timeout >= 0) {
			long expires = System.currentTimeMillis() + expireMsecs + 1;
			String expiresStr = String.valueOf(expires); // ������ʱ��
			if (this.setNX(lockKey, expiresStr)) {
				// lock acquired
				//locked = true;
				lockedMap.put(lockKey, true);
				return true;
			}

			String currentValueStr = this.get(lockKey); // redis���ʱ��
			if (currentValueStr != null && Long.parseLong(currentValueStr) < System.currentTimeMillis()) {
				// �ж��Ƿ�Ϊ�գ���Ϊ�յ�����£�����������߳�������ֵ����ڶ��������ж��ǹ���ȥ��
				// lock is expired

				String oldValueStr = this.getSet(lockKey, expiresStr);
				// ��ȡ��һ��������ʱ�䣬���������ڵ�������ʱ�䣬
				// ֻ��һ���̲߳��ܻ�ȡ��һ�����ϵ�����ʱ�䣬��Ϊjedis.getSet��ͬ����
				if (oldValueStr != null && oldValueStr.equals(currentValueStr)) {
					// ��ֹ��ɾ�����ǣ���Ϊkey����ͬ�ģ������˵�����������ﲻ��Ч��������ֵ�ᱻ���ǣ�������Ϊʲô����˺��ٵ�ʱ�䣬���Կ��Խ���

					// [�ֲ�ʽ�������]:������ʱ�򣬶���߳�ǡ�ö������������ֻ��һ���̵߳�����ֵ�͵�ǰֵ��ͬ��������Ȩ����ȡ��
					// lock acquired
					//locked = true;
					lockedMap.put(lockKey, true);
					return true;
				}
			}
			timeout -= DEFAULT_ACQUIRY_RESOLUTION_MILLIS;

			/*
			 * �ӳ�100 ����, ����ʹ�����ʱ����ܻ��һ��,���Է�ֹ�������̵ĳ���,��,��ͬʱ����������,
			 * ֻ����һ�����̻����,�����Ķ���ͬ����Ƶ�ʽ��г���,����������һЩ����,Ҳ��ͬ����Ƶ��������,�⽫���ܵ���ǰ���������ò�������.
			 * ʹ������ĵȴ�ʱ�����һ���̶��ϱ�֤��ƽ��
			 */
			Thread.sleep(DEFAULT_ACQUIRY_RESOLUTION_MILLIS);

		}
		return false;
	}

	/**
	 * Acqurired lock release.
	 */
	public synchronized void unlock(String lockKey) {
		boolean locked = lockedMap.get(lockKey);
		if (locked) {
			try (Jedis jedis = codisResourcePool.getResource()) {
				jedis.del(lockKey);
			}
			locked = false;
		}
	}

}

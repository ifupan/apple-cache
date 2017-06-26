package com.appleframework.cache.jedis.lock;

import java.util.List;

import org.apache.log4j.Logger;

import com.appleframework.cache.core.lock.Lock;
import com.appleframework.cache.core.utils.SequenceUtility;
import com.appleframework.cache.jedis.factory.PoolFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;

/**
 * Redis distributed lock implementation.
 *
 * @author cruise.xu
 */
@SuppressWarnings("deprecation")
public class JedisLock implements Lock {

	private static Logger logger = Logger.getLogger(JedisLock.class);

	private PoolFactory poolFactory;

	/**
	 * ����ʱʱ�䣬��ֹ�߳��������Ժ����޵�ִ�еȴ�
	 */
	private long acquireTimeout = 60000;

	/**
	 * ���ȴ�ʱ�䣬��ֹ�̼߳���
	 */
	private long timeout = 10000;
	
	
	private static String sequence = SequenceUtility.getSequence();
	
	
	private String keyPrefix = "lock:";
	
	/**
	 * Detailed constructor with default acquire timeout 10000 msecs and lock
	 * expiration of 60000 msecs.
	 *
	 * @param lockKey
	 *            lock key (ex. account:1, ...)
	 */
	public JedisLock(PoolFactory poolFactory) {
		this.poolFactory = poolFactory;
	}

	/**
	 * Detailed constructor with default lock expiration of 60000 msecs.
	 *
	 */
	public JedisLock(PoolFactory poolFactory, long timeout) {
		this(poolFactory);
		this.timeout = timeout;
	}

	/**
	 * Detailed constructor.
	 *
	 */
	public JedisLock(PoolFactory poolFactory, long acquireTimeout, long timeout) {
		this(poolFactory, timeout);
		this.acquireTimeout = acquireTimeout;
	}
	
	private String genIdentifier() {
		return sequence + "-" + Thread.currentThread().getId();
	}
	
	/**
	 * ����
	 * 
	 * @param lockKey
	 *            ����key
	 * @param acquireTimeout
	 *            ��ȡ��ʱʱ��
	 * @param timeout
	 *            ���ĳ�ʱʱ��
	 * @return ����ʶ
	 */
	public void lock(String lockKey, long acquireTimeout, long timeout) {
		JedisPool jedisPool = poolFactory.getWritePool();
		Jedis jedis = jedisPool.getResource();
		try {
			// �������һ��value
			String identifier = this.genIdentifier();
			
			// ��������keyֵ
			lockKey = keyPrefix + lockKey;
			// ��ʱʱ�䣬�����󳬹���ʱ�����Զ��ͷ���
			int lockExpire = (int) (timeout / 1000);

			// ��ȡ���ĳ�ʱʱ�䣬�������ʱ���������ȡ��
			long end = System.currentTimeMillis() + acquireTimeout;
			while (System.currentTimeMillis() < end) {
				if (jedis.setnx(lockKey, identifier) == 1) {
					jedis.expire(lockKey, lockExpire);
					break;
				}
				// ����-1����keyû�����ó�ʱʱ�䣬Ϊkey����һ����ʱʱ��
				if (jedis.ttl(lockKey) == -1) {
					jedis.expire(lockKey, lockExpire);
				}
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		} catch (Exception e) {
			logger.error(e);
		} finally {
			jedisPool.returnResource(jedis);
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
	public void lock(String lockKey) {
		this.lock(lockKey, acquireTimeout, timeout);
	}
	
	@Override
	public boolean tryLock(String lockKey, long timeout) {
		boolean lockedSuccess = false;
		JedisPool jedisPool = poolFactory.getWritePool();
		Jedis jedis = jedisPool.getResource();
		try {
			// �������һ��value
			String identifier = this.genIdentifier();
			// ��������keyֵ
			lockKey = keyPrefix + lockKey;
			// ��ʱʱ�䣬�����󳬹���ʱ�����Զ��ͷ���
			int lockExpire = (int) (timeout / 1000);
			if (jedis.setnx(lockKey, identifier) == 1) {
				jedis.expire(lockKey, lockExpire);
				lockedSuccess = true;
			}
		} catch (Exception e) {
			logger.error(e);
		} finally {
			jedisPool.returnResource(jedis);
		}
		return lockedSuccess;
	}
	
	/**
     * ��� lock.
     * ʵ��˼·: ��Ҫ��ʹ����redis ��setnx����,��������.
     * reids�����key������key,���еĹ���, value�����ĵ���ʱ��(ע��:����ѹ���ʱ�����value��,û��ʱ���������䳬ʱʱ��)
     * ִ�й���:
     * 1.ͨ��setnx��������ĳ��key��ֵ,�ɹ�(��ǰû�������)�򷵻�,�ɹ������
     * 2.���Ѿ��������ȡ���ĵ���ʱ��,�͵�ǰʱ��Ƚ�,��ʱ�Ļ�,�������µ�ֵ
     *
     * @return true if lock is acquired, false acquire timeouted
     * @throws InterruptedException in case of thread interruption
     */
	@Override
	public boolean tryLock(String lockKey) {
		return this.tryLock(lockKey, timeout);
	}

	@Override
	public boolean isLocked(String lockKey) {
		boolean isLocked = false;
		JedisPool jedisPool = poolFactory.getWritePool();
		Jedis jedis = jedisPool.getResource();
		try {
			// �������һ��value
			String identifier = this.genIdentifier();
			// ��������keyֵ
			lockKey = keyPrefix + lockKey;
			String value = jedis.get(lockKey);
			if (null != value && value.equals(identifier)) {
				isLocked = true;
			}
		} catch (Exception e) {
			logger.error(e);
		} finally {
			jedisPool.returnResource(jedis);
		}
		return isLocked;
	}

	/**
	 * �ͷ���
	 * 
	 * @param lockName
	 *            ����key
	 * @param identifier
	 *            �ͷ����ı�ʶ
	 * @return
	 */
	public void unlock(String lockKey) {
		String identifier = this.genIdentifier();
		lockKey = keyPrefix + lockKey;
		JedisPool jedisPool = poolFactory.getWritePool();
		Jedis jedis = jedisPool.getResource();
		try {
			while (true) {
				// ����lock��׼����ʼ����
				jedis.watch(lockKey);
				// ͨ��ǰ�淵�ص�valueֵ�ж��ǲ��Ǹ��������Ǹ�������ɾ�����ͷ���
				String value = jedis.get(lockKey);
				if (identifier.equals(value)) {
					Transaction transaction = jedis.multi();
					transaction.del(lockKey);
					List<Object> results = transaction.exec();
					if (results == null) {
						continue;
					}
				}
				jedis.unwatch();
				break;
			}
		} catch (Exception e) {
			logger.error(e);
		} finally {
			jedisPool.returnResource(jedis);
		}
	}

}

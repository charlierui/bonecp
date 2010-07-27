/*

Copyright 2009 Wallace Wadge

This file is part of BoneCP.

BoneCP is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

BoneCP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with BoneCP.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.jolbox.bonecp;

import java.sql.SQLException;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.MapMaker;


/**
 * JDBC statement cache.
 *
 * @author wallacew
 */
public class StatementCache implements IStatementCache {
	/** Logger class. */
	private static Logger logger = LoggerFactory.getLogger(StatementCache.class);
	/** The cache of our statements. */
	private ConcurrentMap<String, StatementHandle> cache;
	/** How many items to cache. */
	private int cacheSize;


	/**
	 * Creates a statement cache of given size. 
	 *
	 * @param size of cache.
	 */
	public StatementCache(int size){
		this.cache = new MapMaker()
		.concurrencyLevel(32)
		.makeMap();

		this.cacheSize = size;
	}

	/** Simply appends the given parameters and returns it to obtain a cache key
	 * @param sql
	 * @param resultSetConcurrency
	 * @param resultSetHoldability
	 * @param resultSetType
	 * @return cache key to use
	 */
	public String calculateCacheKey(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability){
		StringBuffer tmp = calculateCacheKeyInternal(sql, resultSetType,
				resultSetConcurrency);

		tmp.append(", H:");
		tmp.append(resultSetHoldability);

		return tmp.toString();
	}

	/** Cache key calculation.
	 * @param sql string
	 * @param resultSetType
	 * @param resultSetConcurrency
	 * @return cache key
	 */
	public String calculateCacheKey(String sql, int resultSetType, int resultSetConcurrency){
		StringBuffer tmp = calculateCacheKeyInternal(sql, resultSetType,
				resultSetConcurrency);

		return tmp.toString();
	}

	/** Cache key calculation.
	 * @param sql
	 * @param resultSetType
	 * @param resultSetConcurrency
	 * @return cache key
	 */
	private StringBuffer calculateCacheKeyInternal(String sql,
			int resultSetType, int resultSetConcurrency) {
		StringBuffer tmp = new StringBuffer(sql.length()+20);
		tmp.append(sql);

		tmp.append(", T");
		tmp.append(resultSetType);
		tmp.append(", C");
		tmp.append(resultSetConcurrency);
		return tmp;
	}


	/** Alternate version of autoGeneratedKeys.
	 * @param sql
	 * @param autoGeneratedKeys
	 * @return cache key to use.
	 */
	public String calculateCacheKey(String sql, int autoGeneratedKeys) {
		StringBuffer tmp = new StringBuffer(sql.length()+4);
		tmp.append(sql);
		tmp.append(autoGeneratedKeys);
		return tmp.toString();
	}

	/** Calculate a cache key.
	 * @param sql to use
	 * @param columnIndexes to use
	 * @return cache key to use.
	 */
	public String calculateCacheKey(String sql, int[] columnIndexes) {
		StringBuffer tmp = new StringBuffer(sql.length()+4);
		tmp.append(sql);
		for (int i=0; i < columnIndexes.length; i++){
			tmp.append(columnIndexes[i]);
			tmp.append("CI,");
		}
		return tmp.toString();
	}

	/** Calculate a cache key.
	 * @param sql to use
	 * @param columnNames to use
	 * @return cache key to use.
	 */
	public String calculateCacheKey(String sql, String[] columnNames) {
		StringBuffer tmp = new StringBuffer(sql.length()+4);
		tmp.append(sql);
		for (int i=0; i < columnNames.length; i++){
			tmp.append(columnNames[i]);
			tmp.append("CN,");
		}
		return tmp.toString();

	}

	/**
	 * {@inheritDoc}
	 *
	 * @see com.jolbox.bonecp.IStatementCache#get(java.lang.String)
	 */
//	@Override
	public StatementHandle get(String key){
		StatementHandle statement = this.cache.get(key);
		
		if (statement != null && !statement.logicallyClosed.compareAndSet(true, false)){
			statement = null;
		}
		
		return statement;
	}

	// @Override
	public StatementHandle get(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
		return get(calculateCacheKey(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
	}


	// @Override
	public StatementHandle get(String sql, int resultSetType, int resultSetConcurrency) {
		return get(calculateCacheKey(sql, resultSetType, resultSetConcurrency));
	}

	// @Override
	public StatementHandle get(String sql, int autoGeneratedKeys) {
		return get(calculateCacheKey(sql, autoGeneratedKeys));
	}


	// @Override
	public StatementHandle get(String sql, int[] columnIndexes) {
		return get(calculateCacheKey(sql, columnIndexes));
	}


	// @Override
	public StatementHandle get(String sql, String[] columnNames) {
		return get(calculateCacheKey(sql, columnNames));
	}



	/**
	 * {@inheritDoc}
	 *
	 * @see com.jolbox.bonecp.IStatementCache#put(java.lang.String, com.jolbox.bonecp.StatementHandle)
	 */
	// @Override
	public void put(String key, StatementHandle handle){
		
		if (this.cache.size() <=  this.cacheSize && key != null){ // perhaps use LRU in future?? Worth the overhead? Hmm....
			if (this.cache.putIfAbsent(key, handle) == null){
				handle.inCache = true;
			}
		}
	

	}


	/**
	 * {@inheritDoc}
	 *
	 * @see com.jolbox.bonecp.IStatementCache#size()
	 */
	// @Override
	public int size(){
		return this.cache.size();
	}



	/**
	 * {@inheritDoc}
	 *
	 * @see com.jolbox.bonecp.IStatementCache#clear()
	 */
	// @Override
	public void clear() {
		for (StatementHandle statement: this.cache.values()){
			try {
				statement.internalClose();
			} catch (SQLException e) {
				logger.error("Error closing off statement", e);
			}
		}
		this.cache.clear();
	}

	// @Override
	public void checkForProperClosure() {
		for (StatementHandle statement: this.cache.values()){
			if (!statement.isClosed()){
				logger.error("Statement not closed properly in application\n\n"+statement.getOpenStackTrace());
			}
		}		
	}

}

/*
 * Copyright 2018 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.galbiston.expiring_map;

import static io.github.galbiston.expiring_map.MapDefaultValues.FULL_MAP_WARNING_INTERVAL;
import static io.github.galbiston.expiring_map.MapDefaultValues.MAP_CLEANER_INTERVAL;
import static io.github.galbiston.expiring_map.MapDefaultValues.MAP_EXPIRY_INTERVAL;
import static io.github.galbiston.expiring_map.MapDefaultValues.MINIMUM_MAP_CLEANER_INTERVAL;
import static io.github.galbiston.expiring_map.MapDefaultValues.UNLIMITED_INITIAL_CAPACITY;
import static io.github.galbiston.expiring_map.MapDefaultValues.UNLIMITED_MAP;
import java.lang.invoke.MethodHandles;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Expiring Map for storage of entries which expire if unused for a period of
 * time.
 * <br>Size of map, duration until expiry and frequency of cleaning can all be
 * controlled.
 *
 * @param <K> Key entry object.
 * @param <V> Value entry object.
 */
public class ExpiringMap<K, V> extends ConcurrentHashMap<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private String label;
    private long maxSize;
    private long expiryInterval;
    private long cleanerInterval;
    private long fullMapWarningInterval;
    private long fullMapWarning;
    private ExpiringMapCleaner mapCleaner;
    private Timer cleanerTimer;

    /**
     * Instance of Expiring Map that will remove items after a period of time
     * which have not been accessed.
     *
     * @param label Name of the map.
     * @param maxSize Maximum size of the map when items will no longer be
     * added. Unlimited size (-1) will still remove expired items.
     */
    public ExpiringMap(String label, int maxSize) {
        this(label, maxSize, MAP_EXPIRY_INTERVAL, MAP_CLEANER_INTERVAL, FULL_MAP_WARNING_INTERVAL);
    }

    /**
     * Instance of Expiring Map that will remove items after a period of time
     * which have not been accessed.
     *
     * @param label Name of the map.
     * @param maxSize Maximum size of the map when items will no longer be
     * added. Unlimited size (-1) will still remove expired items.
     * @param expiryInterval Duration that items remain in map.
     */
    public ExpiringMap(String label, int maxSize, long expiryInterval) {
        this(label, maxSize, expiryInterval, MAP_CLEANER_INTERVAL, FULL_MAP_WARNING_INTERVAL);
    }

    /**
     * Instance of Expiring Map that will remove items after a period of time
     * which have not been accessed.
     *
     * @param label Name of the map.
     * @param maxSize Maximum size of the map when items will no longer be
     * added. Unlimited size (-1) will still remove expired items.
     * @param expiryInterval Duration that items remain in map.
     * @param cleanerInterval Frequency that items are checked for removal from
     * map.
     */
    public ExpiringMap(String label, int maxSize, long expiryInterval, long cleanerInterval) {
        this(label, maxSize, expiryInterval, cleanerInterval, FULL_MAP_WARNING_INTERVAL);
    }

    /**
     * Instance of Expiring Map that will remove items after a period of time
     * which have not been accessed.
     *
     * @param label Name of the map.
     * @param maxSize Maximum size of the map when items will no longer be
     * added. Unlimited size (-1) will still remove expired items.
     * @param expiryInterval Duration that items remain in map.
     * @param cleanerInterval Frequency that items are checked for removal from
     * map.
     * @param fullMapWarningInterval Full map warning frequency.
     */
    public ExpiringMap(String label, int maxSize, long expiryInterval, long cleanerInterval, long fullMapWarningInterval) {
        super(maxSize > UNLIMITED_MAP ? maxSize : UNLIMITED_INITIAL_CAPACITY);
        this.label = label;
        this.maxSize = maxSize > UNLIMITED_MAP ? maxSize : Long.MAX_VALUE;
        setCleanerInterval(cleanerInterval);
        setExpiryInterval(expiryInterval);
        this.fullMapWarningInterval = fullMapWarningInterval;
        this.fullMapWarning = System.currentTimeMillis();
        this.mapCleaner = new ExpiringMapCleaner(this, expiryInterval);

        this.cleanerTimer = null;
    }

    @Override
    public V put(K key, V value) {
        if (super.mappingCount() < maxSize) {
            mapCleaner.put(key);
            return super.put(key, value);
        } else {
            long currentSystemTime = System.currentTimeMillis();
            long difference = currentSystemTime - fullMapWarning;
            if (difference > fullMapWarningInterval) {
                fullMapWarning = currentSystemTime;
                LOGGER.warn("{} Map Full: {} - Warning suppressed for {}ms", label, maxSize, fullMapWarningInterval);
            }
        }

        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        boolean isContained = super.containsKey(key);
        if (isContained) {
            mapCleaner.refresh(key);
        }
        return isContained;
    }

    @Override
    public V get(Object key) {
        V value = super.get(key);
        if (value != null) {
            mapCleaner.refresh(key);
        }
        return value;
    }

    @Override
    public void clear() {
        super.clear();
        mapCleaner.clear();
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize > UNLIMITED_MAP ? maxSize : Long.MAX_VALUE;
    }

    public long getCleanerInterval() {
        return cleanerInterval;
    }

    public final void setCleanerInterval(long cleanerInterval) {
        if (MINIMUM_MAP_CLEANER_INTERVAL < cleanerInterval) {
            this.cleanerInterval = cleanerInterval;
        } else {
            LOGGER.warn("Cleaner Interval: {} less than minimum: {}. Setting to minimum.", cleanerInterval, MINIMUM_MAP_CLEANER_INTERVAL);
            this.cleanerInterval = MINIMUM_MAP_CLEANER_INTERVAL;
        }
    }

    public long getExpiryInterval() {
        return expiryInterval;
    }

    public final void setExpiryInterval(long expiryInterval) {

        long minimum_interval = cleanerInterval + 1;
        if (expiryInterval < minimum_interval) {
            LOGGER.warn("Expiry Interval: {} cannot be less than Cleaner Interval: {}. Setting to Minimum Interval: {}", expiryInterval, cleanerInterval, minimum_interval);
            this.expiryInterval = minimum_interval;
        } else {
            this.expiryInterval = expiryInterval;
        }

        if (this.mapCleaner != null) {
            this.mapCleaner.setExpiryInterval(this.expiryInterval);
        }
    }

    public long getFullMapWarningInterval() {
        return fullMapWarningInterval;
    }

    public void setFullMapWarningInterval(long fullMapWarningInterval) {
        this.fullMapWarningInterval = fullMapWarningInterval;
    }

    public long getFullMapWarning() {
        return fullMapWarning;
    }

    public void setFullMapWarning(long fullMapWarning) {
        this.fullMapWarning = fullMapWarning;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void startExpiry() {
        startExpiry(cleanerInterval);
    }

    public void startExpiry(long cleanerInterval) {
        if (cleanerTimer == null) {
            setCleanerInterval(cleanerInterval);
            cleanerTimer = new Timer(label, true);
            mapCleaner = new ExpiringMapCleaner(mapCleaner);
            cleanerTimer.scheduleAtFixedRate(mapCleaner, this.cleanerInterval, this.cleanerInterval);
        }
    }

    public void stopExpiry() {
        if (cleanerTimer != null) {
            cleanerTimer.cancel();
            cleanerTimer = null;
        }
    }

    @Override
    public String toString() {
        return "ExpiringMap{" + "label=" + label + ", maxSize=" + maxSize + ", expiryInterval=" + expiryInterval + ", cleanerInterval=" + cleanerInterval + ", fullMapWarningInterval=" + fullMapWarningInterval + ", fullMapWarning=" + fullMapWarning + ", mapCleaner=" + mapCleaner + ", cleanerTimer=" + cleanerTimer + '}';
    }

}

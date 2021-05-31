/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package dk.kb.labsapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Special purpose cache for Solr requests.
 */
public class TimeCache<O> implements Map<String, O> {
    private static final Logger log = LoggerFactory.getLogger(TimeCache.class);

    private final LinkedHashMap<String, TimeEntry<O>> inner;
    private final int maxCapacity;
    private final long maxAge;

    /**
     *
     * @param maxCapacity the maximum numbers of entries to hold in the cache.
     * @param maxAgeMS    the maximum number og milliseconds that an object can exist in the cache.
     */
    public TimeCache(int maxCapacity, long maxAgeMS) {
        super();
        this.maxCapacity = maxCapacity;
        this.maxAge = maxAgeMS;
        this.inner = new LinkedHashMap<String, TimeEntry<O>>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TimeEntry<O>> eldest) {
                return size() > maxCapacity || eldest.getValue().isTooOld();
            }
        };
    }


    /**
     * Get the object with the given key from the cache. If the object is not available, attempt to create a new one
     * using the supplier. If a new object is created, add it to the cache and return it.
     *
     * If the key is null, no caching is attempted and the supplier is called directly.
     * @param key      the key for the object to retrieve.
     * @param supplier used for creating the object if is is not available.
     * @return the object corresponding to the key.
     */
    public O get(String key, Supplier<O> supplier) {
        if (key == null) {
            log.debug("get(null, ...) called: No caching is performed");
            return supplier.get();
        }
        O o = get(key);
        if (o == null) {
            o = supplier.get();
            if (o != null) {
                put(key, o);
            }
        }
        return o;
    }

    @Override
    public O get(Object key) {
        if (!(key instanceof String)) {
            return null;
        }
        TimeEntry<O> o = inner.get(key);
        if (o == null) {
            return null;
        }
        if (o.isTooOld()) {
            inner.remove(key);
            return null;
        }
        inner.put((String)key, o);
        return o.getValue();
    }

    @Override
    public O getOrDefault(Object key, O defaultValue) {
        return Optional.ofNullable(get(key)).orElse(defaultValue);
    }

    @Override
    public int size() {
        return inner.size();
    }

    @Override
    public boolean isEmpty() {
        return inner.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return inner.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return inner.values().stream()
                .map(TimeEntry::getValue)
                .anyMatch(value::equals);
    }

    @Override
    public O put(String key, O value) {
        TimeEntry<O> entry = new TimeEntry<>(value);
        return Optional.ofNullable(inner.put(key, entry))
                .map(TimeEntry::getValue)
                .orElse(null);
    }

    @Override
    public O remove(Object key) {
        return Optional.ofNullable(inner.remove(key))
                .map(TimeEntry::getValue)
                .orElse(null);
    }

    @Override
    public void putAll(Map<? extends String, ? extends O> m) {
        m.forEach((key, value) -> inner.put(key, new TimeEntry<>(value)));
    }

    @Override
    public void clear() {
        inner.clear();
    }

    @Override
    public Set<String> keySet() {
        return inner.keySet();
    }

    @Override
    public Collection<O> values() {
        return inner.values().stream()
                .map(TimeEntry::getValue)
                .collect(Collectors.toList());
    }

    @Override
    public Set<Entry<String, O>> entrySet() {
        return inner.entrySet().stream()
                .map(e -> new AbstractMap.SimpleImmutableEntry<>(e.getKey(), e.getValue().getValue()))
                .collect(Collectors.toSet());
    }

    /* Helper class */

    public class TimeEntry<O> {
        private final O value;
        private final Instant created = Instant.now();

        public TimeEntry(O o) {
            this.value = o;
        }

        public Instant getCreated() {
            return created;
        }

        public O getValue() {
            return value;
        }

        public boolean isTooOld() {
            return getCreated().plus(maxAge, ChronoUnit.MILLIS).isBefore(Instant.now());
        }
    }
}

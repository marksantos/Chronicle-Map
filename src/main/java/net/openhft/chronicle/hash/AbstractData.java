/*
 *      Copyright (C) 2015  higherfrequencytrading.com
 *
 *      This program is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU Lesser General Public License as published by
 *      the Free Software Foundation, either version 3 of the License.
 *
 *      This program is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU Lesser General Public License for more details.
 *
 *      You should have received a copy of the GNU Lesser General Public License
 *      along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.chronicle.hash;

import net.openhft.chronicle.algo.hashing.LongHashFunction;

/**
 * Defines reasonable defaults for {@code Data}'s {@code equals()}, {@code hashCode()} and
 * {@code toString()}. They should be default implementations in the {@code Data} interface itself,
 * but Java 8 doesn't allow to override {@code Object}'s methods by default implementations
 * in interfaces.
 */
public abstract class AbstractData<V> implements Data<V> {

    /**
     * Constructor for use by subclasses. 
     */
    protected AbstractData() {}

    /**
     * Computes value's hash code by applying a hash function to {@code Data}'s <i>bytes</i>
     * representation.
     */
    @Override
    public int hashCode() {
        return (int) hash(LongHashFunction.city_1_1());
    }

    /**
     * Compares {@code Data}s' <i>bytes</i> representations.
     */
    @Override
    public boolean equals(Object obj) {
        return obj != null &&
                obj instanceof Data &&
                Data.bytesEquivalent(this, (Data<?>) obj);
    }

    /**
     * Delegates to {@code Data}'s <i>object</i> {@code toString()}.
     */
    @Override
    public String toString() {
        return get().toString();
    }
}

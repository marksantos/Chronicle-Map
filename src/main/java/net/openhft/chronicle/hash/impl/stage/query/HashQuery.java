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

package net.openhft.chronicle.hash.impl.stage.query;

import net.openhft.chronicle.hash.Data;
import net.openhft.chronicle.hash.HashEntry;
import net.openhft.chronicle.hash.impl.VanillaChronicleHashHolder;
import net.openhft.chronicle.hash.impl.stage.data.bytes.InputKeyBytesData;
import net.openhft.chronicle.hash.impl.stage.data.instance.InputKeyInstanceData;
import net.openhft.chronicle.hash.impl.stage.entry.HashEntryStages;
import net.openhft.chronicle.hash.impl.stage.entry.HashLookupPos;
import net.openhft.chronicle.hash.impl.stage.entry.HashLookupSearch;
import net.openhft.chronicle.hash.impl.stage.entry.SegmentStages;
import net.openhft.chronicle.hash.impl.stage.hash.CheckOnEachPublicOperation;
import net.openhft.sg.Stage;
import net.openhft.sg.StageRef;
import net.openhft.sg.Staged;

import static net.openhft.chronicle.hash.impl.stage.query.KeySearch.SearchState.DELETED;
import static net.openhft.chronicle.hash.impl.stage.query.KeySearch.SearchState.PRESENT;

@Staged
public abstract class HashQuery<K> implements HashEntry<K> {

    @StageRef public VanillaChronicleHashHolder<K, ?, ?> hh;
    @StageRef public SegmentStages s;
    @StageRef public HashEntryStages<K> entry;
    @StageRef public HashLookupSearch hashLookupSearch;
    @StageRef public CheckOnEachPublicOperation checkOnEachPublicOperation;
    @StageRef public HashLookupPos hlp;
    @StageRef public KeySearch ks;
    
    @StageRef public InputKeyInstanceData<K, ?, ?> inputKeyInstanceValue;
    @StageRef public InputKeyBytesData<K> inputKeyBytesValue;
    
    public void dropSearchIfNestedContextsAndPresentHashLookupSlotCheckFailed() {
        if (s.locksInit()) {
            if (s.nestedContextsLockedOnSameSegment &&
                    s.rootContextLockedOnThisSegment.latestSameThreadSegmentModCount() !=
                            s.contextModCount) {
                if (ks.keySearchInit()) {
                    if (ks.searchState == PRESENT) {
                        if (!hashLookupSearch.checkSlotContainsExpectedKeyAndValue(entry.pos))
                            hlp.closeHashLookupPos();
                    }
                }
            }
        }
    }

    public Data<K> queriedKey() {
        checkOnEachPublicOperation.checkOnEachPublicOperation();
        return ks.inputKey;
    }

    public enum EntryPresence {PRESENT, ABSENT}

    @Stage("PresenceOfEntry") private EntryPresence entryPresence = null;

    private void initPresenceOfEntry() {
        if (ks.searchStatePresent() || tieredEntryPresent()) {
            entryPresence = EntryPresence.PRESENT;
        } else {
            entryPresence = EntryPresence.ABSENT;
        }
    }

    public void initPresenceOfEntry(EntryPresence entryPresence) {
        this.entryPresence = entryPresence;
    }

    private boolean tieredEntryPresent() {
        int firstTier = s.segmentTier;
        long firstTierBaseAddr = s.segmentBaseAddr;
        while (true) {
            if (s.hasNextTier()) {
                s.nextTier();
            } else {
                if (s.segmentTier != 0)
                    s.initSegmentTier(); // loop to the root tier
            }
            if (s.segmentBaseAddr == firstTierBaseAddr)
                break;
            if (ks.searchStatePresent())
                return true;
        }
        // not found
        if (firstTier != 0) {
            // key is absent; probably are going to allocate a new entry;
            // start trying from the root tier
            s.initSegmentTier();
        }
        return false;
    }

    public boolean entryPresent() {
        return entryPresence == EntryPresence.PRESENT;
    }

    @Override
    public void doRemove() {
        checkOnEachPublicOperation.checkOnEachPublicOperation();
        s.innerUpdateLock.lock();
        if (ks.searchStatePresent()) {
            // TODO optimize: if shift-deletion is trivial, updateLock.lock()
            s.innerWriteLock.lock();
            hashLookupSearch.remove();
            entry.innerRemoveEntryExceptHashLookupUpdate();
            ks.setSearchState(DELETED);
            initPresenceOfEntry(EntryPresence.ABSENT);
        } else {
            throw new IllegalStateException("Entry is absent when doRemove() is called");
        }
    }
}

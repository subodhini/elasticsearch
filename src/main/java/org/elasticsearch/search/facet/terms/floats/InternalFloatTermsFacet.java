/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.facet.terms.floats;

import com.google.common.collect.ImmutableList;
import gnu.trove.iterator.TFloatIntIterator;
import gnu.trove.map.hash.TFloatIntHashMap;
import org.elasticsearch.common.CacheRecycler;
import org.elasticsearch.common.collect.BoundedTreeSet;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.terms.InternalTermsFacet;
import org.elasticsearch.search.facet.terms.TermsFacet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 *
 */
public class InternalFloatTermsFacet extends InternalTermsFacet {

    private static final String STREAM_TYPE = "fTerms";

    public static void registerStream() {
        Streams.registerStream(STREAM, STREAM_TYPE);
    }

    static Stream STREAM = new Stream() {
        @Override
        public Facet readFacet(String type, StreamInput in) throws IOException {
            return readTermsFacet(in);
        }
    };

    @Override
    public String streamType() {
        return STREAM_TYPE;
    }

    public static class FloatEntry implements Entry {

        float term;
        int count;

        public FloatEntry(float term, int count) {
            this.term = term;
            this.count = count;
        }

        public String term() {
            return Float.toString(term);
        }

        public String getTerm() {
            return term();
        }

        @Override
        public Number termAsNumber() {
            return term;
        }

        @Override
        public Number getTermAsNumber() {
            return termAsNumber();
        }

        public int count() {
            return count;
        }

        public int getCount() {
            return count();
        }

        @Override
        public int compareTo(Entry o) {
            float anotherVal = ((FloatEntry) o).term;
            if (term < anotherVal) {
                return -1;
            }
            if (term == anotherVal) {
                int i = count - o.count();
                if (i == 0) {
                    i = System.identityHashCode(this) - System.identityHashCode(o);
                }
                return i;
            }
            return 1;
        }
    }

    private String name;

    int requiredSize;

    long missing;
    long total;

    Collection<FloatEntry> entries = ImmutableList.of();

    ComparatorType comparatorType;

    InternalFloatTermsFacet() {
    }

    public InternalFloatTermsFacet(String name, ComparatorType comparatorType, int requiredSize, Collection<FloatEntry> entries, long missing, long total) {
        this.name = name;
        this.comparatorType = comparatorType;
        this.requiredSize = requiredSize;
        this.entries = entries;
        this.missing = missing;
        this.total = total;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String getType() {
        return type();
    }

    @Override
    public List<FloatEntry> entries() {
        if (!(entries instanceof List)) {
            entries = ImmutableList.copyOf(entries);
        }
        return (List<FloatEntry>) entries;
    }

    @Override
    public List<FloatEntry> getEntries() {
        return entries();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public Iterator<Entry> iterator() {
        return (Iterator) entries.iterator();
    }

    @Override
    public long missingCount() {
        return this.missing;
    }

    @Override
    public long getMissingCount() {
        return missingCount();
    }

    @Override
    public long totalCount() {
        return this.total;
    }

    @Override
    public long getTotalCount() {
        return totalCount();
    }

    @Override
    public long otherCount() {
        long other = total;
        for (Entry entry : entries) {
            other -= entry.count();
        }
        return other;
    }

    @Override
    public long getOtherCount() {
        return otherCount();
    }

    @Override
    public Facet reduce(String name, List<Facet> facets) {
        if (facets.size() == 1) {
            return facets.get(0);
        }
        InternalFloatTermsFacet first = (InternalFloatTermsFacet) facets.get(0);
        TFloatIntHashMap aggregated = CacheRecycler.popFloatIntMap();
        long missing = 0;
        long total = 0;
        for (Facet facet : facets) {
            InternalFloatTermsFacet mFacet = (InternalFloatTermsFacet) facet;
            missing += mFacet.missingCount();
            total += mFacet.totalCount();
            for (FloatEntry entry : mFacet.entries) {
                aggregated.adjustOrPutValue(entry.term, entry.count(), entry.count());
            }
        }

        BoundedTreeSet<FloatEntry> ordered = new BoundedTreeSet<FloatEntry>(first.comparatorType.comparator(), first.requiredSize);
        for (TFloatIntIterator it = aggregated.iterator(); it.hasNext(); ) {
            it.advance();
            ordered.add(new FloatEntry(it.key(), it.value()));
        }
        first.entries = ordered;
        first.missing = missing;
        first.total = total;

        CacheRecycler.pushFloatIntMap(aggregated);

        return first;
    }

    static final class Fields {
        static final XContentBuilderString _TYPE = new XContentBuilderString("_type");
        static final XContentBuilderString MISSING = new XContentBuilderString("missing");
        static final XContentBuilderString TOTAL = new XContentBuilderString("total");
        static final XContentBuilderString OTHER = new XContentBuilderString("other");
        static final XContentBuilderString TERMS = new XContentBuilderString("terms");
        static final XContentBuilderString TERM = new XContentBuilderString("term");
        static final XContentBuilderString COUNT = new XContentBuilderString("count");
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(name);
        builder.field(Fields._TYPE, TermsFacet.TYPE);
        builder.field(Fields.MISSING, missing);
        builder.field(Fields.TOTAL, total);
        builder.field(Fields.OTHER, otherCount());
        builder.startArray(Fields.TERMS);
        for (FloatEntry entry : entries) {
            builder.startObject();
            builder.field(Fields.TERM, entry.term);
            builder.field(Fields.COUNT, entry.count());
            builder.endObject();
        }
        builder.endArray();
        builder.endObject();
        return builder;
    }

    public static InternalFloatTermsFacet readTermsFacet(StreamInput in) throws IOException {
        InternalFloatTermsFacet facet = new InternalFloatTermsFacet();
        facet.readFrom(in);
        return facet;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        name = in.readUTF();
        comparatorType = ComparatorType.fromId(in.readByte());
        requiredSize = in.readVInt();
        missing = in.readVLong();
        total = in.readVLong();

        int size = in.readVInt();
        entries = new ArrayList<FloatEntry>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new FloatEntry(in.readFloat(), in.readVInt()));
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeUTF(name);
        out.writeByte(comparatorType.id());
        out.writeVInt(requiredSize);
        out.writeVLong(missing);
        out.writeVLong(total);

        out.writeVInt(entries.size());
        for (FloatEntry entry : entries) {
            out.writeFloat(entry.term);
            out.writeVInt(entry.count());
        }
    }
}
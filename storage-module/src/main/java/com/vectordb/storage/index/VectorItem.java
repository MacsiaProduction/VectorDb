package com.vectordb.storage.index;

import com.github.jelmerk.hnswlib.core.Item;
import com.vectordb.common.model.VectorEntry;
import lombok.Getter;


/**
 * Wrapper class to make VectorEntry compatible with hnswlib Item interface
 */
public class VectorItem implements Item<String, float[]> {
    private final String id;
    private final float[] vector;
    @Getter
    private final VectorEntry entry;

    VectorItem(String id, float[] vector, VectorEntry entry) {
        this.id = id;
        this.vector = vector;
        this.entry = entry;
    }

    public static VectorItem fromVectorEntry(VectorEntry entry) {
        // Convert double[] to float[] for hnswlib compatibility
        float[] floatVector = new float[entry.embedding().length];
        for (int i = 0; i < entry.embedding().length; i++) {
            floatVector[i] = (float) entry.embedding()[i];
        }
        return new VectorItem(entry.id(), floatVector, entry);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public float[] vector() {
        return vector;
    }

    @Override
    public int dimensions() {
        return vector.length;
    }
}
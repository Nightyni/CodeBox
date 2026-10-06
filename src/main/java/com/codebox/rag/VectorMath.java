package com.codebox.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Vector helpers shared by the index and the LLM embedding client. */
@Component
public class VectorMath {

    public String serialize(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    public float[] deserialize(String text) {
        if (text == null || text.isBlank()) return new float[0];
        String[] parts = text.split(",");
        List<Float> values = new ArrayList<>(parts.length);
        for (String p : parts) {
            try {
                values.add(Float.parseFloat(p.trim()));
            } catch (NumberFormatException ignored) {
                // skip malformed component rather than failing the whole query
            }
        }
        float[] out = new float[values.size()];
        for (int i = 0; i < out.length; i++) out[i] = values.get(i);
        return out;
    }

    /** Cosine similarity; vectors are usually pre-normalised so this is a dot product. */
    public double cosine(float[] a, float[] b) {
        if (a.length == 0 || b.length == 0 || a.length != b.length) return 0d;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0d;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}

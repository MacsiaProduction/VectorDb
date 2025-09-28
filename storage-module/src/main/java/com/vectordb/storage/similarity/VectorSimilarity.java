package com.vectordb.storage.similarity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class VectorSimilarity {
    
    /**
     * Calculate Euclidean distance between two vectors
     */
    public double euclideanDistance(double[] vector1, double[] vector2) {
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }
        
        double sum = 0.0;
        for (int i = 0; i < vector1.length; i++) {
            double diff = vector1[i] - vector2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
    
    /**
     * Calculate cosine similarity between two vectors
     */
    public double cosineSimilarity(double[] vector1, double[] vector2) {
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            normA += vector1[i] * vector1[i];
            normB += vector2[i] * vector2[i];
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * Calculate Manhattan (L1) distance between two vectors
     */
    public double manhattanDistance(double[] vector1, double[] vector2) {
        if (vector1.length != vector2.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }
        
        double sum = 0.0;
        for (int i = 0; i < vector1.length; i++) {
            sum += Math.abs(vector1[i] - vector2[i]);
        }
        return sum;
    }

}

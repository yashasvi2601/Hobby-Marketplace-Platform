package backend.hobbiebackend.service;

public interface EmbeddingService {

    /**
     * Returns the embedding vector for the given text, or null if the embedding
     * provider is not configured or the request fails. Callers must treat null
     * as "embeddings unavailable" and fall back to a non-AI matching strategy.
     */
    double[] embed(String text);
}

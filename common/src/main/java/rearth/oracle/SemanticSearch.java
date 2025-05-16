package rearth.oracle;

import ai.djl.engine.Engine;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import rearth.oracle.util.MarkdownParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SemanticSearch {
    
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final AllMiniLmL6V2QuantizedEmbeddingModel embeddingModel;
    private final EmbeddingStoreIngestor ingestor;
    
    private final AtomicLong TOTAL_EMBEDDING_TIME = new AtomicLong(0);
    private final AtomicInteger PENDING_JOBS = new AtomicInteger(0);
    
    public SemanticSearch() {
        
        embeddingStore = new InMemoryEmbeddingStore<>();
        
        // workaround for weird neoforge different class loading issues?
        var original = Thread.currentThread().getContextClassLoader();
        try {
            // Inject the class loader that actually has the DJL engine resources
            Thread.currentThread().setContextClassLoader(Engine.class.getClassLoader());
            embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        } finally {
            // Restore the original loader to avoid side‑effects
            Thread.currentThread().setContextClassLoader(original);
        }
        
        ingestor = EmbeddingStoreIngestor.builder()
                     .embeddingStore(embeddingStore)
                     .embeddingModel(embeddingModel)
                     .documentSplitter(DocumentSplitters.recursive(500, 50))
                     .build();
        
        // generate embeddings for all found entries
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var resources = resourceManager.findResources("books", path -> path.getPath().endsWith(".mdx"));
        
        for (var resourceId : resources.keySet()) {
            var purePath = resourceId.getPath().replaceFirst("books/", "");
            var segments = purePath.split("/");
            var modId = segments[0];        // e.g. "oritech"
            var entryPath = purePath.replaceFirst(modId + "/", ""); // e.g. "tools/wrench.mdx"
            var entryFileName = segments[segments.length - 1]; // e.g. "wrench.mdx"
            var entryDirectory = entryPath.replace(entryFileName, ""); // e.g. "tools" or "processing/reactor"
            
            if (entryDirectory.startsWith(".translated")) continue; // skip / don't support translations for now
            
            try {
                var fileContent = new String(resources.get(resourceId).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                var fileComponents = MarkdownParser.parseFrontmatter(fileContent);
                
                // generate embeddings
                this.queueEmbeddingsJob(modId, entryDirectory, entryFileName, fileComponents, fileContent);
                
            } catch (IOException e) {
                Oracle.LOGGER.error("Unable to load book with id: " + resourceId);
                throw new RuntimeException(e);
            }
        }
        
    }
    
    public boolean isReady() {
        return PENDING_JOBS.get() <= 0;
    }
    
    public long getEmbeddingTime() {
        return TOTAL_EMBEDDING_TIME.get();
    }
    
    public ArrayList<SearchResult> search(String query) {
        var queryEmbedding = embeddingModel.embed(query).content();
        
        var searchRequest = EmbeddingSearchRequest.builder()
                              .queryEmbedding(queryEmbedding)
                              .maxResults(15)
                              .minScore(0.6)
                              .build();
        
        var matches = embeddingStore.search(searchRequest).matches();
        var results = new ArrayList<SearchResult>();
        
        for (var match : matches) {
            
            var id = match.embedded().metadata().getString("book") + ":" + match.embedded().metadata().getString("category") + match.embedded().metadata().getString("fileName");
            var title = match.embedded().metadata().getString("title");
            if (title == null) title = "No title";
            
            // check if id already exists, add it to alt texts
            var existingCandidate = results.stream().filter(result -> result.id.equals(Identifier.of(id))).findFirst();
            if (existingCandidate.isPresent()) {
                existingCandidate.get().texts.add(match.embedded().text());
            } else {
                var list = new ArrayList<String>();
                list.add(match.embedded().text());
                var step = new SearchResult(list, match.score(), title, Identifier.of(id), match.embedded().metadata().getString("icon"));
                results.add(step);
            }
        }
        
        return results;
        
    }
    
    public void queueEmbeddingsJob(String bookId, String filePath, String fileName, Map<String, String> frontmatter, String content) {
        
        CompletableFuture.runAsync(() -> {
            PENDING_JOBS.addAndGet(1);
            var startedAt = System.nanoTime();
            var document = Document.from(content, Metadata.from(frontmatter));
            document.metadata().put("fileName", fileName);
            document.metadata().put("category", filePath);
            document.metadata().put("book", bookId);
            ingestor.ingest(document);
            var takenTime = System.nanoTime() - startedAt;
            TOTAL_EMBEDDING_TIME.addAndGet(takenTime);
            PENDING_JOBS.decrementAndGet();
        });
    }
    
    public record SearchResult(List<String> texts, double bestScore, String title, Identifier id, String iconName) {
    }
    
}

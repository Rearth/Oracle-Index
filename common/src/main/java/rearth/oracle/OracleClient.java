package rearth.oracle;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
import rearth.oracle.ui.OracleScreen;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class OracleClient {
    
    public static final KeyBinding ORACLE_WIKI = new KeyBinding("key.oracle_wiki.open", GLFW.GLFW_KEY_H, "key.categories.misc");
    
    public static final Set<ResourceEntry> RESOURCE_ENTRIES = new HashSet<>();

    public static void init() {
        Oracle.LOGGER.info("Hello from the Oracle Wiki Client!");
        
        KeyMappingRegistry.register(ORACLE_WIKI);
        
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (ORACLE_WIKI.wasPressed()) {
                Oracle.LOGGER.info("Opening Oracle Wiki...");
                client.setScreen(new OracleScreen());
            }
        });
        
        ClientLifecycleEvent.CLIENT_STARTED.register(client -> {
            Oracle.LOGGER.info("Indexing entry items...");
            findAllResourceEntries();
        });
        
    }
    
    private static void findAllResourceEntries() {
        var resourceManager = MinecraftClient.getInstance().getResourceManager();
        var resources = resourceManager.findResources("books", path -> path.getPath().endsWith(".mdx"));
        
        RESOURCE_ENTRIES.clear();
        
        for (var resourceId : resources.keySet()) {
            var purePath = resourceId.getPath().replaceFirst("books/", "");
            var segments = purePath.split("/");
            var modId = segments[0];        // e.g. "oritech"
            var entryPath = purePath.replaceFirst(modId + "/", ""); // e.g. "tools/wrench.mdx"
            var entryFileName = segments[segments.length - 1]; // e.g. "wrench.mdx"
            var entryDirectory = entryPath.replace(entryFileName, ""); // e.g. "tools" or "processing/reactor"
            var entry = new ResourceEntry(modId, entryDirectory.split("/"), entryFileName);
            
            RESOURCE_ENTRIES.add(entry);
        }
    }
    
    public record ResourceEntry(String bookId, String[] entryDirectory, String entryFileName) {
        @Override
        public String toString() {
            return "ResourceEntry{" +
                     "bookId=" + bookId +
                     ", entryDirectory=" + Arrays.toString(entryDirectory) +
                     ", entryFileName='" + entryFileName + '\'' +
                     '}';
        }
    }
    
}

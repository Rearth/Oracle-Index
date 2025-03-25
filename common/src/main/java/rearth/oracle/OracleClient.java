package rearth.oracle;

import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import io.wispforest.owo.ui.util.Delta;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import rearth.oracle.ui.OracleScreen;
import rearth.oracle.util.MarkdownParser;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class OracleClient {
    
    public static final KeyBinding ORACLE_WIKI = new KeyBinding("key.oracle_index.open", GLFW.GLFW_KEY_H, "key.categories.misc");

    public static final Set<String> LOADED_BOOKS = new HashSet<>();
    public static final HashMap<Identifier, BookItemLink> ITEM_LINKS = new HashMap<>();
    
    public static ItemStack tooltipStack;
    public static float openEntryProgress = 0;

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
        
        ClientTickEvent.CLIENT_POST.register(client -> {
            if (Screen.hasAltDown()) return;
            openEntryProgress += Delta.compute(openEntryProgress, 0, 0.13f);
        });
        
    }
    
    /**
     * Opens the Oracle Screen, potentially setting the active book and entry.
     *
     * @param bookId  The ID of the book to activate. If null, the last active book remains active.
     * @param entryId The Identifier of the entry to activate. If null, the currently active entry remains active. Example format: {@code oracle_index:books/oritech/interaction/enderic_laser.mdx}
     * @param parent  The parent screen. This is the screen that will be returned to when the wiki is closed. Usually just {@code MinecraftClient.getInstance().currentScreen} works here.
     *
     * @warning If {@code entryId} is set, {@code bookId} should generally also be set to ensure the correct book is active.
     *          Otherwise, the behavior depends on the currently active book, and could lead to unexpected results. Only omit
     *          {@code bookId} if you are certain that the correct book is already active.
     */
    public static void openScreen(@Nullable String bookId, @Nullable Identifier entryId, @Nullable Screen parent) {
        if (bookId != null)
            OracleScreen.activeBook = bookId;
        if (entryId != null)
            OracleScreen.activeEntry = entryId;
        
        MinecraftClient.getInstance().setScreen(new OracleScreen(parent));
    }

    private static final HashMap<String, String> loadedModIds_Urls = new HashMap<>();

    private static String mainDownloadsFolder = "";

    /**Called in the loader specific settings.
     * These is a map of the laoded mods and their git repo link
     */
    public static void setModIdAndUrls(HashMap<String, String> ids_urls){
        loadedModIds_Urls.putAll(ids_urls);
    }

    /**Sets where the repos will be downloaded*/
    public static void setMainDownloadsFolder(String folder){
        mainDownloadsFolder = folder;
    }

    public static String getDownloadedWikisFolder(){
        return mainDownloadsFolder+"/"+Oracle.MOD_ID+"/downloaded_wikis/";
    }

    /**Downloads the repository from github, from the given url*/
    private static boolean pullFromWeb(String id, String url){
        if(url.equals("-")){
            return false;
        }
        try {
            createDownloadedWikisResourcePack();
        } catch (IOException e) {
            Oracle.LOGGER.error("Could not create the downloaded wikis' resourcepack folder!");
            throw new RuntimeException(e);
        }
        File dirPath = new File(getDownloadedWikisFolder()+id+"/");
        try {
            //TODO find a way to download only somthing from the repo
            //TODO add option to regenerate the repository/update it or something.

            //TODO migrate the dir path directly to resource folder?
            Git git = Git.cloneRepository()
                    .setURI(url)
                    .setDirectory(dirPath)
                    .call();

            sanitizeDirectory(dirPath);


            //The copying over to the resource pack
            try {
                FileUtils.moveDirectory(new File(dirPath+"/docs/"+id+"/"), new File(getBooksFolder()+id+"/"));
                FileUtils.deleteDirectory(new File(dirPath+"/docs/"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return true;
        } catch (GitAPIException e) {
            Oracle.LOGGER.error("BIG ERRORR GIT HUB API THINGY");
            throw new RuntimeException(e);
        } catch (JGitInternalException e){
            //means it already exists
            //TODO maybe do this? Git.open(dirPath).fetch().call();
            Oracle.LOGGER.info("---------Already downloaded!!!!!!");
            return true;
        }
    }

    public static void createDownloadedWikisResourcePack() throws IOException {
        File packmeta = new File(MinecraftClient.getInstance().getResourcePackDir().toString()+"/oracleDownloadedWikis/pack.mcmeta");

        if(!packmeta.exists()){
            FileUtils.createParentDirectories(packmeta);
            PrintWriter writer = new PrintWriter(packmeta.toString(), StandardCharsets.UTF_8);

            writer.println("{");
            writer.println("    \"pack\": {");
            writer.println("        \"pack_format\": 34,"); //34 is 1.21.1
            writer.println("    \"description\": \"OracleIndex downloaded wikis\"");
            writer.println("   }");
            writer.println("}");
            writer.close();
            packmeta.createNewFile();
        }
    }

    public static String getResourcePackFolder(){
        return MinecraftClient.getInstance().getResourcePackDir().toString()+"/oracleDownloadedWikis/";
    }

    public static String getBooksFolder(){
        return getResourcePackFolder()+"assets/"+Oracle.MOD_ID+"/books/";
    }

    /**Removes anything except the docs folder and the related files and the .git folder*/
    public static void sanitizeDirectory(@NotNull File dirPath){
        try {
            for (File file : dirPath.listFiles()){
                if(file.isDirectory() && !(file.getName().equals("docs") || file.getName().equals(".git"))){
                    FileUtils.cleanDirectory(file);
                    FileUtils.delete(file);
                }else if(file.isFile()){
                    FileUtils.delete(file);
                }else if(file.isDirectory() && file.getName().equals("docs")){
                    sanitizeDocsFolder(file);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**Removes files that aren't supposed to be in the docs folder*/
    public static void sanitizeDocsFolder(File docsPath){
        File[] files = docsPath.listFiles();
        if(files == null){
            return;
        }
        for (File file : files){
            if(file.isDirectory()){
                sanitizeDocsFolder(file);
            }else if(!checkValidExtension(file.getName())){
                try {
                    FileUtils.delete(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**Checks if the files have a valid extension, which are .mdx .md .png .json
     *
     * Returns true if the file has one of the valid extensions*/
    public static boolean checkValidExtension(String fileName){
        return fileName.endsWith(".mdx") || fileName.endsWith(".md") || fileName.endsWith("png") || fileName.endsWith("json");
    }

    private static void findAllResourceEntries() {
        ResourceManager resourceManager = MinecraftClient.getInstance().getResourceManager();
        Map<Identifier, Resource> resources = resourceManager.findResources("books", path -> path.getPath().endsWith(".mdx"));

        LOADED_BOOKS.clear();

        for (Identifier resourceId : resources.keySet()) {
            String purePath = resourceId.getPath().replaceFirst("books/", "");

            @NotNull String[] segments = purePath.split("/");
            @NotNull String modId = segments[0];        // e.g. "oritech"
            String entryPath = purePath.replaceFirst(modId + "/", ""); // e.g. "tools/wrench.mdx"
            @NotNull String entryFileName = segments[segments.length - 1]; // e.g. "wrench.mdx"
            String entryDirectory = entryPath.replace(entryFileName, ""); // e.g. "tools" or "processing/reactor"

            try {
                String fileContent = new String(resources.get(resourceId).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                readFiles(fileContent, resourceId, modId);

            } catch (IOException e) {
                Oracle.LOGGER.error("Unable to load book with id: " + resourceId);
                throw new RuntimeException(e);
            }


            LOADED_BOOKS.add(modId);
        }

        boolean downloadedSomething = false;

        for(String id : loadedModIds_Urls.keySet()){

            if(!LOADED_BOOKS.contains(id)){
                try {
                    if(queryWikiExists(id)){
                        if(pullFromWeb(id, loadedModIds_Urls.get(id))){
                            //TODO remove
                            downloadedSomething = true;
                            LOADED_BOOKS.add(id);

                        }else{
                            //TODO remove
                            Oracle.LOGGER.info("DEBUG: message error!");

                        }
                    }
                } catch (IOException | URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if(downloadedSomething){
            MinecraftClient.getInstance().getResourcePackManager().enable("file/oracleDownloadedWikis");
            MinecraftClient.getInstance().reloadResources();
        }

    }

    /**Sends an HTTP request to the wiki page of the mod. It checks the
     * url of moddedwiki + the modid of the mod, so while setting up the wiki the
     * slug and the modid must be the same.
     *
     * @return true if the http request is 200, aka it's ok, aka the page exists, False otherwise. */
    public static boolean queryWikiExists(String id) throws IOException, URISyntaxException {
        //TODO add a check for having internet  connection maybe?
        URL url = new URI("https://moddedmc.wiki/en/project/"+id+"/docs").toURL();
        HttpURLConnection huc = (HttpURLConnection) url.openConnection();
        huc.setInstanceFollowRedirects(false);
        huc.setRequestMethod("HEAD");

        int responseCode = huc.getResponseCode();
        return responseCode == HttpURLConnection.HTTP_OK;
    }

    /**Reads the file inputeed as an argument*/
    public static void readFiles(String fileContent, Identifier resourceId, String modId){
        Map<String, String> fileComponents = MarkdownParser.parseFrontmatter(fileContent);
        if (fileComponents.containsKey("related_items")) {
            String baseString = fileComponents.get("related_items").replace("[", "").replace("]", "");
            @NotNull String[] itemStrings = baseString.split(", ");
            for (String itemString : itemStrings) {
                Identifier itemId = Identifier.of(itemString);
                //TODO need to find a way to modify this thing here
                BookItemLink linkData = new BookItemLink(resourceId, fileComponents.getOrDefault("title", "missing"), modId);
                ITEM_LINKS.put(itemId, linkData);
            }
        }
    }
    
    public record BookItemLink(Identifier linkTarget, String entryName, String bookId) {}
    
}

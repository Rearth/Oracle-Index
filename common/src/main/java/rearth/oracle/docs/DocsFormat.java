package rearth.oracle.docs;

public interface DocsFormat {
    boolean isContentPath(String path);
    
    boolean isTranslatedPath(String path);

    String getDocsRoot(DocsMode mode);

    String getTranslatedDir(String locale);

    String getAssetsRoot();
    
    String getDocsPagePath(String slug);

    String stripContentPrefix(String path);
}

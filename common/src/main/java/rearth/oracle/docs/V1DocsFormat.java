package rearth.oracle.docs;

public class V1DocsFormat implements DocsFormat {
    @Override
    public boolean isContentPath(String path) {
        return path.contains("/content/");
    }

    @Override
    public boolean isTranslatedPath(String path) {
        return path.contains("/translated");
    }

    @Override
    public String getDocsRoot(DocsMode mode) {
        return switch (mode) {
            case DOCS -> "/docs";
            case CONTENT -> "/content";
        };
    }

    @Override
    public String getTranslatedDir(String locale) {
        return "/translated/" + locale;
    }

    @Override
    public String getAssetsRoot() {
        return "/assets";
    }

    @Override
    public String getDocsPagePath(String slug) {
        return "docs/" + slug;
    }

    @Override
    public String stripContentPrefix(String path) {
        return path.split("/content/")[1];
    }
}

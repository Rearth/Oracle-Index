package rearth.oracle.util;

import org.jetbrains.annotations.NotNull;
import rearth.oracle.Oracle;

import java.util.HashSet;
import java.util.Set;

public class BookMetadata implements Comparable<BookMetadata> {
    public static final String DEFAULT_LANGUAGE = "en_us";

    private final String bookId;
    private final Set<String> supportedLanguages = new HashSet<>();
    private String currentLanguage = DEFAULT_LANGUAGE;

    public BookMetadata(String bookId, Set<String> supportedLanguages) {
        this.bookId = bookId;
        supportedLanguages.add(DEFAULT_LANGUAGE);
        this.supportedLanguages.addAll(supportedLanguages);
    }

    public String getBookId() {
        return bookId;
    }

    public Set<String> getSupportedLanguages() {
        return supportedLanguages;
    }

    public boolean isSupportedLanguage(String language) {
        return supportedLanguages.contains(language);
    }

    public void updateSupportedLanguages(Set<String> supportedLanguages) {
        if (this.supportedLanguages.equals(supportedLanguages)) return;
        this.supportedLanguages.clear();
        this.supportedLanguages.addAll(supportedLanguages);
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public void setCurrentLanguage(String currentLanguage) {
        if(currentLanguage == null || !supportedLanguages.contains(currentLanguage)) return;
        this.currentLanguage = currentLanguage;
    }

    public boolean isCurrentLanguageDefault() {
        return currentLanguage.equals(DEFAULT_LANGUAGE);
    }

    public boolean isCurrentLanguageSupported() {
        return supportedLanguages.contains(currentLanguage);
    }

    public String getEntryPath(String path) {
        if (path.startsWith("/")) path = path.substring(1);
        if (isCurrentLanguageDefault()) return "books/" + bookId +  "/" + path;
        else return "books/" + bookId + "/.translated/" + currentLanguage + "/" + path;
    }

    public String convertPathToCurrentLanguage(String path) {
        return getEntryPath(path.replaceFirst("^books/[^/]+/(?:.translated/[^/]+/)?", ""));
    }

    @Override
    public int compareTo(@NotNull BookMetadata bookMetadata) {
        return bookId.compareTo(bookMetadata.bookId);
    }

    @Override
    public String toString() {
        return bookId;
    }
}

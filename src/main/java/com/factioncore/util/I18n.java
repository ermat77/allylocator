package com.factioncore.util;

import com.factioncore.Main;
import com.factioncore.config.GeneralConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internationalization service. Resolves UI and chat strings from language bundles
 * based on the {@code Language} setting in GeneralConfig (e.g. "en", "fr").
 */
public final class I18n {

    private static final String BUNDLE_PATH = "lang/";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static final Map<String, Map<String, String>> BUNDLE_CACHE = new ConcurrentHashMap<>();

    private I18n() {
    }

    /**
     * Resolves the current language from config. Returns only "en" or "fr"; falls back to "en" if invalid.
     */
    public static String getLanguage() {
        Main main = Main.get();
        if (main == null || main.getGeneralConfig() == null) {
            return DEFAULT_LANGUAGE;
        }
        GeneralConfig config = main.getGeneralConfig().get();
        if (config == null) {
            return DEFAULT_LANGUAGE;
        }
        String lang = config.getLanguage();
        if (lang == null) {
            return DEFAULT_LANGUAGE;
        }
        String normalized = lang.trim().toLowerCase();
        if ("fr".equals(normalized)) {
            return "fr";
        }
        return DEFAULT_LANGUAGE;
    }

    /**
     * Returns the translation for the given key in the current language.
     * If the key is missing, returns the key itself. If the locale is missing, uses the English bundle.
     */
    public static String get(String key) {
        if (key == null) {
            return "";
        }
        String lang = getLanguage();
        Map<String, String> bundle = getBundle(lang);
        String value = bundle != null ? bundle.get(key) : null;
        if (value != null) {
            return value;
        }
        if (!DEFAULT_LANGUAGE.equals(lang)) {
            Map<String, String> fallback = getBundle(DEFAULT_LANGUAGE);
            value = fallback != null ? fallback.get(key) : null;
            if (value != null) {
                return value;
            }
        }
        return key;
    }

    /**
     * Returns the translation for the given key with placeholders replaced by the given arguments.
     * Placeholders use MessageFormat style: {0}, {1}, etc.
     */
    public static String get(String key, Object... args) {
        if (key == null) {
            return "";
        }
        String template = get(key);
        if (args == null || args.length == 0) {
            return template;
        }
        try {
            return MessageFormat.format(template, args);
        } catch (Exception e) {
            return template;
        }
    }

    private static Map<String, String> getBundle(String language) {
        return BUNDLE_CACHE.computeIfAbsent(language, I18n::loadBundle);
    }

    private static Map<String, String> loadBundle(String language) {
        String path = BUNDLE_PATH + language + ".json";
        try (InputStream in = I18n.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return new ConcurrentHashMap<>();
            }
            Map<String, String> map = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), MAP_TYPE);
            return map != null ? new ConcurrentHashMap<>(map) : new ConcurrentHashMap<>();
        } catch (Exception e) {
            return new ConcurrentHashMap<>();
        }
    }
}

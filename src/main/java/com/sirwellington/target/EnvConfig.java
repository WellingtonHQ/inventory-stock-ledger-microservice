package com.sirwellington.target;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Loads environment variables from Environmental Variables, falling back to a .env file in the project root.
 */
public final class EnvConfig {

    private EnvConfig() {}

    private static final Dotenv DOTENV = Dotenv.configure().ignoreIfMissing().load();

    public static String get(String key, String defaultValue) {
        return DOTENV.get(key, defaultValue);
    }

    public static String get(String key) {
        return get(key, null);
    }
}

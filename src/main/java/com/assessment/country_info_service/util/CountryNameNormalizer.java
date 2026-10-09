package com.assessment.country_info_service.util;

import java.util.Locale;

public final class CountryNameNormalizer {

    private CountryNameNormalizer() {
    }

    /**
     * Trims, collapses whitespace and applies title case.
     * Example: "south africa" -> "South Africa".
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }

        String[] words = raw.trim()
                .toLowerCase(Locale.ROOT)
                .split("\\s+");

        StringBuilder sb = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (sb.length() > 0) {
                sb.append(' ');
            }

            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(word.substring(1));
        }

        return sb.toString();
    }
}
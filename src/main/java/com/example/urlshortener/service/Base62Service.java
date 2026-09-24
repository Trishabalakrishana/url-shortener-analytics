package com.example.urlshortener.service;

import org.springframework.stereotype.Service;

/**
 * Encodes numeric IDs (e.g. auto-increment primary keys) into base62 strings.
 * Counter-based approach: no collision checks needed for generated codes,
 * since each DB id is unique by definition. Custom aliases are checked
 * separately for uniqueness in LinkService.
 */
@Service
public class Base62Service {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    public String encode(long id) {
        if (id == 0) return String.valueOf(ALPHABET.charAt(0));
        StringBuilder sb = new StringBuilder();
        long n = id;
        while (n > 0) {
            int rem = (int) (n % BASE);
            sb.append(ALPHABET.charAt(rem));
            n /= BASE;
        }
        return sb.reverse().toString();
    }

    public long decode(String code) {
        long result = 0;
        for (char c : code.toCharArray()) {
            result = result * BASE + ALPHABET.indexOf(c);
        }
        return result;
    }
}

package com.vaios.holobar;
import org.apache.commons.lang3.tuple.ImmutablePair;
import com.ibm.icu.text.RuleBasedNumberFormat;
import android.content.Context;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Locale;

public class Tokenizer {
    private final String textSymbols;
    private final boolean lowercase;
    private final Map<String, Integer> tokenToIdx;
    private final Phonemizer phonemizer;
    private final List<ImmutablePair<Pattern, String>> abbreviations;
    private final String languageCodeEn;
    private final String languageCodeDe;
    private final String padToken;
    private final String endToken;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b");
    private final RuleBasedNumberFormat numberFormat;

    public Tokenizer(Context context) {
        Properties config = loadConfig(context);
        this.textSymbols = config.getProperty("text_symbols");
        this.lowercase = Boolean.parseBoolean(config.getProperty("lowercase"));
        this.languageCodeEn = config.getProperty("language_code_en");
        this.languageCodeDe = config.getProperty("language_code_de");
        this.padToken = config.getProperty("pad_token");
        this.endToken = config.getProperty("end_token");
        this.tokenToIdx = createTokenToIdx();
        this.phonemizer = new Phonemizer(config, tokenToIdx);
        this.abbreviations = createAbbreviations(config);
        this.numberFormat = new RuleBasedNumberFormat(Locale.US, RuleBasedNumberFormat.SPELLOUT);
    }

    private Properties loadConfig(Context context) {
        Properties prop = new Properties();
        String resourceName = "tokenizer_config.properties";
        try (InputStream input = context.getAssets().open(resourceName)) {
            prop.load(input);
        } catch (IOException ex) {
            throw new TokenizerException("Failed to load configuration", ex);
        }
        return prop;
    }

    private Map<String, Integer> createTokenToIdx() {
        Map<String, Integer> tokenToIdx = new LinkedHashMap<>();
        tokenToIdx.put(padToken, 0);
        tokenToIdx.put(languageCodeEn, tokenToIdx.size());
        tokenToIdx.put(languageCodeDe, tokenToIdx.size());
        tokenToIdx.put(endToken, tokenToIdx.size());
        
        for (char c = 'a'; c <= 'z'; c++) {
            tokenToIdx.put(String.valueOf(c), tokenToIdx.size());
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            tokenToIdx.put(String.valueOf(c), tokenToIdx.size());
        }
        
        String germanChars = "äöüÄÖÜß";
        for (char c : germanChars.toCharArray()) {
            tokenToIdx.put(String.valueOf(c), tokenToIdx.size());
        }
        tokenToIdx.put("'", tokenToIdx.size());

        return tokenToIdx;
    }

    private List<ImmutablePair<Pattern, String>> createAbbreviations(Properties config) {
        List<ImmutablePair<Pattern, String>> abbr = new ArrayList<>();
        for (String key : config.stringPropertyNames()) {
            if (key.startsWith("abbr.")) {
                String abbreviation = key.substring(5);
                String expansion = config.getProperty(key);
                Pattern pattern = Pattern.compile("\\b" + abbreviation + "\\.", Pattern.CASE_INSENSITIVE);
                abbr.add(ImmutablePair.of(pattern, expansion));
            }
        }
        return abbr;
    }

    private String convertToAscii(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");
    }

    private String expandAbbreviations(String text) {
        for (ImmutablePair<Pattern, String> pair : abbreviations) {
            text = pair.left.matcher(text).replaceAll(pair.right);
        }
        return text;
    }

    private String convertNumbersToWords(String text) {
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group());
            matcher.appendReplacement(sb, numberFormat.format(number));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String cleanText(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        sb.append(Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", ""));
        if (lowercase) {
            for (int i = 0; i < sb.length(); i++) {
                sb.setCharAt(i, Character.toLowerCase(sb.charAt(i)));
            }
        }
        String expanded = expandAbbreviations(sb.toString());
        expanded = convertNumbersToWords(expanded);
        return WHITESPACE_PATTERN.matcher(expanded).replaceAll(" ").trim();
    }

    public List<Integer> textToIds(String text) {
        String cleanedText = cleanText(text);
        return phonemizer.infer(cleanedText);
    }

    public static class TokenizerException extends RuntimeException {
        public TokenizerException(String message) {
            super(message);
        }

        public TokenizerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
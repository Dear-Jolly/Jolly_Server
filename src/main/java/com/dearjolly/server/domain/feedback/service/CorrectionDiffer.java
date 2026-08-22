package com.dearjolly.server.domain.feedback.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

// 조각 생성은 LLM 이 아니라 서버 책임이다. LLM 출력이 어떻든
// 조각을 이어붙이면 원문·교정문과 정확히 일치해야 한다.
@Component
public class CorrectionDiffer {
    private static final Pattern TOKEN = Pattern.compile("\\s+|\\S+");

    public List<CorrectionPair> diff(String original, String corrected) {
        List<String> originalTokens = tokenize(original);
        List<String> correctedTokens = tokenize(corrected);
        int[][] lcs = longestCommonSubsequence(originalTokens, correctedTokens);

        List<CorrectionPair> pairs = new ArrayList<>();
        StringBuilder removed = new StringBuilder();
        StringBuilder added = new StringBuilder();
        StringBuilder unchanged = new StringBuilder();

        int i = 0;
        int j = 0;
        while (i < originalTokens.size() || j < correctedTokens.size()) {
            if (isMatch(originalTokens, correctedTokens, lcs, i, j)) {
                flushModified(pairs, removed, added);
                unchanged.append(originalTokens.get(i));
                i++;
                j++;
                continue;
            }
            flushUnchanged(pairs, unchanged);
            if (j >= correctedTokens.size() || (i < originalTokens.size() && lcs[i + 1][j] >= lcs[i][j + 1])) {
                removed.append(originalTokens.get(i));
                i++;
                continue;
            }
            added.append(correctedTokens.get(j));
            j++;
        }
        flushModified(pairs, removed, added);
        flushUnchanged(pairs, unchanged);
        return pairs;
    }

    public boolean isValid(List<CorrectionPair> pairs, String original, String corrected) {
        return join(pairs, CorrectionPair::originalText).equals(original)
                && join(pairs, CorrectionPair::correctedText).equals(corrected);
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private int[][] longestCommonSubsequence(List<String> original, List<String> corrected) {
        int[][] lcs = new int[original.size() + 1][corrected.size() + 1];
        for (int i = original.size() - 1; i >= 0; i--) {
            for (int j = corrected.size() - 1; j >= 0; j--) {
                lcs[i][j] = original.get(i).equals(corrected.get(j))
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        return lcs;
    }

    private boolean isMatch(List<String> original, List<String> corrected, int[][] lcs, int i, int j) {
        return i < original.size()
                && j < corrected.size()
                && original.get(i).equals(corrected.get(j))
                && lcs[i][j] == lcs[i + 1][j + 1] + 1;
    }

    private void flushUnchanged(List<CorrectionPair> pairs, StringBuilder unchanged) {
        if (unchanged.isEmpty()) {
            return;
        }
        String text = unchanged.toString();
        pairs.add(new CorrectionPair(text, text));
        unchanged.setLength(0);
    }

    private void flushModified(List<CorrectionPair> pairs, StringBuilder removed, StringBuilder added) {
        if (removed.isEmpty() && added.isEmpty()) {
            return;
        }
        pairs.add(new CorrectionPair(removed.toString(), added.toString()));
        removed.setLength(0);
        added.setLength(0);
    }

    private String join(List<CorrectionPair> pairs, Function<CorrectionPair, String> extractor) {
        return pairs.stream().map(extractor).collect(Collectors.joining());
    }
}

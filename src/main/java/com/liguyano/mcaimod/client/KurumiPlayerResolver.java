package com.liguyano.mcaimod.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** One shared player-name resolver for combat, navigation, camera and TPA. */
final class KurumiPlayerResolver {
    record Match(String name, List<String> candidates) {
        boolean found() { return name != null; }
        String failure(String requested) {
            return "无法唯一识别玩家“" + requested + "”；当前在线玩家候选："
                    + (candidates.isEmpty() ? "无" : String.join("、", candidates))
                    + "。请根据玩家名单选择准确用户名后重试";
        }
    }

    private KurumiPlayerResolver() {}

    static Match resolve(String requested, Collection<String> online) {
        List<String> names = online.stream().filter(s -> s != null && !s.isBlank())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (requested == null || requested.isBlank()) return new Match(null, names);
        String alias = KurumiLookPlayerTarget.playerName(requested.strip());
        String query = (alias == null ? requested.strip() : alias).toLowerCase(Locale.ROOT);
        if (!query.matches("[a-z0-9_]{1,16}")) return new Match(null, names);
        List<String> exact = names.stream().filter(n -> n.equalsIgnoreCase(query)).toList();
        if (exact.size() == 1) return new Match(exact.get(0), exact);
        List<String> prefix = names.stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(query)).toList();
        if (prefix.size() == 1) return new Match(prefix.get(0), prefix);
        if (prefix.size() > 1) return new Match(null, prefix);
        List<String> contained = names.stream().filter(n -> n.toLowerCase(Locale.ROOT).contains(query)).toList();
        if (contained.size() == 1) return new Match(contained.get(0), contained);
        if (contained.size() > 1) return new Match(null, contained);
        if (query.length() < 4) return new Match(null, names);
        int min = Integer.MAX_VALUE;
        List<String> nearest = new ArrayList<>();
        for (String name : names) {
            int score = distance(query, name.toLowerCase(Locale.ROOT));
            if (score < min) { min = score; nearest.clear(); nearest.add(name); }
            else if (score == min) nearest.add(name);
        }
        if (min <= Math.min(2, Math.max(1, query.length() / 3)) && nearest.size() == 1)
            return new Match(nearest.get(0), nearest);
        return new Match(null, nearest.isEmpty() ? names : nearest);
    }

    private static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) for (int j = 1; j <= b.length(); j++) {
            int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
            d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2)
                    && a.charAt(i - 2) == b.charAt(j - 1))
                d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
        }
        return d[a.length()][b.length()];
    }
}

package com.matissjurevics.icyou.terminal;

import java.util.concurrent.ThreadLocalRandom;

/** Generates a URL-safe word-slug passphrase, e.g. {@code "ember-kestrel-orchid-77"}. */
public final class SlugToken {

    private SlugToken() {}

    private static final String[] WORDS = {
            "ember", "kestrel", "orchid", "coral", "raven", "pine", "onyx", "drift",
            "glacier", "meadow", "willow", "juniper", "basalt", "harbor", "fable", "grove",
            "lumen", "nimbus", "pebble", "quill", "ridge", "sable", "tarn", "umbra",
            "verge", "whist", "zephyr", "atlas", "briar", "cedar", "dune", "elm",
            "frost", "gale", "holm", "islet", "jasper", "kelp", "loam", "moss",
            "nova", "opal", "pumice", "quartz", "rune", "slate", "tide", "violet",
            "wisp", "yarrow"
    };

    /** 4 words + a 0-99 suffix. Enough entropy for a shared read-only link. */
    public static String generate() {
        var r = ThreadLocalRandom.current();
        int a = r.nextInt(WORDS.length);
        int b = r.nextInt(WORDS.length);
        int c = r.nextInt(WORDS.length);
        int d = r.nextInt(WORDS.length);
        int n = r.nextInt(100);
        return WORDS[a] + "-" + WORDS[b] + "-" + WORDS[c] + "-" + WORDS[d] + "-" + n;
    }
}

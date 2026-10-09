package com.archguard;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobMatcherTest {

    private static boolean matches(String glob, String path) {
        return new GlobMatcher(List.of(glob)).matches(path);
    }

    @Test
    void doubleStarSlashAlsoMatchesTheRepoRoot() {
        assertThat(matches("**/views/**", "views/a.py")).isTrue();
        assertThat(matches("**/views/**", "app/views/a.py")).isTrue();
        assertThat(matches("**/views/**", "a/b/views/c/d.py")).isTrue();
    }

    @Test
    void segmentsMustMatchExactly() {
        assertThat(matches("**/views/**", "previews/a.py")).isFalse();
        assertThat(matches("**/views/**", "app/views.py")).isFalse();
        assertThat(matches("**/ui/**", "build/a.py")).isFalse();
    }

    @Test
    void singleStarStaysWithinOneDirectory() {
        assertThat(matches("app/*.py", "app/main.py")).isTrue();
        assertThat(matches("app/*.py", "app/sub/main.py")).isFalse();
    }

    @Test
    void questionMarkMatchesOneCharacter() {
        assertThat(matches("v?.py", "v1.py")).isTrue();
        assertThat(matches("v?.py", "v10.py")).isFalse();
    }

    @Test
    void regexMetacharactersAreLiteral() {
        assertThat(matches("a+b/(x).py", "a+b/(x).py")).isTrue();
        assertThat(matches("a.py", "aXpy")).isFalse();
    }

    @Test
    void normalisesSeparatorsAndLeadingDotSlash() {
        assertThat(matches("**/views/**", "app\\views\\a.py")).isTrue();
        assertThat(matches("**/views/**", "./views/a.py")).isTrue();
        assertThat(GlobMatcher.normalize("/x/y")).isEqualTo("x/y");
    }

    @Test
    void anyOfSeveralGlobsMatches() {
        GlobMatcher matcher = new GlobMatcher(List.of("**/views/**", "**/views.py"));

        assertThat(matcher.matches("shop/views.py")).isTrue();
        assertThat(matcher.matches("shop/models.py")).isFalse();
    }
}

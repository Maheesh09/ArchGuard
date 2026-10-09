package com.archguard;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryUrlValidatorTest {

    @ParameterizedTest
    @CsvSource({
            "https://github.com/Maheesh09/ArchGuard,         https://github.com/Maheesh09/ArchGuard.git",
            "https://github.com/Maheesh09/ArchGuard.git,     https://github.com/Maheesh09/ArchGuard.git",
            "https://github.com/Maheesh09/ArchGuard/,        https://github.com/Maheesh09/ArchGuard.git",
            "'  https://github.com/pallets/flask.git\n',     https://github.com/pallets/flask.git",
            "https://github.com/my-org/repo.with_dots-1,     https://github.com/my-org/repo.with_dots-1.git"
    })
    void acceptsAndCanonicalisesGitHubHttpsUrls(String input, String expected) {
        assertThat(RepositoryUrlValidator.validate(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "/etc",
            "C:\\Users\\me\\secret-repo",
            "file:///etc/passwd",
            "file://localhost/home/user/repo",
            "../other-job",
            "http://github.com/owner/repo",
            "git@github.com:owner/repo.git",
            "ssh://git@github.com/owner/repo.git",
            "https://gitlab.com/owner/repo",
            "https://github.com.evil.com/owner/repo",
            "https://evil.com/github.com/owner/repo",
            "https://user:token@github.com/owner/repo",
            "https://github.com/owner",
            "https://github.com/owner/repo/tree/main",
            "https://github.com/owner/repo?x=1",
            "https://github.com/owner/repo#frag",
            "https://github.com/owner/..",
            "https://github.com/owner/.",
            "https://github.com/-owner/repo",
            "https://github.com/owner/re po",
            "https://github.com/owner/repo\nhttps://github.com/other/repo"
    })
    void rejectsAnythingThatIsNotAGitHubHttpsRepository(String input) {
        assertThatThrownBy(() -> RepositoryUrlValidator.validate(input))
                .isInstanceOf(RepositoryUrlValidator.InvalidRepositoryUrlException.class);
    }
}

package com.archguard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitServiceTest {

    @TempDir
    Path tmp;

    @Test
    void workspaceIsAChildOfTheWorkspaceRoot() {
        GitService git = new GitService(tmp.toString(), 10);

        assertThat(git.workspaceFor("abc")).isEqualTo(tmp.toAbsolutePath().normalize().resolve("abc"));
    }

    @Test
    void workspaceRejectsPathTraversal() {
        GitService git = new GitService(tmp.toString(), 10);

        assertThatThrownBy(() -> git.workspaceFor("../escape")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> git.workspaceFor(".")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToCloneNonGitHubUrlsWithoutTouchingTheDisk() {
        GitService git = new GitService(tmp.toString(), 10);
        Path target = git.workspaceFor("job");

        assertThatThrownBy(() -> git.cloneRepository("file:///etc", target))
                .isInstanceOf(RepositoryUrlValidator.InvalidRepositoryUrlException.class);
        assertThat(target).doesNotExist();
    }

    @Test
    void deleteQuietlyRemovesNestedAndReadOnlyFiles() throws Exception {
        GitService git = new GitService(tmp.toString(), 10);
        Path dir = tmp.resolve("clone");
        Path pack = dir.resolve(".git/objects/pack/pack-1.pack");
        Files.createDirectories(pack.getParent());
        Files.writeString(pack, "data");
        assertThat(pack.toFile().setReadOnly()).isTrue(); // git pack files are read-only

        git.deleteQuietly(dir);

        assertThat(dir).doesNotExist();
    }

    @Test
    void deleteQuietlyIgnoresMissingAndNullDirectories() {
        GitService git = new GitService(tmp.toString(), 10);

        assertThatCode(() -> {
            git.deleteQuietly(null);
            git.deleteQuietly(tmp.resolve("missing"));
        }).doesNotThrowAnyException();
    }
}

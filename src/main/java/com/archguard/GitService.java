package com.archguard;

import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class GitService {

    private static final Logger logger = Logger.getLogger(GitService.class.getName());

    public File cloneRepository(String repoUrl, String jobId) throws Exception {
        String tmpDirPath = System.getProperty("java.io.tmpdir") + "/archguard/jobs/" + jobId;
        File localPath = new File(tmpDirPath);

        // Clean up directory if it already exists
        if (localPath.exists()) {
            deleteDirectory(localPath);
        }
        logger.log(Level.INFO, "[GIT] Cloning {0} into {1}", new Object[]{repoUrl, localPath.getAbsolutePath()});
        
        Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(localPath)
                .call()
                .close();

        return localPath;
    }

    public void deleteDirectory(File dir) throws IOException {
        try (var paths = Files.walk(dir.toPath())) {
            paths.sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        }
    }
}
package dev.graboo;

import org.gradle.wrapper.*;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

public class BootWrapper {

    public static void main(String[] args) throws Exception {
        String grabooDir = System.getProperty("graboo.dir");

        File bootwrapperProperties = new File(grabooDir, "bootwrapper.properties");
        if (!bootwrapperProperties.exists()) {
            System.err.println(bootwrapperProperties + " does not exist");
        }

        Logger logger = new Logger(true);

        File projectDir = new File(System.getProperty("user.dir"));

        WrapperExecutor wrapperExecutor = WrapperExecutor.forWrapperPropertiesFile(bootwrapperProperties);
        WrapperConfiguration configuration = wrapperExecutor.getConfiguration();
        IDownload download = new Download(logger, "gradlew", "0", configuration.getNetworkTimeout());
        File gradleUserHome = GradleUserHomeLookup.gradleUserHome();
        Install install = new Install(logger, download, new PathAssembler(gradleUserHome, projectDir));

        File gradleHome = install.createDist(configuration);

        File ideaConfigDir = new File(projectDir, ".idea");
        File gradleIdeaFile = new File(ideaConfigDir, "gradle.xml");
        if (!gradleIdeaFile.exists()) {
            if (!ideaConfigDir.exists()) {
                ideaConfigDir.mkdir();
            }

            String javaHome = System.getProperty("java.home");

            List<String> lines = Arrays.asList(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                    "<project version=\"4\">",
                    "  <component name=\"GradleSettings\">",
                    "    <option name=\"linkedExternalProjectsSettings\">",
                    "      <GradleProjectSettings>",
                    "        <option name=\"distributionType\" value=\"LOCAL\" />",
                    "        <option name=\"externalProjectPath\" value=\"$PROJECT_DIR$\" />",
                    "        <option name=\"gradleHome\" value=\"" + gradleHome + "\" />",
                    "        <option name=\"gradleJvm\" value=\"" + javaHome + "\" />",
                    "      </GradleProjectSettings>",
                    "    </option>",
                    "    <option name=\"parallelModelFetch\" value=\"true\" />",
                    "  </component>",
                    "</project>"
            );

            Files.write(gradleIdeaFile.toPath(), lines, Charset.defaultCharset(), StandardOpenOption.CREATE_NEW);
        }

        wrapperExecutor.execute(args, install, new BootstrapMainStarter());
    }

}

package dev.graboo;

import org.gradle.wrapper.*;

import java.io.File;

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
        wrapperExecutor.execute(args, install, new BootstrapMainStarter());
    }

}

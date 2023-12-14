# Graboo Dev Info

## Bootwrapper

> Wraps Gradle Wrapper for a JVM entrypoint and externalized wrapper config
> Written in Java to reduce UberJar size since it is embedded into the Bootstrapper

## Bootstrapper

> sh and cmd scripts that download and launch the native bootstrapper
> native bootstrapper handles `graboo new` and downloading a JDK and launching the bootwrapper

Run locally:
```
./gradlew :bootstrapper:runDebugExecutableLinuxX64

# With Gradle Args
./gradlew :bootstrapper:runDebugExecutableLinuxX64 -PrunArgs='--help'
```

Create native executable:
```
./gradlew :bootstrapper:linkReleaseExecutableLinuxX64
```

## Gradle Plugin

> The Gradle Settings Plugin that transforms a concise project definition into the Gradle config

```
./gradlew :gradle-plugin:test --tests com.jamesward.gradleboot.GradlePluginTest.androidapp_hello_world_device_test
```


## Server

Dev Mode, run each in a separate terminal
```
./gradlew :server:jvmRun
```

```
./gradlew -t :server:compileKotlinJvm
```

Native Linux Run:
```
./gradlew :server:runDebugExecutableLinuxX64
```

Build a container:
```
./gradlew :server:jibDockerBuild --image=graboo-server
```

Run the container:
```
docker run -it -p8080:8080 graboo-server
```

## Configurer

```
./gradlew :configurer:installDist
```

Some other dir
```
<project_dir>/configurer/build/install/configurer/bin/configurer
```

## Release
- Update `README.md` and examples/*/settings.gradle.kts
- `git tag v0.0.x`
- `git push --atomic origin main v0.0.x`

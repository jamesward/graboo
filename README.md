# GradleBoot

> The best build tool is the one you don't see.

## Bootstrapper

Run locally:
```
./gradlew :bootstrapper:runDebugExecutableLinuxX64
```

Create native executable:
```
./gradlew :bootstrapper:linkReleaseExecutableLinuxX64
```

## Configurer

```
./gradlew :configurer:installDist
```

Some other dir
```
<project_dir>/configurer/build/install/configurer/bin/configurer
```
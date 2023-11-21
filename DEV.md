# Graboo Dev Info

## Bootstrapper

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

```
./gradlew :gradle-plugin:test --tests com.jamesward.gradleboot.GradlePluginTest.androidapp_hello_world_device_test
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

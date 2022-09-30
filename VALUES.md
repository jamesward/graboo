GradleBoot Values & Design
-----------------

- Make mostly accurate assumptions about what the user is trying to do
  - Inspects a directory then assembles and runs a Gradle Build
  - Automatic Gradle Plugins
  - Automatic imports to dependencies (overrides via graboo.toml)
- Project-centric toolchains, not global ones. (ie, no system deps to work on a project)
- Terminal UI (TUI), not a CLI (ie, no command parsing)
  - Zero system dependencies (bootstraps the JVM)
  - Kotlin/Native executable ~1M
- In-place updates to graboo, jvm, & gradle (overrides via graboo.toml)


## TUI

Empty Dir
```
No Sources

What would you like to do?
> Create source directory
  > Java
  > Kotlin
> Open in an IDE
  > IntelliJ
  > Android Studio
  > VS Code
> Help
```

> "Java" -> creates `src/main/java` and `src/test/java`
> BuildBuilder sees `src/main/java` dir and configures java library plugin
> BuildBuilder sees `src/test/java` dir and configures testing

No-main `src/main/java/Foo.java`
```
Detected new file:
  src/main/java/Foo.java

Compile Status: 0 Errors.  0 Warnings.  15 seconds ago.

What would you like to do?
> Configure
  > Libraries
  > Compile
> Open in an IDE
> Help
```

Test `src/test/java/FooTest.java`
```
Detected new file:
  src/test/java/FooTest.java

Compile Status: 0 Errors.  0 Warnings.  15 seconds ago.
Test Status: 5 Passed.  0 Failed.  15 seconds ago.

What would you like to do?
> Configure
  > Libraries
  > Compile
  > Tests
    > Turn off auto-test
    > Filter tests
> Open in an IDE
> Help

Detected changes to:
  src/test/java/FooTest.java

Compile Status: 0 Errors.  0 Warnings.  15 seconds ago.
Test Status: 5 Passed.  0 Failed.  15 seconds ago.

What would you like to do?
> Configure
  > Libraries
  > Compile
  > Tests
    > Turn off auto-test
    > Filter tests
> Open in an IDE
> Help
```

Single-main `src/main/java/App.java`
```
Detected new file:
  src/main/java/App.java

... Compile Output ...
... Test Output ...
... Run Output ...

Compile Status: 0 Errors.  0 Warnings.  15 seconds ago.
Test Status: 5 Passed.  0 Failed.  15 seconds ago.
Run Status: Exit Code 0. 15 seconds ago.

What would you like to do?
> Configure
  > Libraries
  > Compile
  > Run
  > Tests
> Open in an IDE
> Help
```

Multi-main `src/main/java/Bar.java`
```
... Compile Output ...
... Test Output ...

Compile Status: 0 Errors.  0 Warnings.  15 seconds ago.
Test Status: 5 Passed.  0 Failed.  15 seconds ago.

What would you like to run:
> App
> Bar

... Run Output ...

Compile Status: 0 Errors.  0 Warnings.  15 seconds ago.
Test Status: 5 Passed.  0 Failed.  15 seconds ago.
Run Status [Bar]: Exit Code 0. 15 seconds ago.

What would you like to do?
> Configure
  > Libraries
  > Compile
  > Run
  > Tests
> Open in an IDE
> Help
```

Blocking-main `src/main/java/Server.java`
```
Detected new file:
  src/main/java/Server.java

... Compile Output ...
... Test Output ...
... Run Output ...

Compile Status: 0 Errors.  0 Warnings.  15 seconds ago.
Test Status: 5 Passed.  0 Failed.  15 seconds ago.
Running [Server]: Started 15 seconds ago.

What would you like to do?
> Configure
> Open in an IDE
> Help

Detected changes to:
  src/main/java/Server.java

Compile Status: 0 Errors.  0 Warnings.  15 seconds ago.
Test Status: 5 Passed.  0 Failed.  15 seconds ago.
Running [Server]: Started 15 seconds ago.

What would you like to do?
> Restart Server
> Configure
  > Libraries
  > Compile
  > Run
  > Tests
> Open in an IDE
> Help
```
# Setting up an example project to run model checking

## Requirements

1. Gradle (min version 8.4)
2. Java (min version 17)

## Setting up

Create a new project directory and initialize a gradle java project in that directory

```bash
mkdir new-project
cd new-project
gradle init
```

Set up the gradle project with the following choices

1. Choose **Application** type of build
2. Select **Java** as the implementation language
3. Enter the java version installed in your local machine
4. Retain the default project name
5. Select **Single application project** as the application structure
6. Select **Kotlin** as the build script DSL
7. Select **JUnit Jupiter** as the test framework
8. Avoid using new APIs for building (optional)

Once set up, the project will have an `app` directory containing the structure of the application to test

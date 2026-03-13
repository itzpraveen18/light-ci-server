Build the Java fat JAR using Maven.

Run the following from the project root:

```bash
cd /Users/praveen/python/light-ci-server
mvn clean package -DskipTests
```

After the build, confirm that `target/light-ci-server.jar` exists and print its file size.

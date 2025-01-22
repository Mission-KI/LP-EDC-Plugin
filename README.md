# EDC EDPS Extension

build the connector:

```bash
./gradlew edc-edps-extension:connector:build`
```

run the connector:

```bash
java -Dedc.keystore=app-edc-edps-demo/resources/certs/cert.pfx -Dedc.keystore.password=123456 -Dedc.fs.config=-Dedc.fs.config=edc-edps-extension/connector/src/main/resources/application.properties -jar app-edc-edps-demo/connector/build/libs/connector.jar
```

dummy change
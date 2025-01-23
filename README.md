# EDC EDPS Extension

build the connector:

```bash
./gradlew edc-edps-extension:connector:build
```

run the connector:

```bash
java -Dedc.keystore=app-edc-edps-demo/resources/certs/cert.pfx -Dedc.keystore.password=123456 -Dedc.fs.config=-Dedc.fs.config=edc-edps-extension/connector/src/main/resources/application.properties -jar app-edc-edps-demo/connector/build/libs/connector.jar
```

# edp calls

create edp
```bash 
curl -X POST http://localhost:19191/api/edps
```

start local demo http server
```bash
python ./util/http-demo-server/server.py
```

demo.csv file at
```
http://localhost:8080/data.csv
```

post demo data at
```bash
curl -X POST -F "file=@yourfile.zip" http://localhost:8080/upload
```
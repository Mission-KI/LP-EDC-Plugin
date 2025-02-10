# EDC - EDP Extension


To extend the EDC with the EDPS functionality, this extension provides the following features:
- Create an EDPS job to enhance an asset
- Retrieve the enhanced asset from EDPS
- Exposes endpoints to retrieve EDPS job information
- Create a result asset in EDC with the enhanced data
- Publish the result asset to Daseen

## Requirements
- Java 17 (17.0.8+7)
  - maybe you need to export JAVA_HOME for IntelliJ
  - if installed with asdf you can do `export JAVA_HOME="$(asdf where java)"`

## Project Structure

The project consists of the following modules:
- `edc-edps-extension`: The actual EDC-EDP connector extension
- utils: 
  - `http-file-server`: contains the http file server to provide the demo data.csv
  - `edps_mock-server`: the mock server for the EDPS and Daseen Api

Note: *To switch between the mock server and the real EDPS and Daseen Api, the `application.properties` in the `edc-edps-extension` module has to be adjusted.
It is sufficient to change the `edc.edps.url` and `edc.daseen.url` properties to the real endpoint.*

## Documentation

The Api reference for the:
- Management Api can be found [here](https://github.com/eclipse-edc/Connector/blob/gh-pages/openapi/management-api/3.0.6/management-api.yaml).  
- Extension Api can be found [here](edc-edps-extension/connector/src/main/resources/edc-edps-openapi.yml).


## EDP Workflow

### 1. Start the EDC server

Build the connector:

```bash
./gradlew edc-edp-extension:connector:build
```

Note: The gradle-wrapper.jar needs to be in the `.gradle/wrapper/` directory.

Run the connector:

```bash
java -Dedc.fs.config=edc-edp-extension/connector/src/main/resources/application.properties -jar edc-edp-extension/connector/build/libs/connector.jar
```

Alternatively, start the `io/nexyo/edp/extensions/Runner.java` in the IDE.


### 2. Start Services

Start the http server with the following command:

```bash
python ./util/http-file-server/server.py
```

The file server will be used to provide files referenced by the EDC assets. 
Additionally, the file server acts as the callback address for the dataplane, logging the results of dataplane operations.

Start the mock server for the EDPS and Daseen Api:

```bash
python ./util/edps-mock-server/server.py
```

### 3. Create Asset

Use the management Api to create an asset:

```bash
curl -d @edc-edp-extension/connector/src/main/resources/requests/create-asset.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/assets \
  -s | jq 
```

[Optional] Check if asset is created:

```bash 
curl  http://localhost:19193/management/v3/assets/assetId1 | jq
```


### 4. Create EDPS Job

```bash 
curl -X POST http://localhost:19191/api/edp/edps/assetId1/jobs  | jq
```

Note the `jobId` in the response as it is needed for the next step.

[Optional] Get EDPS job by assetId:
```bash
curl http://localhost:19191/api/edp/edps/assetId1/jobs | jq 
```


[Optional] Get EDPS job status:

```bash
curl  http://localhost:19191/api/edp/edps/assetId1/jobs/{jobId}/status  | jq
```

### 5. Get EDPS Result

Replace the jobId in the request with the jobId from the previous step.

```bash
curl -X POST http://localhost:19191/api/edp/edps/assetId1/jobs/{jobId}/result \
  -H 'content-type: application/json' \
  -d @edc-edp-extension/connector/src/main/resources/requests/fetch-edps-result.json 
````

### 6. Create Result Asset

```bash
curl -d @edc-edp-extension/connector/src/main/resources/requests/create-result-asset.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/assets \
  -s | jq 
```


### 7. Publish Result to Daseen

```bash
curl -X POST http://localhost:19191/api/edp/daseen/resultAssetId1/publish | jq
```


## Limitations

- Only transfers of type `HttpData` are supported at the moment
- EDPS and Daseen Api are mocked as the content type of the requests are determined by the Dataplane
- No error handling for Dataplane operations


## ToDos

- Add tests
- Support for other transfer types
- Use job infos from request rather than currently used test data
- Add routes for dataplane results (POST and GET)

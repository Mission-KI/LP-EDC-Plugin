# EDC - EDP Extension


To extend the EDC with the EDPS functionality, this extension provides the following features:
- Establish a contract to an EDPS
- Create an EDPS job to enhance an asset
- Retrieve the enhanced asset from EDPS
- Exposes endpoints to retrieve EDPS job information
- Create a result asset in EDC with the enhanced data
- Publish the result asset to Daseen

## Requirements
- Java 17 (17.0.8+7)

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
- Extension Api can be found [here](resources/edc-edps-openapi.yml).


## EDP Workflow

### 1. Start the EDC server

Build the connector:

```bash
./gradlew connector:build
```

Note: The gradle-wrapper.jar needs to be in the `.gradle/wrapper/` directory.

Run the provider connector:

```bash
java -Dedc.fs.config=resources/configuration/provider-configuration.properties -jar connector/build/libs/connector.jar
```

Run the edps and daseen services connector:

```bash
java -Dedc.fs.config=resources/configuration/service-provider-configuration.properties -jar connector/build/libs/connector.jar
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


### Setup Service Provider side

#### 1. Create EDPS Asset

```bash
curl -d @resources/requests/create-edps-asset.json \
  -H 'content-type: application/json' http://localhost:29193/management/v3/assets \
  -s | jq 
```

[Optional] Check if asset is created:

```bash 
curl -X POST http://localhost:29193/management/v3/assets/request | jq
```

#### 2. Create Policy

```bash
curl -d @resources/requests/create-policy.json \
  -H 'content-type: application/json' http://localhost:29193/management/v3/policydefinitions \
  -s | jq 
```

[Optional] Check if policy is created:

```bash 
curl -X POST http://localhost:29193/management/v3/policydefinitions/request | jq
```

#### 3. Create Contract Definition


```bash
curl -d @resources/requests/create-contract-definition.json \
  -H 'content-type: application/json' http://localhost:29193/management/v3/contractdefinitions \
  -s | jq 
```

[Optional] Check if contract definition is created:

```bash 
curl -X POST http://localhost:29193/management/v3/contractdefinitions/request | jq
```

### Establish EDPS contract

#### 1. Fetch Catalog

```bash
curl -d @resources/requests/fetch-service-provider-catalog.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/catalog/request \
  -s | jq 
```

#### 2. Negotiate contract

Please replace the `{{contract-offer-id}}` placeholder in the `negotiate-edps-contract.json` file with the contract offer id you found in the catalog at the path `dcat:dataset.odrl:hasPolicy.@id`.

```bash
curl -d @resources/requests/negotiate-edps-contract.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/contractnegotiations \
  -s | jq 
```

[Optional] Check if contract negotiation is successfull:

```bash 
curl -X POST http://localhost:29193/management/v3/contractnegotiations/request | jq
```

#### 3. Create transfer process

Please replace the `{{contract-id}}` placeholder in the `start-transfer.json` file with the contract id you found in the check contract negotiations request.

```bash
curl -d @resources/requests/start-transfer.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/transferprocesses \
  -s | jq 
```

[Optional] Check if transfer process is successfull:

```bash 
curl -X POST http://localhost:19193/management/v3/transferprocesses/request | jq
```

#### 4. Get EDR for transfer process

Please replace the `<transfer-process-id>` placeholder in the request with the id you found in the transfer processes request.

```bash 
curl -X GET http://localhost:19193/management/v3/edrs/<transfer-process-id>/dataaddress | jq
```

### Run EDP flow for asset

- **TODO**: use contract agreement for edps
- **TODO**: use contract agreement for daseen

### 1. Create Asset

Use the management Api to create an asset:

```bash
curl -d @resources/requests/create-asset.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/assets \
  -s | jq 
```

[Optional] Check if asset is created:

```bash 
curl -X POST http://localhost:19193/management/v3/assets/request | jq
```


### 2. Create EDPS Job

```bash
curl -d @resources/requests/create-edps-job.json \
  -H 'content-type: application/json' http://localhost:19191/api/edp/edps/assetId1/jobs \
  -s | jq
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

### 3. Get EDPS Result

Replace the jobId in the request with the jobId from the previous step.

```bash
curl -X POST http://localhost:19191/api/edp/edps/assetId1/jobs/{jobId}/result \
  -H 'content-type: application/json' \
  -d @resources/requests/fetch-edps-result.json 
```

### 4. Create Result Asset

```bash
curl -d @resources/requests/create-result-asset.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/assets \
  -s | jq 
```


### 5. Publish Result to Daseen

```bash
curl -X POST http://localhost:19191/api/edp/daseen/resultAssetId1 | jq
```

[optional] Update Daseen entry

```bash
curl -X PUT http://localhost:19191/api/edp/daseen/resultAssetId1 | jq
```

[optional] Delete Daseen entry

```bash
curl -X DELETE http://localhost:19191/api/edp/daseen/resultAssetId1 | jq
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
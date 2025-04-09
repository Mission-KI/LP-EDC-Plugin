#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}Testing PUT request to HTTP server -> EDPS mock server flow${NC}"
echo "============================================================="

# Create a test ZIP file if it doesn't exist
TEST_ZIP="test-edps-result.zip"
if [ ! -f "$TEST_ZIP" ]; then
    echo -e "${BLUE}Creating test ZIP file...${NC}"
    echo "Test content" > test-file.txt
    zip "$TEST_ZIP" test-file.txt
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Created test ZIP file: $TEST_ZIP${NC}"
    else
        echo -e "${RED}Failed to create test ZIP file${NC}"
        exit 1
    fi
fi

# Create a multi-file ZIP to better simulate real-world files
MULTI_FILE_ZIP="multi-file-test.zip"
if [ ! -f "$MULTI_FILE_ZIP" ]; then
    echo -e "${BLUE}Creating multi-file test ZIP...${NC}"
    mkdir -p temp_dir
    # Create multiple files with different content
    for i in {1..5}; do
        echo "Content for file $i with some random data: $RANDOM $RANDOM" > "temp_dir/file_$i.txt"
    done
    # Create a nested directory structure
    mkdir -p temp_dir/subdir1/subdir2
    echo "Nested file content" > temp_dir/subdir1/subdir2/nested.txt
    
    # Create the ZIP file
    zip -r "$MULTI_FILE_ZIP" temp_dir
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Created multi-file ZIP: $MULTI_FILE_ZIP${NC}"
        # Clean up the temporary directory
        rm -rf temp_dir
    else
        echo -e "${RED}Failed to create multi-file ZIP${NC}"
        exit 1
    fi
fi

# Get file sizes
FILE_SIZE=$(stat -f %z "$TEST_ZIP")
MULTI_FILE_SIZE=$(stat -f %z "$MULTI_FILE_ZIP")
echo -e "Simple file size: ${GREEN}$FILE_SIZE bytes${NC}"
echo -e "Multi-file size: ${GREEN}$MULTI_FILE_SIZE bytes${NC}"

# Test 1: Send PUT request with correct file extension
echo -e "\n${BLUE}Test 1: PUT request with .zip extension and Content-Length header${NC}"
echo "Sending PUT request to http://localhost:8080/connector/edp/test-connector/edp-result.zip"

curl -i -X 'PUT' \
  'http://localhost:8080/connector/edp/test-connector/edp-result.zip' \
  -H "Content-Length: $FILE_SIZE" \
  -H 'Content-Type: application/zip' \
  -H 'accept: application/json' \
  --data-binary "@$TEST_ZIP"

# Test 2: Send PUT request without file extension but with Content-Type
echo -e "\n\n${BLUE}Test 2: PUT request without .zip extension but with Content-Type: application/zip${NC}"
echo "Sending PUT request to http://localhost:8080/connector/edp/test-connector/edp-result"

curl -i -X 'PUT' \
  'http://localhost:8080/connector/edp/test-connector/edp-result' \
  -H "Content-Length: $FILE_SIZE" \
  -H 'Content-Type: application/zip' \
  -H 'accept: application/json' \
  --data-binary "@$TEST_ZIP"

# Test 3: Send PUT request with trailing slash
echo -e "\n\n${BLUE}Test 3: PUT request with trailing slash in URL${NC}"
echo "Sending PUT request to http://localhost:8080/connector/edp/test-connector/edp-result.zip/"

curl -i -X 'PUT' \
  'http://localhost:8080/connector/edp/test-connector/edp-result.zip/' \
  -H "Content-Length: $FILE_SIZE" \
  -H 'Content-Type: application/zip' \
  -H 'accept: application/json' \
  --data-binary "@$TEST_ZIP"

# Test 4: Send PUT request with chunked transfer encoding
echo -e "\n\n${YELLOW}Test 4: PUT request with chunked transfer encoding${NC}"
echo "Sending PUT request with chunked encoding (no Content-Length)"

curl -i -X 'PUT' \
  'http://localhost:8080/connector/edp/test-connector/edp-chunked.zip' \
  -H 'Content-Type: application/zip' \
  -H 'Transfer-Encoding: chunked' \
  -H 'accept: application/json' \
  --data-binary "@$TEST_ZIP"

# Test 5: Multi-file ZIP with content-length
echo -e "\n\n${BLUE}Test 5: PUT request with multi-file ZIP${NC}"
echo "Sending multi-file ZIP to simulate real-world API transfer"

curl -i -X 'PUT' \
  'http://localhost:8080/connector/edp/test-connector/multi-file.zip' \
  -H "Content-Length: $MULTI_FILE_SIZE" \
  -H 'Content-Type: application/zip' \
  -H 'accept: application/json' \
  --data-binary "@$MULTI_FILE_ZIP"

# Test 6: Standard PUT request with "edps-result-data.zip" name (the default name expected by the connector)
echo -e "\n\n${GREEN}Test 6: PUT request with edps-result-data.zip filename${NC}"
echo "Sending PUT request with the exact filename expected by the connector"

curl -i -X 'PUT' \
  'http://localhost:8080/edps-result-data.zip' \
  -H "Content-Length: $FILE_SIZE" \
  -H 'Content-Type: application/zip' \
  -H 'accept: application/zip' \
  --data-binary "@$TEST_ZIP"

# Test 7: PUT request with URL-encoded path
echo -e "\n\n${YELLOW}Test 7: PUT request with URL-encoded characters${NC}"
echo "Sending PUT request with URL-encoded filename"

curl -i -X 'PUT' \
  'http://localhost:8080/edps-result-data%2Ezip' \
  -H "Content-Length: $FILE_SIZE" \
  -H 'Content-Type: application/zip' \
  -H 'accept: application/zip' \
  --data-binary "@$TEST_ZIP"

echo -e "\n\n${GREEN}Tests completed${NC}"
echo "Check the server logs for detailed information" 
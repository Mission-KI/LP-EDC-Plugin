#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}Testing GET requests to HTTP file server${NC}"
echo "=============================================="

# Create a test ZIP file in the server data directory
DATA_DIR="util/http-file-server/data"
TEST_ZIP="$DATA_DIR/edps-result-data.zip"

if [ ! -d "$DATA_DIR" ]; then
    mkdir -p "$DATA_DIR"
fi

if [ ! -f "$TEST_ZIP" ]; then
    echo -e "${BLUE}Creating test ZIP file in server data directory...${NC}"
    # Create a simple file
    echo "Test content for edps-result-data.zip" > test-content.txt
    
    # Create a zip file
    zip "$TEST_ZIP" test-content.txt
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Created test ZIP file: $TEST_ZIP${NC}"
        rm test-content.txt
    else
        echo -e "${RED}Failed to create test ZIP file${NC}"
        exit 1
    fi
fi

# Test 1: Basic GET request with correct file name
echo -e "\n${BLUE}Test 1: Basic GET request with correct file name${NC}"
echo "GET request to http://localhost:8080/edps-result-data.zip"

curl -i -X GET \
  'http://localhost:8080/edps-result-data.zip' \
  -H 'accept: application/zip'

# Test 2: GET request with trailing slash
echo -e "\n\n${BLUE}Test 2: GET request with trailing slash${NC}"
echo "GET request to http://localhost:8080/edps-result-data.zip/"

curl -i -X GET \
  'http://localhost:8080/edps-result-data.zip/' \
  -H 'accept: application/zip'

# Test 3: GET request without .zip extension (should still find the file)
echo -e "\n\n${BLUE}Test 3: GET request without .zip extension${NC}"
echo "GET request to http://localhost:8080/edps-result-data"

curl -i -X GET \
  'http://localhost:8080/edps-result-data' \
  -H 'accept: application/zip'

# Test 4: GET request with URL-encoded characters
echo -e "\n\n${BLUE}Test 4: GET request with URL-encoded characters${NC}"
echo "GET request to http://localhost:8080/edps-result-data%2Ezip"

curl -i -X GET \
  'http://localhost:8080/edps-result-data%2Ezip' \
  -H 'accept: application/zip'

echo -e "\n\n${GREEN}Tests completed${NC}"
echo "Check the server logs for detailed information about file serving" 
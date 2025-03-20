#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
NC='\033[0m' # No Color

SERVER_PORT=8080
SERVER_URL="http://localhost:${SERVER_PORT}"

echo -e "${BLUE}Debugging ZIP File Transfer Issues${NC}"
echo "========================================"

# Create test directory if it doesn't exist
TEST_DIR="test_files"
mkdir -p $TEST_DIR

# Create a very simple ZIP file with minimal content
SIMPLE_ZIP="${TEST_DIR}/simple-test.zip"
echo -e "${BLUE}Creating a very simple ZIP file...${NC}"
echo "Simple test content" > ${TEST_DIR}/simple.txt
zip "$SIMPLE_ZIP" ${TEST_DIR}/simple.txt
if [ $? -eq 0 ]; then
    echo -e "${GREEN}Created simple ZIP file: $SIMPLE_ZIP${NC}"
else
    echo -e "${RED}Failed to create simple ZIP file${NC}"
    exit 1
fi

# Get file size and check ZIP integrity locally
FILE_SIZE=$(stat -f %z "$SIMPLE_ZIP" 2>/dev/null || stat -c %s "$SIMPLE_ZIP")
echo -e "ZIP file size: ${GREEN}$FILE_SIZE bytes${NC}"
unzip -t "$SIMPLE_ZIP" > /dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}Local ZIP file integrity check: PASSED${NC}"
else
    echo -e "${RED}Local ZIP file integrity check: FAILED${NC}"
    exit 1
fi

# Test 1: PUT request with explicit Content-Length
echo -e "\n${BLUE}Test 1: PUT request with explicit Content-Length${NC}"
echo "PUT request to ${SERVER_URL}/edps-result-data.zip"

curl -v -X PUT \
  "${SERVER_URL}/edps-result-data.zip" \
  -H "Content-Length: $FILE_SIZE" \
  -H 'Content-Type: application/zip' \
  --data-binary "@$SIMPLE_ZIP"

# Test 2: PUT request with chunked encoding but disable compression
echo -e "\n\n${BLUE}Test 2: PUT request with chunked encoding but NO compression${NC}"
echo "PUT request to ${SERVER_URL}/edps-result-chunked.zip"

curl -v -X PUT \
  "${SERVER_URL}/edps-result-chunked.zip" \
  -H 'Content-Type: application/zip' \
  -H 'Transfer-Encoding: chunked' \
  --compressed false \
  --data-binary "@$SIMPLE_ZIP"

# Test 3: Download the files we just uploaded to verify integrity
echo -e "\n\n${BLUE}Test 3: Downloading the uploaded files to verify integrity${NC}"

echo "Downloading edps-result-data.zip..."
curl -s -X GET "${SERVER_URL}/edps-result-data.zip" -o "${TEST_DIR}/downloaded-result.zip"

echo "Checking integrity of downloaded file..."
unzip -t "${TEST_DIR}/downloaded-result.zip" > /dev/null
if [ $? -eq 0 ]; then
    echo -e "${GREEN}Downloaded ZIP integrity check: PASSED${NC}"
else
    echo -e "${RED}Downloaded ZIP integrity check: FAILED${NC}"
    # Show file size difference
    DL_SIZE=$(stat -f %z "${TEST_DIR}/downloaded-result.zip" 2>/dev/null || stat -c %s "${TEST_DIR}/downloaded-result.zip")
    echo -e "Original size: ${FILE_SIZE} bytes, Downloaded size: ${DL_SIZE} bytes"
    
    # Compare file headers
    echo -e "\n${YELLOW}Comparing file headers:${NC}"
    hexdump -C -n 32 "$SIMPLE_ZIP" | head -2
    hexdump -C -n 32 "${TEST_DIR}/downloaded-result.zip" | head -2
fi

# Test 4: Try with a different HTTP client (Python requests)
echo -e "\n\n${BLUE}Test 4: Using Python requests library${NC}"
echo "This test uses a different HTTP client to rule out curl issues"

cat > ${TEST_DIR}/python_upload.py << 'EOF'
import requests
import sys
import os
import zipfile

def test_zip_upload(filename, url):
    print(f"Testing upload of {filename} to {url}")
    
    # Verify local file is a valid ZIP
    try:
        with zipfile.ZipFile(filename, 'r') as zip_ref:
            file_list = zip_ref.namelist()
            print(f"Local ZIP contains {len(file_list)} files: {', '.join(file_list)}")
            test_result = zip_ref.testzip()
            if test_result is not None:
                print(f"WARNING: Local ZIP file has issues with {test_result}")
            else:
                print("Local ZIP integrity check: PASSED")
    except Exception as e:
        print(f"Error checking local ZIP: {e}")
        return
    
    # Upload file
    with open(filename, 'rb') as f:
        file_data = f.read()
        
    headers = {
        'Content-Type': 'application/zip'
    }
    
    try:
        response = requests.put(url, data=file_data, headers=headers)
        print(f"Upload response: {response.status_code}")
        print(f"Response body: {response.text[:200]}...")
    except Exception as e:
        print(f"Error during upload: {e}")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python python_upload.py <zip_file> <url>")
        sys.exit(1)
    
    test_zip_upload(sys.argv[1], sys.argv[2])
EOF

python3 ${TEST_DIR}/python_upload.py "$SIMPLE_ZIP" "${SERVER_URL}/python-upload.zip"

echo -e "\n\n${GREEN}All tests completed${NC}"
echo "Check the server logs for detailed information about file handling" 
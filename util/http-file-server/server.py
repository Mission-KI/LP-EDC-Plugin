import os
import logging
from http.server import SimpleHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse
import re
import zipfile

PORT = 8080
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
os.makedirs(DATA_DIR, exist_ok=True)

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler()
    ]
)


class CustomHandler(SimpleHTTPRequestHandler):
    def log_request_details(self):
        """Log details of the incoming request."""
        parsed_path = urlparse(self.path).path
        logging.info("Path: %s", parsed_path)
        logging.info("Request Line: %s", self.requestline)
        logging.info("Headers:\n%s", self.headers)


    def do_GET(self):
        """Handle GET requests."""
        self.log_request_details()

        parsed_path = urlparse(self.path).path
        file_name = parsed_path.lstrip("/")  # Extract the file name from the path

        if file_name:
            self.serve_data_file(file_name)
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not found")

    def serve_data_file(self, file_name):
        """Serve the specified file from the data directory."""
        data_file_path = os.path.join(DATA_DIR, file_name)
        
        if not os.path.exists(data_file_path):
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"File not found.")
            return
            
        # Get file size for Content-Length header
        file_size = os.path.getsize(data_file_path)
        if file_size == 0:
            logging.warning(f"File {file_name} has zero size")
            
        # Guess the content type based on the file extension
        content_type = self.guess_type(data_file_path)
        
        # Check if this is a ZIP file and validate it
        if content_type == "application/zip" or file_name.lower().endswith('.zip'):
            try:
                # Attempt to open and validate the ZIP file
                with zipfile.ZipFile(data_file_path, 'r') as zip_file:
                    # Test the ZIP file for integrity
                    zip_test_result = zip_file.testzip()
                    if zip_test_result is not None:
                        logging.warning(f"ZIP file {file_name} is corrupt. First bad file: {zip_test_result}")
                    else:
                        logging.info(f"ZIP file {file_name} is valid. Contains {len(zip_file.namelist())} files")
                
                # Force content type to application/zip even if guess_type failed
                content_type = "application/zip"
                logging.info(f"Serving ZIP file {file_name} with size {file_size} bytes")
            except zipfile.BadZipFile:
                logging.error(f"File {file_name} is not a valid ZIP file")
                # Still serve the file, but log the error
            except Exception as e:
                logging.error(f"Error validating ZIP file {file_name}: {str(e)}")
                # Still serve the file, but log the error
        
        # Send the response with proper headers
        self.send_response(200)
        self.send_header("Content-type", content_type)
        self.send_header("Content-Length", str(file_size))
        self.end_headers()
        
        # Send the file content
        with open(data_file_path, "rb") as file:
            self.wfile.write(file.read())

    def do_POST(self):
        """Handle POST requests for file upload."""
        self.log_request_details()
        parsed_path = urlparse(self.path).path

        if parsed_path == "/":
            self.handle_file_upload()
        elif parsed_path.startswith("/dataplane/result") or "/dataplane/result" in parsed_path:
            logging.info("Received callback call from dataplane")
            
            # Check if this looks like a FileResponse upload based on headers
            content_type = self.headers.get("content-type", "")
            content_disposition = self.headers.get("content-disposition", "")
            
            if content_type.startswith("application/") and "filename" in content_disposition:
                logging.info("Detected potential Starlette FileResponse upload, handling as file upload")
                self.handle_file_upload()
                return
            
            # Process as regular text-based request
            try:
                content_length = int(self.headers.get('Content-Length', 0))
                body = self.rfile.read(content_length).decode("utf-8") if content_length else ""
                logging.info(f"Request Body: {body}")
                self.send_response(200)
                self.end_headers()
            except Exception as e:
                logging.error(f"Error processing request: {str(e)}", exc_info=True)
                self.send_response(400)
                self.end_headers()
                self.wfile.write(f"Error processing request: {e}".encode())
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not found")

    def handle_file_upload(self):
        """Handles file uploads sent as octet stream and saves them in the results directory."""
        logging.info("Handling file upload")
        logging.info(f"Headers: {self.headers}")
        
        # Get content type to check if it's a file
        content_type = self.headers.get("content-type", "")
        logging.info(f"Content type: {content_type}")
        
        # Try to get filename from Content-Disposition header
        content_disposition = self.headers.get("content-disposition")
        file_name = None
        
        if content_disposition:
            # Parse the Content-Disposition header to extract filename
            filename_match = re.search(r'filename=\"([^\"]+)\"', content_disposition)
            if filename_match:
                file_name = filename_match.group(1)
                logging.info(f"Extracted filename from Content-Disposition: {file_name}")
        
        # If no filename found in Content-Disposition, try X-File-Name header as fallback
        if not file_name:
            file_name = self.headers.get("X-File-Name")
            
        # Use default name if no filename was found in headers
        if not file_name:
            file_name = "edps-result-data.zip"
            
        logging.info(f"Saving uploaded file as: {file_name}")
        
        file_path = os.path.join(DATA_DIR, os.path.basename(file_name))
        temp_file_path = file_path + ".tmp"

        try:
            # Check if content-length header is present
            content_length_str = self.headers.get("content-length")
            
            # Create or open a temporary file for writing first
            with open(temp_file_path, "wb") as output_file:
                # If content-length is available, use it for reading data in chunks
                if content_length_str:
                    content_length = int(content_length_str)
                    logging.info(f"Content length specified: {content_length} bytes")
                    
                    # Read all data in chunks to handle large files
                    remaining = content_length
                    chunk_size = 8192  # 8KB chunks
                    total_bytes = 0
                    
                    while remaining > 0:
                        chunk = self.rfile.read(min(remaining, chunk_size))
                        if not chunk:
                            logging.warning("Premature end of data (connection closed?)")
                            break
                        output_file.write(chunk)
                        total_bytes += len(chunk)
                        remaining -= len(chunk)
                    
                    # Check if we received the complete content
                    if total_bytes < content_length:
                        logging.warning(f"Expected {content_length} bytes but received {total_bytes} bytes")
                else:
                    # Handle chunked transfer encoding or unknown length
                    logging.info("No Content-Length header. Reading data until EOF.")
                    total_bytes = 0
                    chunk_size = 8192  # 8KB chunks
                    
                    while True:
                        chunk = self.rfile.read(chunk_size)
                        if not chunk:
                            break
                        output_file.write(chunk)
                        total_bytes += len(chunk)
                        
                        # Log progress for large files
                        if total_bytes % (10 * 1024 * 1024) == 0:  # Log every 10MB
                            logging.info(f"Read {total_bytes} bytes so far...")
            
            # Check if the file was saved successfully
            if os.path.exists(temp_file_path):
                file_size = os.path.getsize(temp_file_path)
                logging.info(f"File successfully saved to temporary location, size: {file_size} bytes")
                
                if file_size == 0:
                    logging.error("File was saved but has 0 bytes!")
                elif file_size != total_bytes and content_length_str:
                    logging.warning(f"Mismatch: received {total_bytes} bytes but saved file size is {file_size} bytes")
                
                # Validate zip file if it's meant to be one
                is_valid_zip = False
                if file_name.lower().endswith('.zip') or content_type in ["application/zip", "application/x-zip-compressed"]:
                    try:
                        with zipfile.ZipFile(temp_file_path, 'r') as zip_file:
                            # Test the ZIP file for integrity
                            zip_test_result = zip_file.testzip()
                            if zip_test_result is not None:
                                logging.warning(f"Uploaded ZIP file is corrupt. First bad file: {zip_test_result}")
                            else:
                                logging.info(f"Uploaded ZIP file is valid. Contains {len(zip_file.namelist())} files")
                                is_valid_zip = True
                    except zipfile.BadZipFile:
                        logging.error(f"Uploaded file is not a valid ZIP file")
                    except Exception as e:
                        logging.error(f"Error validating ZIP file: {str(e)}")
                
                # Move the temporary file to its final location
                if os.path.exists(file_path):
                    os.remove(file_path)  # Remove existing file if it exists
                os.rename(temp_file_path, file_path)
                
                # Prepare response message
                response_msg = f"File upload processed. Received {total_bytes} bytes as {file_name}"
                if file_name.lower().endswith('.zip'):
                    response_msg += f". ZIP file validation: {'PASSED' if is_valid_zip else 'FAILED'}"
            else:
                logging.error(f"Temporary file was not saved at {temp_file_path}")
                response_msg = "File upload failed: Temporary file not saved"
                
            # Send response
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(response_msg.encode())
            
        except Exception as e:
            logging.error(f"Error saving file: {str(e)}", exc_info=True)
            self.send_response(500)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(f"Error saving file: {e}".encode())
            
            # Clean up temporary file if it exists
            if os.path.exists(temp_file_path):
                try:
                    os.remove(temp_file_path)
                except:
                    pass


if __name__ == "__main__":
    server_address = ("", PORT)
    httpd = HTTPServer(server_address, CustomHandler)
    logging.info("Serving on http://localhost:%s", PORT)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()
        logging.info("Server stopped.")
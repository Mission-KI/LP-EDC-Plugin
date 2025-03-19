import os
import logging
from http.server import SimpleHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse
import re

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

        # TODO: try to parse the file as zip
        
        if os.path.exists(data_file_path):
            # Guess the content type based on the file extension
            content_type = self.guess_type(data_file_path)
            self.send_response(200)
            self.send_header("Content-type", content_type)
            self.end_headers()
            with open(data_file_path, "rb") as file:
                self.wfile.write(file.read())
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"File not found.")

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

        try:
            # Check if content-length header is present
            content_length_str = self.headers.get("content-length")
            
            # Create or open the file for writing
            with open(file_path, "wb") as output_file:
                # If content-length is available, use it for reading data in chunks
                if content_length_str:
                    content_length = int(content_length_str)
                    logging.info(f"Content length specified: {content_length} bytes")
                    
                    # Read all data in chunks to handle large files
                    data = bytearray()
                    remaining = content_length
                    chunk_size = 8192  # 8KB chunks
                    
                    while remaining > 0:
                        chunk = self.rfile.read(min(remaining, chunk_size))
                        if not chunk:
                            logging.warning("Premature end of data (connection closed?)")
                            break
                        output_file.write(chunk)
                        data.extend(chunk)  # Keep track of data for logging
                        remaining -= len(chunk)
                    
                    # Check if we received the complete content
                    if len(data) < content_length:
                        logging.warning(f"Expected {content_length} bytes but received {len(data)} bytes")
                    
                    total_bytes = len(data)
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
            
            # Log the final result
            if os.path.exists(file_path):
                file_size = os.path.getsize(file_path)
                logging.info(f"File successfully saved to {file_path}, size: {file_size} bytes")
                
                if file_size == 0:
                    logging.error("File was saved but has 0 bytes!")
                elif file_size != total_bytes and content_length_str:
                    logging.warning(f"Mismatch: received {total_bytes} bytes but saved file size is {file_size} bytes")
            else:
                logging.error(f"File was not saved at {file_path}")
                
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(f"File upload processed. Received {total_bytes} bytes as {file_name}".encode())
            
        except Exception as e:
            logging.error(f"Error saving file: {str(e)}", exc_info=True)
            self.send_response(500)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(f"Error saving file: {e}".encode())


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
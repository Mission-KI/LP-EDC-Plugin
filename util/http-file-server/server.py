import os
import logging
from http.server import SimpleHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, unquote
import re
import zipfile
import shutil
import uuid
import socket

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
        logging.info("--- Request Details ---")
        logging.info("Method: %s", self.command)  # self.command contains the HTTP method
        logging.info("Path: %s", parsed_path)
        
        # Format headers for better readability
        header_str = ""
        for header, value in self.headers.items():
            header_str += f"{header}: {value}\n"
        logging.info("Headers: %s", header_str)


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
        # URL decode the file name to handle any URL-encoded characters
        file_name = unquote(file_name)
        
        # Check for and handle trailing slashes
        if file_name.endswith('/'):
            file_name = file_name[:-1]
            logging.info(f"Removed trailing slash from filename: {file_name}")
            
        logging.info(f"Looking for file: {file_name}")
        
        # Try exact filename first
        data_file_path = os.path.join(DATA_DIR, file_name)
        
        # If file doesn't exist and it's a ZIP request but without .zip extension, try adding it
        if not os.path.exists(data_file_path) and not file_name.lower().endswith('.zip'):
            zip_path = os.path.join(DATA_DIR, f"{file_name}.zip")
            if os.path.exists(zip_path):
                data_file_path = zip_path
                file_name = f"{file_name}.zip"
                logging.info(f"Found file with .zip extension: {file_name}")
        
        if not os.path.exists(data_file_path):
            logging.warning(f"File not found: {data_file_path}")
            self.send_response(404)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"File not found.")
            return
            
        # Get file size for Content-Length header
        file_size = os.path.getsize(data_file_path)
        if file_size == 0:
            logging.warning(f"File {file_name} has zero size")
            
        # Determine content type
        if file_name.lower().endswith('.zip'):
            content_type = "application/zip"
        else:
            # Use the server's built-in guess_type for other file types
            content_type = self.guess_type(data_file_path)
            if not content_type:
                content_type = "application/octet-stream"  # Default to binary if unknown
        
        # Log detailed information about the file being served
        logging.info(f"Serving file: {file_name}")
        logging.info(f"Full path: {data_file_path}")
        logging.info(f"Size: {file_size} bytes")
        logging.info(f"Content-Type: {content_type}")
        
        # Check if this is a ZIP file and validate it
        if content_type == "application/zip" or file_name.lower().endswith('.zip'):
            try:
                # Attempt to open and validate the ZIP file
                with zipfile.ZipFile(data_file_path, 'r') as zip_file:
                    # Get info about the ZIP contents
                    file_list = zip_file.namelist()
                    file_count = len(file_list)
                    
                    # Test the ZIP file for integrity
                    zip_test_result = zip_file.testzip()
                    if zip_test_result is not None:
                        logging.warning(f"ZIP file {file_name} is corrupt. First bad file: {zip_test_result}")
                    else:
                        logging.info(f"ZIP file {file_name} is valid. Contains {file_count} files")
                        if file_count > 0:
                            logging.info(f"First few files in ZIP: {', '.join(file_list[:5] if len(file_list) > 5 else file_list)}")
                            
                        # Log compression methods used
                        compression_methods = {}
                        for info in zip_file.infolist():
                            method = info.compress_type
                            method_name = {
                                zipfile.ZIP_STORED: "STORED (no compression)",
                                zipfile.ZIP_DEFLATED: "DEFLATED",
                                zipfile.ZIP_BZIP2: "BZIP2",
                                zipfile.ZIP_LZMA: "LZMA"
                            }.get(method, f"Unknown ({method})")
                            
                            if method_name in compression_methods:
                                compression_methods[method_name] += 1
                            else:
                                compression_methods[method_name] = 1
                        
                        for method, count in compression_methods.items():
                            logging.info(f"  {method}: {count} files")
                
                # Force content type to application/zip
                content_type = "application/zip"
            except zipfile.BadZipFile as e:
                logging.error(f"File {file_name} is not a valid ZIP file: {str(e)}")
                # Try to see what's wrong with the file
                try:
                    with open(data_file_path, 'rb') as f:
                        header = f.read(4).hex()
                        logging.error(f"File header (first 4 bytes): {header}")
                        if header != '504b0304':  # ZIP magic number
                            logging.error(f"Invalid ZIP header. Expected '504b0304', got '{header}'")
                except Exception as header_e:
                    logging.error(f"Error reading file header: {str(header_e)}")
            except Exception as e:
                logging.error(f"Error validating ZIP file {file_name}: {str(e)}")
        
        # Send the response with proper headers
        self.send_response(200)
        
        # Set proper headers
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(file_size))
        
        # Add Content-Disposition header for file downloads
        if file_name.lower().endswith('.zip'):
            self.send_header("Content-Disposition", f'attachment; filename="{os.path.basename(file_name)}"')
        
        # Add CORS headers to allow cross-origin requests
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        
        self.end_headers()
        
        # Send the file content in chunks to handle large files
        with open(data_file_path, "rb") as file:
            chunk_size = 8192  # 8KB chunks
            total_sent = 0
            
            while True:
                chunk = file.read(chunk_size)
                if not chunk:
                    break
                    
                self.wfile.write(chunk)
                total_sent += len(chunk)
                
                # Log progress for large files
                if total_sent % (10 * 1024 * 1024) == 0:  # Log every 10MB
                    logging.info(f"Sent {total_sent} of {file_size} bytes ({(total_sent / file_size) * 100:.1f}%)")
            
            logging.info(f"File transfer complete. Sent {total_sent} bytes.")

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

    def do_PUT(self):
        """Handle PUT requests specifically for uploading files."""
        self.log_request_details()
        parsed_path = urlparse(self.path).path
        
        # Extract the file name from the path - assuming the file name is the last part of the path
        path_parts = [p for p in parsed_path.split('/') if p]
        if not path_parts:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Invalid path for PUT request")
            return
            
        # Use the last part of the path as filename
        file_name = path_parts[-1]
        
        # Remove file extension like .zip/ if it has a trailing slash
        if file_name.endswith('/'):
            file_name = file_name[:-1]
            logging.info(f"Removed trailing slash from filename: {file_name}")
            
        logging.info(f"PUT request for file: {file_name}")
        
        # Use the same file upload logic as POST but with the filename from the URL
        self.handle_file_upload(file_name)

    def handle_file_upload(self, provided_filename=None):
        """Handles file uploads sent as octet stream and saves them in the results directory."""
        logging.info(f"Handling file upload, method: {self.command}")
        logging.info(f"Headers: {self.headers}")
        
        # Get content type to check if it's a file
        content_type = self.headers.get("content-type", "")
        logging.info(f"Content type: {content_type}")
        
        # If a filename was provided from the URL path (in case of PUT), use it
        file_name = provided_filename
        
        # If no filename provided, try to get it from headers
        if not file_name:
            # Try to get filename from Content-Disposition header
            content_disposition = self.headers.get("content-disposition")
            
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
        
        # Ensure ZIP files have the correct extension
        is_zip_content = False
        if content_type in ["application/zip", "application/x-zip-compressed"]:
            is_zip_content = True
            if not file_name.lower().endswith('.zip'):
                file_name = f"{file_name}.zip"
                logging.info(f"Added .zip extension to filename based on content type: {file_name}")
        
        logging.info(f"Saving uploaded file as: {file_name}")
        
        # Define file paths with a unique temp file name to avoid conflicts
        file_path = os.path.join(DATA_DIR, os.path.basename(file_name))
        temp_file_path = os.path.join(DATA_DIR, f"temp_{uuid.uuid4().hex}_{os.path.basename(file_name)}")
        
        try:
            # Get content length and transfer encoding info
            content_length_str = self.headers.get('Content-Length')
            content_length = int(content_length_str) if content_length_str else None
            transfer_encoding = self.headers.get('Transfer-Encoding', '')
            is_chunked = 'chunked' in transfer_encoding.lower()
            
            logging.info(f"Content-Length: {content_length if content_length else 'Not specified'}")
            logging.info(f"Transfer-Encoding: {transfer_encoding if transfer_encoding else 'Not specified'}")
            logging.info(f"Chunked encoding: {'Yes' if is_chunked else 'No'}")
            
            total_bytes = 0
            
            # Create or open a temporary file for writing
            with open(temp_file_path, "wb") as output_file:
                # Handle different transfer modes
                if is_chunked:
                    logging.info("Reading chunked data...")
                    while True:
                        chunk_size_line = self.rfile.readline().strip()
                        try:
                            chunk_size = int(chunk_size_line, 16)
                        except ValueError:
                            logging.error(f"Invalid chunk size: {chunk_size_line}")
                            break
                            
                        if chunk_size == 0:
                            break
                            
                        chunk_data = self.rfile.read(chunk_size)
                        output_file.write(chunk_data)
                        total_bytes += len(chunk_data)
                        self.rfile.readline()  # Read trailing CRLF
                        
                        if total_bytes % (1024 * 1024) == 0:  # Log every 1MB
                            logging.info(f"Read {total_bytes} bytes so far...")
                
                elif content_length:
                    logging.info(f"Reading {content_length} bytes of data...")
                    remaining = content_length
                    chunk_size = 8192  # 8KB chunks
                    
                    while remaining > 0:
                        read_size = min(remaining, chunk_size)
                        chunk = self.rfile.read(read_size)
                        if not chunk:
                            logging.warning("Premature end of data (connection closed?)")
                            break
                        output_file.write(chunk)
                        total_bytes += len(chunk)
                        remaining -= len(chunk)
                        
                        if total_bytes % (1024 * 1024) == 0:  # Log every 1MB
                            logging.info(f"Read {total_bytes} of {content_length} bytes")
                
                else:
                    # Handle unknown length
                    logging.info("No Content-Length or chunked encoding. Reading until EOF...")
                    chunk_size = 8192  # 8KB chunks
                    
                    while True:
                        try:
                            chunk = self.rfile.read(chunk_size)
                            if not chunk:
                                break
                            output_file.write(chunk)
                            total_bytes += len(chunk)
                            
                            # Log progress for large files
                            if total_bytes % (1024 * 1024) == 0:  # Log every 1MB
                                logging.info(f"Read {total_bytes} bytes so far...")
                        except Exception as e:
                            logging.error(f"Error reading data: {str(e)}")
                            break
                
                # Make sure all data is written to disk
                output_file.flush()
                os.fsync(output_file.fileno())
            
            logging.info(f"Successfully read and saved {total_bytes} bytes to {temp_file_path}")
            
            # Check if the file was saved successfully
            if os.path.exists(temp_file_path):
                file_size = os.path.getsize(temp_file_path)
                logging.info(f"File successfully saved to temporary location, size: {file_size} bytes")
                
                if file_size == 0:
                    logging.error("File was saved but has 0 bytes!")
                elif file_size != total_bytes and content_length_str and not is_chunked:
                    logging.warning(f"Mismatch: received {total_bytes} bytes but saved file size is {file_size} bytes")
                
                # Validate zip file if it's meant to be one
                is_valid_zip = False
                if file_name.lower().endswith('.zip') or is_zip_content:
                    validation_message = self.validate_zip_file(temp_file_path)
                    is_valid_zip = "Valid ZIP file" in validation_message
                    
                    if is_valid_zip:
                        logging.info(f"ZIP validation passed: {validation_message}")
                    else:
                        logging.warning(f"ZIP validation failed: {validation_message}")
                else:
                    # Not a ZIP file
                    is_valid_zip = True
                    logging.info("Not a ZIP file, skipping validation")
                
                # Move the temporary file to its final location
                try:
                    if os.path.exists(file_path):
                        os.remove(file_path)  # Remove existing file if it exists
                    
                    # Use shutil.move which is safer across filesystems
                    shutil.move(temp_file_path, file_path)
                    logging.info(f"Successfully moved file to final location: {file_path}")
                except Exception as e:
                    logging.error(f"Error moving file: {str(e)}")
                    # Try to keep the temp file if we couldn't move it
                    validation_message += f" (but failed to move file: {str(e)})"
                
                # Prepare response message
                response_msg = f"File upload processed. Received {total_bytes} bytes as {file_name}"
                if file_name.lower().endswith('.zip') or is_zip_content:
                    response_msg += f". ZIP file validation: {'PASSED' if is_valid_zip else 'FAILED'}"
            else:
                logging.error(f"Temporary file was not saved at {temp_file_path}")
                response_msg = "File upload failed: Temporary file not saved"
                
            # Send response
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(response_msg.encode())
            
        except (BrokenPipeError, ConnectionResetError, socket.error) as e:
            logging.error(f"Connection error while handling file upload: {str(e)}")
            # Clean up temp file if it exists
            if os.path.exists(temp_file_path):
                try:
                    os.remove(temp_file_path)
                except:
                    pass
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

    def validate_zip_file(self, zip_file_path):
        """Validate a ZIP file and return a message about its validity."""
        try:
            file_size = os.path.getsize(zip_file_path)
            if file_size == 0:
                return "File is empty (0 bytes)"
                
            with zipfile.ZipFile(zip_file_path, 'r') as zip_file:
                # Get and log info about the ZIP file
                file_list = zip_file.namelist()
                file_count = len(file_list)
                logging.info(f"ZIP file contains {file_count} files:")
                
                for i, filename in enumerate(file_list[:20]):  # Log first 20 files
                    try:
                        info = zip_file.getinfo(filename)
                        compressed_size = info.compress_size
                        uncompressed_size = info.file_size
                        compression_ratio = (compressed_size / uncompressed_size) * 100 if uncompressed_size > 0 else 0
                        logging.info(f"  {i+1}. {filename}: "
                                    f"compressed={compressed_size} bytes, "
                                    f"uncompressed={uncompressed_size} bytes, "
                                    f"ratio={compression_ratio:.1f}%")
                    except Exception as e:
                        logging.error(f"Error getting info for file {filename}: {str(e)}")
                
                if file_count > 20:
                    logging.info(f"... and {file_count - 20} more files")
                
                # Test the ZIP file for integrity
                logging.info("Testing ZIP file integrity...")
                zip_test_result = zip_file.testzip()
                if zip_test_result is not None:
                    return f"ZIP file is corrupt. First bad file: {zip_test_result}"
                
                # Check compression method
                compression_methods = {}
                for info in zip_file.infolist():
                    method = info.compress_type
                    method_name = {
                        zipfile.ZIP_STORED: "STORED (no compression)",
                        zipfile.ZIP_DEFLATED: "DEFLATED",
                        zipfile.ZIP_BZIP2: "BZIP2",
                        zipfile.ZIP_LZMA: "LZMA"
                    }.get(method, f"Unknown ({method})")
                    
                    if method_name in compression_methods:
                        compression_methods[method_name] += 1
                    else:
                        compression_methods[method_name] = 1
                
                logging.info("Compression methods used:")
                for method, count in compression_methods.items():
                    logging.info(f"  {method}: {count} files")
                
                return f"Valid ZIP file with {file_count} files"
                
        except zipfile.BadZipFile as e:
            error_msg = f"Not a valid ZIP file: {str(e)}"
            logging.error(error_msg)
            # Try to identify what's wrong with the file
            try:
                with open(zip_file_path, 'rb') as f:
                    header = f.read(4).hex()
                    logging.error(f"File header (first 4 bytes): {header}")
                    if header != '504b0304':  # ZIP file magic number
                        logging.error(f"Invalid ZIP header. Expected '504b0304', got '{header}'")
            except Exception as header_e:
                logging.error(f"Error reading file header: {str(header_e)}")
            return error_msg
        except Exception as e:
            error_msg = f"Error validating ZIP file: {str(e)}"
            logging.error(error_msg)
            return error_msg


if __name__ == "__main__":
    server_address = ("", PORT)
    httpd = HTTPServer(server_address, CustomHandler)
    logging.info("Serving on http://localhost:%s", PORT)
    logging.info("Supports GET, POST, and PUT methods")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()
        logging.info("Server stopped.")
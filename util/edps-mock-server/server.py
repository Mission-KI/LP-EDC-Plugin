import os
import logging
import json
import uuid
import socket
from http.server import SimpleHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse
import zipfile

PORT = 8081
TIMEOUT = 120  # Increased timeout from 30 to 120 seconds
BASE_DIR = os.path.dirname(os.path.abspath(__file__))  # Base directory is /server
DATA_DIR = os.path.join(BASE_DIR, "data")  # Directory for GET request files
os.makedirs(DATA_DIR, exist_ok=True)

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler()
    ]
)

class CustomHandler(SimpleHTTPRequestHandler):
    # Set timeout using the constant
    timeout = TIMEOUT
    
    def log_request_details(self):
        """Log details of the incoming request."""
        logging.info("--- Request Details ---")
        logging.info(f"Method: {self.command}")
        logging.info(f"Path: {self.path}")
        logging.info(f"Headers: {self.headers}")

    def do_GET(self):
        """Handle GET requests."""
        self.log_request_details()
        parsed_path = urlparse(self.path).path

        if parsed_path.startswith("/v1/dataspace/analysisjob/") and "/result" in parsed_path:
            self.serve_analysisjob_result(parsed_path)
        elif parsed_path.startswith("/v1/dataspace/analysisjob/") and "/status" in parsed_path:
            self.serve_analysisjob_status(parsed_path)
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not found")

    def serve_analysisjob_status(self, parsed_path):
        """Serve job status."""
        try:
            parts = parsed_path.split("/")
            job_id = parts[4]  # Extract job_id
        except IndexError:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Invalid path format.")
            return

        response = {
            "job_id": f"{job_id}",
            "state": "WAITING_FOR_DATA",
            "state_detail": "Job is waiting for data to be uploaded."
        }
        response_bytes = json.dumps(response, indent=2).encode('utf-8')

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response_bytes)))
        self.end_headers()
        self.wfile.write(response_bytes)

    def serve_analysisjob_result(self, parsed_path):
        """Serve the processed .zip file."""
        try:
            parts = parsed_path.split("/")
            job_id = parts[4]  # Extract job_id
        except IndexError:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Invalid path format.")
            return

        # Look for the corresponding .zip file
        zip_file_name = f"{job_id}.zip"
        zip_file_path = os.path.join(DATA_DIR, zip_file_name)

        if os.path.exists(zip_file_path):
            try:
                file_size = os.path.getsize(zip_file_path)
                logging.info(f"Serving zip file {zip_file_name} with size {file_size} bytes")
                
                # Check if file is empty and create dummy content if needed
                if file_size == 0:
                    logging.warning(f"Zip file {zip_file_path} is empty (0 bytes), creating dummy content")
                    # Create a dummy CSV file
                    csv_file_path = os.path.join(DATA_DIR, f"{job_id}.csv")
                    with open(csv_file_path, "w") as f:
                        f.write("dummy,data\n1,2\n")
                        f.flush()
                        os.fsync(f.fileno())
                    
                    # Create a new zip file with the dummy CSV
                    with zipfile.ZipFile(zip_file_path, "w") as zip_file:
                        zip_file.write(csv_file_path, arcname=f"{job_id}.csv")
                    
                    # Update file size
                    file_size = os.path.getsize(zip_file_path)
                    logging.info(f"Created dummy zip file with size {file_size} bytes")
                
                self.send_response(200)
                self.send_header("Content-Type", "application/zip")
                self.send_header("Content-Length", str(file_size))
                self.send_header("Content-Disposition", f"attachment; filename=\"{zip_file_name}\"")
                self.send_header("Connection", "keep-alive")
                self.end_headers()
                
                # Stream the file in chunks to avoid memory issues
                with open(zip_file_path, "rb") as file:
                    total_sent = 0
                    chunk_size = 8192  # 8KB chunks
                    while True:
                        chunk = file.read(chunk_size)
                        if not chunk:
                            break
                        try:
                            bytes_sent = self.wfile.write(chunk)
                            self.wfile.flush()  # Flush after each chunk
                            total_sent += bytes_sent
                            if total_sent % (1024 * 1024) == 0:  # Log every 1MB
                                logging.info(f"Sent {total_sent} of {file_size} bytes")
                        except (BrokenPipeError, ConnectionResetError, socket.error) as e:
                            logging.error(f"Connection error while streaming file: {str(e)}")
                            # Don't try to send more data or error responses if connection is broken
                            return
                    
                    logging.info(f"Successfully sent {total_sent} of {file_size} bytes")
            except Exception as e:
                logging.error(f"Error serving zip file: {str(e)}")
                try:
                    self.send_response(500)
                    self.send_header("Content-Type", "application/json")
                    self.end_headers()
                    response = {
                        "status": "error",
                        "message": f"Error serving file: {str(e)}"
                    }
                    self.wfile.write(json.dumps(response).encode())
                except (BrokenPipeError, ConnectionResetError, socket.error):
                    logging.error("Could not send error response - connection already closed")
        else:
            logging.warning(f"Result file not found: {zip_file_path}")
            
            # Create a dummy file if it doesn't exist
            try:
                logging.info(f"Creating dummy zip file for job_id: {job_id}")
                # Create a dummy CSV file
                csv_file_path = os.path.join(DATA_DIR, f"{job_id}.csv")
                with open(csv_file_path, "w") as f:
                    f.write("dummy,data\n1,2\n")
                    f.flush()
                    os.fsync(f.fileno())
                
                # Create a new zip file with the dummy CSV
                with zipfile.ZipFile(zip_file_path, "w") as zip_file:
                    zip_file.write(csv_file_path, arcname=f"{job_id}.csv")
                
                file_size = os.path.getsize(zip_file_path)
                logging.info(f"Created dummy zip file with size {file_size} bytes")
                
                self.send_response(200)
                self.send_header("Content-Type", "application/zip")
                self.send_header("Content-Length", str(file_size))
                self.send_header("Content-Disposition", f"attachment; filename=\"{zip_file_name}\"")
                self.send_header("Connection", "keep-alive")
                self.end_headers()
                
                # Stream the file
                with open(zip_file_path, "rb") as file:
                    total_sent = 0
                    chunk_size = 8192  # 8KB chunks
                    while True:
                        chunk = file.read(chunk_size)
                        if not chunk:
                            break
                        try:
                            bytes_sent = self.wfile.write(chunk)
                            self.wfile.flush()
                            total_sent += bytes_sent
                        except (BrokenPipeError, ConnectionResetError, socket.error) as e:
                            logging.error(f"Connection error while streaming dummy file: {str(e)}")
                            return
                    
                    logging.info(f"Successfully sent dummy file: {total_sent} bytes")
            except Exception as e:
                logging.error(f"Error creating and serving dummy file: {str(e)}")
                self.send_response(404)
                self.end_headers()
                self.wfile.write(b"Result file not found and could not create dummy file.")

    def do_POST(self):
        """Handle POST requests."""
        self.log_request_details()
        parsed_path = urlparse(self.path).path

        if parsed_path == "/v1/dataspace/analysisjob":
            self.handle_create_analysisjob()
        elif parsed_path.startswith("/v1/dataspace/analysisjob/") and "/data/file" in parsed_path:
            self.handle_analysisjob_upload(parsed_path)
        elif parsed_path == "/connector/edp":
            self.handle_daseen_create()
        elif parsed_path.startswith("/connector/edp/"):
            self.handle_daseen_upload(parsed_path)
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not found")

    def do_PUT(self):
        """Handle PUT requests."""
        self.log_request_details()
        parsed_path = urlparse(self.path).path

        if parsed_path.startswith("/connector/edp/"):
            self.handle_daseen_update(parsed_path)
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not found")

    def do_DELETE(self):
        """Handle DELETE requests."""
        self.log_request_details()
        parsed_path = urlparse(self.path).path

        if parsed_path.startswith("/connector/edp/"):
            self.handle_daseen_delete(parsed_path)
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not found")

    def handle_create_analysisjob(self):
        """Create a new analysis job and return mock response."""
        try:
            # Verify content type if needed
            content_type = self.headers.get('Content-Type', '')
            if content_type and 'application/json' not in content_type:
                self.send_error(415, "Unsupported Media Type. Expected application/json")
                return

            job_id = str(uuid.uuid4())

            response = {
                "job_id": job_id,
                "state": "WAITING_FOR_DATA",
                "state_detail": "Job is waiting for data to be uploaded."
            }

            response_bytes = json.dumps(response, indent=2).encode('utf-8')

            self.send_response(201)
            self.send_header("Content-Length", str(len(response_bytes)))
            self.send_header("Content-Type", "application/json")
            self.send_header("Location", "/v1/dataspace/analysisjob")
            self.end_headers()
            self.wfile.write(response_bytes)
            self.wfile.flush()

            logging.info(f"Created new analysis job with ID: {job_id}")
        except (BrokenPipeError, ConnectionResetError, socket.error) as e:
            logging.error(f"Connection error while creating analysis job: {str(e)}")
        except Exception as e:
            logging.error(f"Error creating analysis job: {str(e)}")
            try:
                self.send_error(500, f"Internal server error: {str(e)}")
            except (BrokenPipeError, ConnectionResetError, socket.error):
                logging.error("Could not send error response - connection already closed")

    def handle_analysisjob_upload(self, parsed_path):
        """Handle file uploads for analysis jobs."""
        try:
            # Expected format: /v1/dataspace/analysisjob/{job_id}/data
            parts = parsed_path.split("/")
            job_id = parts[4]  # Extract job_id
        except IndexError:
            self.send_response(400)
            self.end_headers()
            self.wfile.write(b"Invalid path format.")
            return

        # Save the file as {jobid}-{filename}.csv
        result_file_name = f"{job_id}.csv"
        result_file_path = os.path.join(DATA_DIR, result_file_name)

        try:
            # Send response headers first to prevent timeout
            self.send_response(201)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            
            # Read the entire input at once
            data = self.rfile.read()
            bytes_written = len(data)
            logging.info(f"Read {bytes_written} bytes from input")
            
            # Write the data to file
            with open(result_file_path, "wb") as output_file:
                output_file.write(data)
                output_file.flush()
                os.fsync(output_file.fileno())
            
            logging.info(f"File upload complete. Written {bytes_written} bytes to {result_file_path}")

            # Process the file if we received data
            if bytes_written > 0:
                self.process_edps(result_file_name)
                response = {
                    "status": "success",
                    "message": "File uploaded and processed successfully."
                }
            else:
                response = {
                    "status": "error",
                    "message": "No data received."
                }
            
            self.wfile.write(json.dumps(response).encode())
            self.wfile.flush()

        except Exception as e:
            logging.error(f"Error during file upload: {str(e)}")
            if not self.wfile.closed:
                response = {
                    "status": "error",
                    "message": str(e)
                }
                self.wfile.write(json.dumps(response).encode())
                self.wfile.flush()

    def process_edps(self, result_file_name):
        """Process the CSV file into a .zip file."""
        result_file_path = os.path.join(DATA_DIR, result_file_name)
        zip_file_name = result_file_name.replace(".csv", ".zip")
        zip_file_path = os.path.join(DATA_DIR, zip_file_name)

        try:
            # Check if the source file exists and has content
            if not os.path.exists(result_file_path):
                logging.error(f"Source file {result_file_path} does not exist")
                return
            
            file_size = os.path.getsize(result_file_path)
            logging.info(f"Processing file {result_file_name} with size {file_size} bytes")
            
            if file_size == 0:
                logging.error(f"Source file {result_file_path} is empty (0 bytes)")
                # Create a dummy content if the file is empty to ensure we have something to return
                with open(result_file_path, "w") as f:
                    f.write("dummy,data\n1,2\n")
                    f.flush()
                    os.fsync(f.fileno())
                logging.info(f"Added dummy content to empty file {result_file_path}")
            
            # Create the zip file with the CSV
            with zipfile.ZipFile(zip_file_path, "w") as zip_file:
                zip_file.write(result_file_path, arcname=result_file_name)
                
            # Verify the zip file was created and has content
            if os.path.exists(zip_file_path):
                zip_size = os.path.getsize(zip_file_path)
                logging.info(f"Created zip file {zip_file_name} with size {zip_size} bytes")
                if zip_size == 0:
                    logging.error(f"Created zip file is empty (0 bytes)")
            else:
                logging.error(f"Failed to create zip file {zip_file_path}")
                
        except Exception as e:
            logging.error(f"Error creating zip file: {str(e)}")

    def handle_daseen_create(self):
        """Handle creation of new daseen connector."""
        try:
            resource_id = str(uuid.uuid4())  # Generate a unique ID
            
            self.send_response(201)
            self.send_header("Content-Type", "application/json")
            self.end_headers()

            response = {
                "state": "SUCCESS",
                "id": resource_id,
                "message": "EDPS connector created"
            }
            self.wfile.write(json.dumps(response).encode())
        except (BrokenPipeError, ConnectionResetError, socket.error) as e:
            logging.error(f"Connection error while creating daseen connector: {str(e)}")
        except Exception as e:
            logging.error(f"Error creating connector: {str(e)}")
            try:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(f"Error creating connector: {str(e)}".encode())
            except (BrokenPipeError, ConnectionResetError, socket.error):
                logging.error("Could not send error response - connection already closed")

    def handle_daseen_upload(self, parsed_path):
        """Handle daseen data upload to specific connector."""
        try:
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()

            response = {
                "state": "SUCCESS",
                "state_detail": "EDPS data published to Daseen"
            }
            self.wfile.write(json.dumps(response).encode())
        except (BrokenPipeError, ConnectionResetError, socket.error) as e:
            logging.error(f"Connection error while handling daseen upload: {str(e)}")
        except Exception as e:
            logging.error(f"Error uploading data: {str(e)}")
            try:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(f"Error uploading data: {str(e)}".encode())
            except (BrokenPipeError, ConnectionResetError, socket.error):
                logging.error("Could not send error response - connection already closed")

    def handle_daseen_update(self, parsed_path):
        """Handle daseen connector updates."""
        try:
            # Extract connector ID and other path components
            path_parts = [p for p in parsed_path.split('/') if p]
            if len(path_parts) < 3:
                self.send_response(400)
                self.end_headers()
                self.wfile.write(b"Invalid path format.")
                return
                
            connector_id = path_parts[2]  # Get the ID from /connector/edp/{id}/...
            logging.info(f"Processing PUT request for connector ID: {connector_id}")
            
            # Check if this is likely a zip file upload
            content_type = self.headers.get('Content-Type', '')
            is_zip_upload = False
            zip_filename = None
            
            if 'application/zip' in content_type or parsed_path.lower().endswith('.zip') or parsed_path.lower().endswith('.zip/'):
                is_zip_upload = True
                # Get filename from the path
                if parsed_path.lower().endswith('.zip/'):
                    zip_filename = path_parts[-1][:-1]  # Remove trailing slash
                elif parsed_path.lower().endswith('.zip'):
                    zip_filename = path_parts[-1]
                else:
                    zip_filename = f"{connector_id}-edp-result.zip"
                    
                logging.info(f"Detected ZIP file upload: {zip_filename}")
            
            # Initialize variables for response
            total_bytes = 0
            is_valid_zip = False
            validation_message = "No data received"
            
            if is_zip_upload and zip_filename:
                # Define file paths
                temp_file_path = os.path.join(DATA_DIR, f"temp_{uuid.uuid4().hex}_{zip_filename}")
                final_file_path = os.path.join(DATA_DIR, zip_filename)
                
                # Create directory if it doesn't exist
                os.makedirs(os.path.dirname(temp_file_path), exist_ok=True)
                
                # Get content length and transfer encoding info
                content_length_str = self.headers.get('Content-Length')
                content_length = int(content_length_str) if content_length_str else None
                transfer_encoding = self.headers.get('Transfer-Encoding', '')
                is_chunked = 'chunked' in transfer_encoding.lower()
                
                logging.info(f"Content-Length: {content_length if content_length else 'Not specified'}")
                logging.info(f"Transfer-Encoding: {transfer_encoding if transfer_encoding else 'Not specified'}")
                logging.info(f"Chunked encoding: {'Yes' if is_chunked else 'No'}")
                
                # STEP 1: Read and save the uploaded data to a temporary file
                try:
                    with open(temp_file_path, 'wb') as output_file:
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
                            chunk_size = min(8192, content_length)  # 8KB chunks or smaller
                            
                            while remaining > 0:
                                read_size = min(remaining, chunk_size)
                                chunk = self.rfile.read(read_size)
                                if not chunk:
                                    logging.warning("Premature end of data")
                                    break
                                    
                                output_file.write(chunk)
                                total_bytes += len(chunk)
                                remaining -= len(chunk)
                                
                                if total_bytes % (1024 * 1024) == 0:  # Log every 1MB
                                    logging.info(f"Read {total_bytes} of {content_length} bytes")
                        else:
                            logging.info("No Content-Length or chunked encoding. Reading until EOF...")
                            chunk_size = 8192  # 8KB chunks
                            
                            while True:
                                chunk = self.rfile.read(chunk_size)
                                if not chunk:
                                    break
                                    
                                output_file.write(chunk)
                                total_bytes += len(chunk)
                                
                                if total_bytes % (1024 * 1024) == 0:  # Log every 1MB
                                    logging.info(f"Read {total_bytes} bytes so far...")
                                    
                    logging.info(f"Successfully read and saved {total_bytes} bytes to {temp_file_path}")
                    
                    # STEP 2: Check if we received any data
                    if total_bytes == 0:
                        validation_message = "File is empty (0 bytes)"
                        logging.error(validation_message)
                    else:
                        # STEP 3: Validate the ZIP file
                        logging.info(f"Validating ZIP file of size {total_bytes} bytes")
                        validation_message = self.validate_zip_file(temp_file_path)
                        is_valid_zip = "Valid ZIP file" in validation_message
                        
                        # STEP 4: Process the file based on validation result
                        if is_valid_zip:
                            try:
                                if os.path.exists(final_file_path):
                                    os.remove(final_file_path)
                                os.rename(temp_file_path, final_file_path)
                                logging.info(f"Moved valid ZIP file to {final_file_path}")
                            except Exception as e:
                                logging.error(f"Error moving file: {str(e)}")
                                validation_message += f" (but failed to move file: {str(e)})"
                        else:
                            logging.info(f"Keeping invalid ZIP file at {temp_file_path} for debugging")
                
                except Exception as e:
                    logging.error(f"Error handling file upload: {str(e)}")
                    validation_message = f"Error processing file: {str(e)}"
                    # Clean up temp file if there was an error
                    if os.path.exists(temp_file_path):
                        try:
                            os.remove(temp_file_path)
                        except:
                            pass
            
            # Send response to client
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()

            response = {
                "state": "SUCCESS" if not is_zip_upload or is_valid_zip else "WARNING",
                "state_detail": f"EDPS connector {connector_id} updated successfully" if not is_zip_upload else validation_message
            }
            response_json = json.dumps(response)
            self.wfile.write(response_json.encode())
            logging.info(f"Sent response: {response_json}")
            
        except (BrokenPipeError, ConnectionResetError, socket.error) as e:
            logging.error(f"Connection error while updating daseen connector: {str(e)}")
        except Exception as e:
            logging.error(f"Error updating connector: {str(e)}")
            try:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(f"Error updating connector: {str(e)}".encode())
            except (BrokenPipeError, ConnectionResetError, socket.error):
                logging.error("Could not send error response - connection already closed")
    
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

    def handle_daseen_delete(self, parsed_path):
        """Handle daseen connector deletion."""
        try:
            # For 204 No Content, we only send the status code and end headers
            self.send_response(204)
            self.end_headers()
            # Don't write any response body for 204
            
        except (BrokenPipeError, ConnectionResetError, socket.error) as e:
            logging.error(f"Connection error while deleting daseen connector: {str(e)}")
        except Exception as e:
            logging.error(f"Error deleting connector: {str(e)}")
            try:
                self.send_response(500)
                self.end_headers()
                self.wfile.write(f"Error deleting connector: {str(e)}".encode())
            except (BrokenPipeError, ConnectionResetError, socket.error):
                logging.error("Could not send error response - connection already closed")


if __name__ == "__main__":
    server_address = ("", PORT)
    httpd = HTTPServer(server_address, CustomHandler)
    httpd.timeout = TIMEOUT    
    logging.info("Serving EDPS mock on http://localhost:%s with timeout of %s seconds", PORT, TIMEOUT)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        logging.info("Server stopped by user.")
    except Exception as e:
        logging.error(f"Server error: {str(e)}")
    finally:
        httpd.server_close()
        logging.info("Server stopped.")

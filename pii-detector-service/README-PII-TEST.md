# PII Detection Test Scripts

This directory contains scripts to test the PII (Personally Identifiable Information) detection capabilities of the Piiranha model against regex-based detection.

## Available Scripts

### 1. run-pii-test.bat

This is a Windows batch script that provides a one-click solution to run the PII detection test.

**Usage:**
- Simply double-click the `run-pii-test.bat` file in Windows Explorer
- Or run it from the command prompt: `run-pii-test.bat`

The script will:
1. Activate the virtual environment
2. Change to the pii-grpc-service directory
3. Run the test with a predefined text containing various PII patterns
4. Return to the original directory and deactivate the virtual environment

### 2. run-pii-test.py

This is a Python script that runs the PII detection test using the subprocess module.

**Usage:**
```
python run-pii-test.py
```

The script will:
1. Define the test text with various PII patterns
2. Construct a command to run the client.test_client module
3. Execute the command and display the results
4. Handle errors and provide fallback instructions if needed

### 3. test-piiranha-script.py

This is a more comprehensive Python script that tries multiple methods to run the PII detection test.

**Usage:**
```
python test-piiranha-script.py
```

The script will try these methods in order:
1. Direct module import
2. Subprocess with full module path
3. Alternative module path
4. Command string construction

## Test Text

The test text includes various patterns of:
- Emails (standard, with underscores, with double dots, etc.)
- Swiss phone numbers (different formats)
- AVS numbers (Swiss social security numbers)
- Other personal information (addresses, credit card numbers, etc.)

## Requirements

Before running these scripts, make sure:
1. The virtual environment is set up (`.venv` directory exists)
2. The gRPC service is installed and configured
3. The Hugging Face API key is set in the environment variables
4. The gRPC server is running on localhost:50051

## Troubleshooting

If you encounter issues:

1. **Server not running:**
   - Start the server first: `python pii-grpc-service/server.py`

2. **Module not found:**
   - Make sure you're in the correct directory
   - Check that the virtual environment is activated
   - Verify that all dependencies are installed

3. **API key issues:**
   - Set the Hugging Face API key: `set HUGGING_FACE_API_KEY=your_key_here`

4. **Manual execution:**
   - If all else fails, you can run the command manually:
   ```
   python -m client.test_client --host localhost --port 50051 --threshold 0.7 --text "your test text here"
   ```

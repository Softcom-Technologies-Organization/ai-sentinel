#!/usr/bin/env python
"""
Script to test PII detection using the Piiranha model.

This script runs a test against the PII detection gRPC service with a predefined text
that contains various patterns of emails, phone numbers, AVS numbers, and other personal information.
"""

import sys
import os
import subprocess
from pathlib import Path

# Add the project root to the Python path
sys.path.insert(0, str(Path(__file__).parent.parent.absolute()))

def main():
    """Run the PII detection test with predefined text."""
    # Test text containing various patterns of PII
    test_text = """Bonjour, Je m'appelle Jean Dupont et je travaille pour l'État de Vaud. Voici mes coordonnées: Emails: jean.dupont@gmail.com (email standard détecté par regex) jean_dupont123@outlook.com (email avec underscore et chiffres) jean.dupont@vd.ch (email @vd.ch exclu par la regex) jean.dupont@subdomain.example.co.uk (email avec sous-domaine et TLD plus long) jean..dupont@example.com (email avec double point - invalide mais pourrait tromper la regex) jean@localhost (email sans TLD - techniquement valide mais souvent non détecté) Numéros de téléphone suisses: 021 123 45 67 (format standard détecté par regex) +41 21 123 45 67 (format avec préfixe international +41) 0041 21 123 45 67 (format avec préfixe international 0041) 0211234567 (format sans espaces) +41211234567 (format international sans espaces) 021-123-45-67 (format avec tirets) +41 (0)21 123 45 67 (format avec zéro entre parenthèses - non détecté par regex) Numéros AVS: 756.1234.5678.90 (format standard détecté par regex) 756.1234.5678.9 (format incorrect - un chiffre en moins) 7561234567890 (format sans points) 756-1234-5678-90 (format avec tirets) 756 1234 5678 90 (format avec espaces) Autres informations personnelles (pour tester Piiranha): Adresse: Rue du Lac 15, 1000 Lausanne Date de naissance: 15.06.1985 Numéro de carte de crédit: 4111 1111 1111 1111 Numéro de passeport: X1234567 Mot de passe: MonMotDePasse123!"""
    
    # Default parameters
    host = "localhost"
    port = 50051
    threshold = 0.7
    
    print("Running PII detection test...")
    print(f"Host: {host}")
    print(f"Port: {port}")
    print(f"Threshold: {threshold}")
    print(f"Text length: {len(test_text)} characters")
    
    try:
        # Method 1: Import and use the client module directly
        try:
            from pii_detector.client.test_client import run_test
            print("\nRunning test using direct module import...")
            run_test(host, port, test_text, threshold)
        except ImportError as e:
            print(f"Could not import client module directly: {e}")
            
            # Method 2: Run as a subprocess
            print("\nRunning test using subprocess...")
            cmd = [
                sys.executable,
                "-m",
                "pii-grpc-service.client.test_client",
                "--host", host,
                "--port", str(port),
                "--threshold", str(threshold),
                "--text", test_text
            ]
            
            try:
                subprocess.run(cmd, check=True)
            except subprocess.CalledProcessError as e:
                print(f"Error running subprocess: {e}")
                
                # Method 3: Try with a different module path
                print("\nTrying alternative module path...")
                cmd = [
                    sys.executable,
                    "-m",
                    "client.test_client",
                    "--host", host,
                    "--port", str(port),
                    "--threshold", str(threshold),
                    "--text", test_text
                ]
                
                try:
                    # Change to the pii-grpc-service directory
                    os.chdir(Path(__file__).parent / "pii_detector")
                    subprocess.run(cmd, check=True)
                except (subprocess.CalledProcessError, FileNotFoundError) as e:
                    print(f"Error running with alternative path: {e}")
                    
                    # Method 4: Last resort - construct the command as a string
                    print("\nFalling back to direct command construction...")
                    cmd_str = f'python -m client.test_client --host {host} --port {port} --threshold {threshold} --text "{test_text}"'
                    print(f"Command: {cmd_str}")
                    print("Please copy and paste this command in your terminal while in the pii-grpc-service directory.")
    
    except Exception as e:
        print(f"Error: {e}")
        print("\nIf all methods fail, you can manually run the following command:")
        print(f'python -m client.test_client --host {host} --port {port} --threshold {threshold} --text "{test_text}"')

if __name__ == "__main__":
    main()

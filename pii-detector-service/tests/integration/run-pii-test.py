#!/usr/bin/env python
"""
Script to test PII detection using the Piiranha model.

This script runs a test against the PII detection gRPC service with a predefined text
that contains various patterns of emails, phone numbers, AVS numbers, and other personal information.
"""

import subprocess
import sys
import os
from pathlib import Path

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

    # Construct the command
    cmd = [
        sys.executable,  # Use the current Python interpreter
        "-m",
        "client.test_client",
        "--host", host,
        "--port", str(port),
        "--threshold", str(threshold),
        "--text", test_text
    ]

    # Print the command for reference
    print("\nExecuting command:")
    print(" ".join(cmd))

    # Get the path to the pii-grpc-service directory
    # We need to change to this directory because the client.test_client module
    # is located there and Python's module resolution is relative to the current directory
    pii_service_dir = Path(__file__).parent / "pii_detector"

    # Store the current directory to return to it later
    original_dir = os.getcwd()

    try:
        # Change to the pii-grpc-service directory
        print(f"\nChanging directory to: {pii_service_dir}")
        os.chdir(pii_service_dir)

        try:
            # Run the command
            subprocess.run(cmd, check=True)
            print("\nTest completed successfully.")
        except subprocess.CalledProcessError as e:
            print(f"\nError running test: {e}")
            print("\nYou can manually run the test with this command:")
            print(f'python -m client.test_client --host {host} --port {port} --threshold {threshold} --text "{test_text}"')
        except FileNotFoundError:
            print("\nError: Could not find the client.test_client module.")
            print("Make sure the module is in your Python path.")
            print("\nYou can manually run the test with this command:")
            print(f'python -m client.test_client --host {host} --port {port} --threshold {threshold} --text "{test_text}"')
        finally:
            # Change back to the original directory
            os.chdir(original_dir)
    except Exception as e:
        print(f"\nError: {e}")
        print("Could not change to the pii-grpc-service directory.")
        print(f"Make sure the directory exists at: {pii_service_dir}")

if __name__ == "__main__":
    main()

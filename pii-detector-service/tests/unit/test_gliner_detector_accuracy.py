from gliner import GLiNER
from pathlib import Path
from collections import Counter

# Load the BI-ENCODER model (supports unlimited labels!)
bi_encoder_model = GLiNER.from_pretrained("gretelai/gretel-gliner-bi-large-v1.0")

file_path = (Path(__file__).parent / ".." / "resources" / "raw_text_confluence.txt").resolve()
text = file_path.read_text(encoding="utf-8")

# CONSOLIDATED: 44 GLiNER labels from data.sql across 7 categories
# Down from 107 types / 13 categories
labels = [
    # IDENTITY (9 types) - Core personal identity
    "person name",
    "national identity number",
    "social security number",
    "passport number",
    "driver license number",
    "date of birth",
    "gender",
    "nationality",
    "age",
    # CONTACT (4 types) - Contact information
    "email address",
    "phone number",
    "address",
    "postal code",
    # DIGITAL (3 types) - Online identifiers
    "username",
    "account id",
    "url",
    # FINANCIAL (6 types) - Money/banking
    "credit card number",
    "bank account number",
    "iban",
    "swift code",
    "tax identification number",
    "salary amount",
    # MEDICAL (6 types) - Health info
    "avs number",
    "patient id",
    "medical record number",
    "health insurance number",
    "medical diagnosis",
    "medication name",
    # IT_CREDENTIALS (9 types) - Technical/secrets
    "ip address",
    "mac address",
    "hostname",
    "device id",
    "password",
    "api key",
    "access token",
    "secret key",
    "session id",
    # LEGAL_ASSET (7 types) - Legal + property
    "case number",
    "license number",
    "criminal record",
    "vehicle registration number",
    "license plate number",
    "vehicle identification number",
    "insurance policy number",
]


if __name__ == "__main__":
    print(f"\n{'='*70}")
    print("GLiNER BI-ENCODER PII Detection Test - NO CHUNKING")
    print("Model: gretelai/gretel-gliner-bi-large-v1.0")
    print(f"{'='*70}")
    print(f"Text length: {len(text)} characters")
    print(f"Labels: {len(labels)} types (CONSOLIDATED from 107)")
    print(f"Categories: 7 (down from 13)")
    print(f"{'='*70}\n")

    # Single pass - no chunking, full text at once
    entities = bi_encoder_model.predict_entities(text, labels, threshold=0.2)

    print(f"Found {len(entities)} PII entities:\n")
    for entity in entities:
        print(f"  [{entity['start']:4d}-{entity['end']:4d}] {entity['label']}: '{entity['text']}' (confidence: {entity['score']:.2f})")

    print(f"\n{'='*70}")
    print("Summary by type:")
    print(f"{'='*70}")
    type_counts = Counter(e['label'] for e in entities)
    for label, count in sorted(type_counts.items(), key=lambda x: -x[1]):
        print(f"  {label}: {count}")

    print(f"\n{'='*70}")
    print(f"TOTAL: {len(entities)} entities with {len(labels)} labels - SINGLE PASS, NO CHUNKING")
    print(f"{'='*70}")

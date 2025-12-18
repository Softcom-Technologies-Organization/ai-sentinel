import pytest
from gliner import GLiNER
from pathlib import Path

# Load the model (downloads automatically on first use)
know_model = GLiNER.from_pretrained("nvidia/gliner-pii")
urchade_model = GLiNER.from_pretrained("urchade/gliner_multi_pii-v1")
file_path = (Path(__file__).parent / ".." / "resources" / "raw_text_confluence.txt").resolve()
text = file_path.read_text(encoding="utf-8")
labels = [
    "name", "first name", "last name"
    "email address", "username",
    "password", "api key", "token", "jwt",
    "url",
    "database string", "jdbc url", "database url",
    "iban number","bank account number","avs number",
    "access key id", "secret access key","db connection string","postgresql string"
]

entities = know_model.predict_entities(text, labels, threshold=0.2)
print("\n")
for entity in entities:
    print(f"{entity['text']} => {entity['label']} (confidence: {entity['score']:.2f})")
print("\n\n")
#entities = urchade_model.predict_entities(text, labels, threshold=0.2,multi_label=True)
#for entity in entities:
#    print(f"{entity['text']} => {entity['label']} (confidence: {entity['score']:.2f})")

# GLiNER isn't limited to PII - you can detect any entities
#text = "The MacBook Pro with M2 chip costs $1,999 at the Apple Store in Manhattan."
#custom_labels = ["product", "processor", "price", "store", "location"]
#print("\n\n")
#entities = know_model.predict_entities(text, custom_labels, threshold=0.3)
#for entity in entities:
#    print(f"{entity['text']} => {entity['label']} (confidence: {entity['score']:.2f})")

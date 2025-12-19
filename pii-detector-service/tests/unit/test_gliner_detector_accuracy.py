import pytest
from gliner import GLiNER
from pathlib import Path

# Load the model (downloads automatically on first use)
know_model = GLiNER.from_pretrained("nvidia/gliner-pii")
file_path = (Path(__file__).parent / ".." / "resources" / "raw_text_confluence.txt").resolve()
text = file_path.read_text(encoding="utf-8")

# Natural language labels - deduplicated
labels = [
    "street",
    "last name",
    "tax number",
    "phone number",
    "username",
    "zip code",
    "full name",
    "url",
    "ip address",
    "mac address",
    "crypto wallet",
    "routing number",
    "cvv",
    "passport number",
    "name medical professional",
    "healthcare number",
    "name",
    "age",
    "gender",
    "marital status",
    "location address",
    "location street",
    "location city",
    "account number",
    "building number",
    "city",
    "credit card number",
    "date of birth",
    "driver license number",
    "email address",
    "first name",
    "ID card number",
    "password",
    "social security number",
    "location state",
    "location country",
    "condition",
    "medical process",
    "drug",
    "dose",
    "blood type",
    "injury",
    "organization medical facility",
    "medical code",
    "money",
    "api key",
    "token",
    "jwt",
    "access key id",
    "secret access key",
    "database connection string",
    "database string",
    "avs number",
    "iban number"
]


print(f"\nUsing {len(labels)} labels (natural language only, no uppercase)\n")

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

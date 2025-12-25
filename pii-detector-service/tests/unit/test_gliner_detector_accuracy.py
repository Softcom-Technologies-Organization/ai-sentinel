from gliner import GLiNER
from pathlib import Path
from collections import Counter

# Load the model (downloads automatically on first use)
know_model = GLiNER.from_pretrained("nvidia/gliner-pii")
file_path = (Path(__file__).parent / ".." / "resources" / "raw_text_confluence.txt").resolve()
text = file_path.read_text(encoding="utf-8")

# GLiNER labels from data.sql (detector_label values)
# These are natural language phrases that GLiNER understands
labels = [
    # CONTACT_CHANNEL
    "email address",
    "phone number",
    # PERSON_IDENTITY
    "person name",
    "username",
    # PERSON_DEMOGRAPHICS
    "date of birth",
    "age",
    "gender",
    # FINANCIAL_IDENTIFIER
    "credit card number",
    "bank account number",
    "iban",
    "routing number",
    "tax identification number",
    "cryptocurrency wallet address",
    # GOVERNMENT_IDENTIFIER
    "social security number",
    "passport number",
    "driver license number",
    "national id number",
    # GEO_LOCATION
    "street address",
    "city",
    "state",
    "country",
    "postal code",
    # CREDENTIAL_SECRET
    "password",
    "api key",
    "access token",
    "secret key",
    "database connection string",
    # STRUCTURED_TECH_IDENTIFIER
    "ip address",
    "avs number",
    "mac address",
    "url",
    # HEALTHCARE
    "medical record number",
    "health insurance number",
    "medical condition",
    "medication",
]

# GLiNER model has internal token limit of 378 tokens
# With ~3-4 chars/token ratio, safe chunk size is ~1000 characters
# Using 1000 chars with 100 char overlap for context continuity
CHUNK_SIZE = 1000
OVERLAP = 100


def chunk_text(text: str, chunk_size: int = CHUNK_SIZE, overlap: int = OVERLAP) -> list[tuple[str, int]]:
    """
    Split text into overlapping chunks for GLiNER processing.

    Args:
        text: The full text to chunk
        chunk_size: Maximum characters per chunk (default 1000, safe for 378 token limit)
        overlap: Character overlap between chunks for context continuity

    Returns:
        List of tuples (chunk_text, start_offset) for position tracking
    """
    chunks = []
    start = 0
    text_len = len(text)

    while start < text_len:
        end = min(start + chunk_size, text_len)
        chunk = text[start:end]
        chunks.append((chunk, start))

        # Move to next chunk with overlap
        start = end - overlap if end < text_len else text_len

    return chunks


def predict_with_chunking(model, text: str, labels: list[str], threshold: float = 0.2) -> list[dict]:
    """
    Run GLiNER prediction with proper chunking for long texts.

    Args:
        model: GLiNER model instance
        text: Full text to analyze
        labels: List of entity labels to detect
        threshold: Confidence threshold for detection

    Returns:
        List of entities with adjusted positions for the full text
    """
    chunks = chunk_text(text)
    all_entities = []
    seen_entities = set()  # Deduplicate entities from overlapping regions

    for chunk_content, chunk_offset in chunks:
        entities = model.predict_entities(chunk_content, labels, threshold=threshold)

        for entity in entities:
            # Adjust positions to full text coordinates
            adjusted_start = entity['start'] + chunk_offset
            adjusted_end = entity['end'] + chunk_offset

            # Create unique key for deduplication
            entity_key = (entity['text'], entity['label'], adjusted_start, adjusted_end)

            if entity_key not in seen_entities:
                seen_entities.add(entity_key)
                all_entities.append({
                    'text': entity['text'],
                    'label': entity['label'],
                    'score': entity['score'],
                    'start': adjusted_start,
                    'end': adjusted_end,
                })

    # Sort by position
    all_entities.sort(key=lambda x: x['start'])
    return all_entities


if __name__ == "__main__":
    # Run detection with chunking
    print(f"\n{'='*60}")
    print("GLiNER PII Detection Test")
    print(f"{'='*60}")
    print(f"Text length: {len(text)} characters")
    print(f"Chunk size: {CHUNK_SIZE} chars (safe for 378 token limit)")
    print(f"Overlap: {OVERLAP} chars")
    print(f"Labels: {len(labels)} types")
    print(f"{'='*60}\n")

    entities = predict_with_chunking(know_model, text, labels, threshold=0.2)

    print(f"Found {len(entities)} PII entities:\n")
    for entity in entities:
        print(f"  [{entity['start']:4d}-{entity['end']:4d}] {entity['label']}: '{entity['text']}' (confidence: {entity['score']:.2f})")

    print(f"\n{'='*60}")
    print("Summary by type:")
    print(f"{'='*60}")
    type_counts = Counter(e['label'] for e in entities)
    for label, count in sorted(type_counts.items(), key=lambda x: -x[1]):
        print(f"  {label}: {count}")

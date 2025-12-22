import pytest
from gliner import GLiNER
from pathlib import Path

# Load the model (downloads automatically on first use)
know_model = GLiNER.from_pretrained("nvidia/gliner-pii")
file_path = (Path(__file__).parent / ".." / "resources" / "raw_text_confluence.txt").resolve()
text = file_path.read_text(encoding="utf-8")

# Natural language labels - deduplicated
labels = [
    "iban",
    "ssn"
]


print(f"\nUsing {len(labels)} labels (natural language only, no uppercase)\n")
first_text = text[:500]
second_text = text[500:1000]
third_text = text[1000:1500]
fourth_text = text[1500:2000]
#fifth_text = text[2000:2500]

entities = know_model.predict_entities(first_text, labels, threshold=0.2)
print("\n")
for entity in entities:
    print(f"{entity['text']} => {entity['label']} (confidence: {entity['score']:.2f})")
print("\n\n")

entities = know_model.predict_entities(second_text, labels, threshold=0.2)
print("\n")
for entity in entities:
    print(f"{entity['text']} => {entity['label']} (confidence: {entity['score']:.2f})")
print("\n\n")

entities = know_model.predict_entities(third_text, labels, threshold=0.2,multi_label=True)
print("\n")
for entity in entities:
    print(f"{entity['text']} => {entity['label']} (confidence: {entity['score']:.2f})")
print("\n\n")

entities = know_model.predict_entities(fourth_text, labels, threshold=0.2,multi_label=True)
print("\n")
for entity in entities:
    print(f"{entity['text']} => {entity['label']} (confidence: {entity['score']:.2f})")
print("\n\n")
# entities = know_model.predict_entities(fifth_text, labels, threshold=0.2)
# print("\n")
# for entity in entities:
#     print(f"{entity['text']} => {entity['label']} (confidence: {entity['score']:.2f})")
# print("\n\n")
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

# import transformers
# import torch
# import time
# model_id = "meta-llama/Meta-Llama-3.1-8B-Instruct"
#
# pipeline = transformers.pipeline(
#     "text-generation",
#     model=model_id,
#     model_kwargs={"torch_dtype": torch.bfloat16},
#     device_map="auto",
# )
#
# messages = [
#     {"role": "system", "content": "You are an expert at findings PII (Personally identifiable Information) in text. output must be a key value object with PII_CATEGORY: [PII]"},
#     {"role": "user", "content": """Find the following  PII  [USERNAME,FIRST_NAME,LAST_NAME,EMAIL,API_KEY,DB_CONN_STRING, AVS_NUMBER, IBAN] in the following text:\n Procédure de déploiement – Environnement préprod
#
#      Objectif
#
#          Ce document décrit le processus de déploiement de l'application "DataBridge" sur l'environnement préproduction (PREPROD2-VD). Il est à usage interne uniquement.
#
#     Informations d'accès Confluence et outils
#
#      Utilisateur système : j.doe (Responsable DevOps)
# Email référent : jean.dupont@example.com
#
# Compte de service API : svc-deploy@databridge.local
#
# Accès VPN nécessaire (voir section sécurité)
#
# Accès Confluence : https://intra.vd.ch/wiki
#
# Variables d'environnement (à injecter dans le docker-compose.override.yml)
# wide760DB_USER: admin_vd
# DB_PASS: P@ssw0rd!2024
# CONFLUENCE_TOKEN: ATATT3xFfGF0y7EXAMPLE
# POSTGRES_URL: jdbc:postgresql://db-internal.vd.ch:5432/databridge
#
# 🔒 Note : ces variables sont injectées dynamiquement par Infisical en environnement sécurisé. Ne pas versionner ce fichier.
#
# Étapes de déploiement
#
# Se connecter au bastion via SSH :
#
# wide760ssh -i ~/.ssh/id_ed25519 j.doe@bastion.vd.ch
#
# Récupérer les derniers artefacts sur Nexus
#
# Lancer le script de mise à jour :
#
# wide760./deploy.sh --env preprod2
#
# Clés et identifiants (ne pas diffuser)
#
# Clé API OpenAI pour module résumé : sk-test-Y9yJW2TfkjYxEXAMPLE
#
# AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
#
# AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
#
# Journal de test (extrait du 2024-11-04)
#
# Le service "PDF Extractor" remonte une erreur 403 lors du traitement des documents confidentiels. Possible cause : token JWT expiré ou rôle manquant.
#
# Ancien token utilisé : eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0...
#
# Suivi utilisateur
#
# Jean Dupont (DPO) a validé le processus. Numéro AVS référent : 756.9217.0769.85 RIB à usage interne : CH93 0076 2011 6238 5295 7
#
# Annexe – Extrait page Confluence
#
# La base de données contient les identifiants initiaux suivants (DO NOT USE in prod)
#
# Utilisateur
#
# Mot de passe
#
# root
#
# changeme123!
#
# support
#
# VdSupport2023*"""},
# ]
# start_time = time.time()
#
# outputs = pipeline(
#     messages,
#     max_new_tokens=256,
# )
# elapsed_time = time.time() - start_time
# print("Elapsed time:", elapsed_time, "seconds")
# print(outputs[0]["generated_text"][-1])
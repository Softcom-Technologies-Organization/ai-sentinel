# Help - Démarrage rapide et dépannage

Ce document complète le README avec des instructions pratiques pour démarrer et diagnostiquer les problèmes courants.

## 1. Prérequis
- Java 24
- Python 3.10+ (microservice PII)
- grpcurl (recommandé pour tester le service gRPC)

## 2. Démarrage
1) Lancer le microservice Python de détection PII
- Le serveur doit écouter sur localhost:50051 en plaintext.
- Vérifier l’accessibilité du service:
  - `grpcurl -plaintext localhost:50051 list` → doit lister `pii_detection.PIIDetectionService`.

2) Lancer l’application Java
- `./mvnw clean package`
- `java -jar target/ai-sentinel-0.0.1-SNAPSHOT.jar`
- Swagger: http://localhost:8080/ai-sentinel/swagger-ui.html

## 3. Configuration essentielle
- Confluence via variables d’environnement (voir README).
- Client gRPC (application.yml):
```yaml
pii-detector:
  client: armeria   # armeria|grpc
  host: localhost
  port: 50051
  default-threshold: 0.5
```
- Si l’hôte/port doivent changer, modifiez `application.yml` (ou utilisez un profil Spring si applicable).

## 4. Point critique: Port gRPC 50051
Seul le serveur Python PII doit écouter le port 50051. Si un autre processus occupe ce port, les appels depuis Java échoueront.

- Symptômes typiques côté Java:
  - `UNIMPLEMENTED: unknown service pii_detection.PIIDetectionService`
  - `UNAVAILABLE` ou timeouts

- Vérifications (Windows PowerShell):
  - Ping du port: `Test-NetConnection -ComputerName localhost -Port 50051`
  - Qui écoute: `netstat -ano | findstr :50051` puis `tasklist /FI "PID eq <PID>"`
  - Service gRPC attendu: `grpcurl -plaintext localhost:50051 list` → doit afficher `pii_detection.PIIDetectionService`

## 5. Tests rapides d’API
- POST /ai-sentinel/api/pii-test/analyze
- POST /ai-sentinel/api/pii-test/analyze-page

Exemple PowerShell:
```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/ai-sentinel/api/pii-test/analyze" `
  -ContentType "application/json" `
  -Body '{"content":"Email: john@example.com","threshold":0.5}'
```

## 6. Dépannage
- Unknown service / UNIMPLEMENTED
  - Presque toujours dû à un mauvais service sur 50051. Fermez le process fautif et relancez le serveur Python.
- UNAVAILABLE / deadline exceeded
  - Service non démarré, pare-feu, ou port bloqué.
- Toujours valider via `grpcurl -plaintext localhost:50051 list`.

Docs utiles:
- `docs/junie-tasks-history/grpc-pii-service-diagnostic-task.md`
- `docs/junie-tasks-history/real-pii-detection-integration-test-task.md`
- `docs/junie-tasks-history/pii-grpc-proto-and-reflection-analysis.md`

## 7. Portée du projet (rappel)
Le scope ne couvre plus RAG/IA ni la vectorisation (pgvector/Qdrant). Seuls l’analyse Confluence et la détection PII via gRPC sont supportés.


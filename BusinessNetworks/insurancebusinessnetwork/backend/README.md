# Insurance Business Network API

Backend Spring Boot pour le frontend React et Corda 4.12.5.

## Prérequis
- JDK 17
- Gradle installé ou wrapper Gradle ajouté au projet

## Configuration RPC
Définir les variables d'environnement :
CORDA_RPC_HOST
CORDA_RPC_PORT
CORDA_RPC_USERNAME
CORDA_RPC_PASSWORD

Ne pas publier les identifiants RPC.
Le compte RPC doit disposer uniquement des permissions nécessaires.

## Démarrage
./gradlew bootRun

## Test
GET http://localhost:8080/api/health

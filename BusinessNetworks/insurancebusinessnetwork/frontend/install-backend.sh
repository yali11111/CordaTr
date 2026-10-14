#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="insurancebusinessnetwork"
BACKEND="$ROOT/backend"

if [[ ! -d "$ROOT/workflows" || ! -d "$ROOT/contracts" ]]; then
  echo "Erreur : lance ce script depuis le dossier parent de insurancebusinessnetwork."
  exit 1
fi

if [[ -e "$BACKEND" ]]; then
  echo "Erreur : $BACKEND existe déjà. Aucun fichier écrasé."
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Java est requis. Utilise un JDK 17."
  exit 1
fi

if ! java -version 2>&1 | grep -q '"17\.'; then
  echo "Attention : vérifie que Java 17 est utilisé."
fi

mkdir -p "$BACKEND/src/main/kotlin/net/corda/samples/insurance/api"

cat > "$BACKEND/settings.gradle.kts" <<'EOF'
rootProject.name = "insurance-api"
EOF

cat > "$BACKEND/build.gradle.kts" <<'EOF'
plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.spring") version "1.9.23"
}

group = "net.corda.samples"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://download.corda.net/maven/corda-releases")
    maven("https://download.corda.net/maven/corda-dependencies")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Client RPC de Corda 4.12.5
    implementation("net.corda:corda-rpc:4.12.5")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.test {
    useJUnitPlatform()
}
EOF

PKG="$BACKEND/src/main/kotlin/net/corda/samples/insurance/api"

cat > "$PKG/InsuranceApiApplication.kt" <<'EOF'
package net.corda.samples.insurance.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class InsuranceApiApplication

fun main(args: Array<String>) {
    runApplication<InsuranceApiApplication>(*args)
}
EOF

cat > "$PKG/HealthController.kt" <<'EOF'
package net.corda.samples.insurance.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HealthController {

    @GetMapping("/health")
    fun health() = mapOf(
        "status" to "UP",
        "application" to "Insurance Business Network API",
        "cordaVersion" to "4.12.5"
    )
}
EOF

cat > "$BACKEND/src/main/resources.tmp" <<'EOF'
EOF
rm "$BACKEND/src/main/resources.tmp"
mkdir -p "$BACKEND/src/main/resources"

cat > "$BACKEND/src/main/resources/application.properties" <<'EOF'
server.port=8080

# Adresse RPC du nœud Insurance en environnement local
corda.rpc.host=${CORDA_RPC_HOST:localhost}
corda.rpc.port=${CORDA_RPC_PORT:10009}
corda.rpc.username=${CORDA_RPC_USERNAME}
corda.rpc.password=${CORDA_RPC_PASSWORD}

# Autoriser le frontend Vite local uniquement en développement
app.cors.allowed-origin=${FRONTEND_ORIGIN:http://localhost:5173}
EOF

cat > "$BACKEND/.gitignore" <<'EOF'
.gradle/
build/
out/
.env
EOF

cat > "$BACKEND/README.md" <<'EOF'
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
EOF

echo
echo "Structure du backend créée dans $BACKEND"
echo "Aucun workflow Corda existant n'a été modifié."
echo
echo "Prochaine étape : ajouter un wrapper Gradle puis configurer"
echo "la connexion RPC et les endpoints métier après vérification"
echo "des états et des flows."

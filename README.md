# MS-AUTH — Microservicio de Autenticación

Microservicio desarrollado con **Spring Boot 3.4.5** encargado de gestionar las credenciales y autenticación de los usuarios del sistema hospitalario. Almacena contraseñas encriptadas con **BCrypt** en su propia **PostgreSQL** y emite **tokens JWT firmados con RSA**. Además expone **JWKS** en `/.well-known/jwks.json` para que otros servicios validen los tokens sin conocer la clave privada.

---

## Tecnologías

| Tecnología | Versión | Uso |
|---|---|---|
| Spring Boot | 3.4.5 | Framework principal |
| Java (Eclipse Temurin) | 21 (LTS) | Lenguaje y runtime |
| Gradle (Kotlin DSL) | 8.7 | Gestión de dependencias y build |
| Spring Security | gestionada por Boot 3.4.5 | Configuración de seguridad y BCrypt |
| Spring Data JPA | gestionada por Boot 3.4.5 | Acceso a datos con Hibernate |
| PostgreSQL JDBC Driver | gestionada por Boot 3.4.5 | Conector a NeonDB |
| NeonDB | PostgreSQL 17 serverless | Base de datos de credenciales |
| Flyway | 10.20.x (gestionada por Boot 3.4.5) | Migraciones de esquema |
| jjwt | 0.12.5 | Generación y validación de JWT |
| Spring Validation | gestionada por Boot 3.4.5 | Validación de DTOs |
| Lombok | gestionada por Boot 3.4.5 | Reducción de boilerplate |
| spring-dotenv | 4.0.0 | Carga automática del `.env` |
| Docker | 26+ | Contenedorización y despliegue |

---

## Por qué Java 21 y no Java 25

Durante el desarrollo se intentó usar **Java 25**, pero generó incompatibilidades concretas con el stack elegido:

1. **Flyway 10.x — classloading conflict**: Flyway 10.x usa `ServiceLoader` para registrar sus plugins internos. Con Java 25 y el module system, aparece un `IncompatibleClassChangeError` al arrancar: `NullFlywayTelemetryManager` no puede implementar `FlywayTelemetryManager` porque la JVM carga dos versiones incompatibles de la misma clase desde diferentes módulos del classpath. Esto no ocurre con Java 21.

2. **Lombok**: Spring Boot 3.2.x gestiona Lombok 1.18.30, que no tiene soporte para Java 25 como compilador y falla durante la compilación con errores de procesamiento de anotaciones. Fue necesario forzar Lombok 1.18.38 como workaround, añadiendo una dependencia fuera del BOM de Spring Boot.

3. **Soporte oficial**: Spring Boot 3.4.x tiene soporte oficial para Java 21 (LTS) y Java 17. Java 25 no forma parte del conjunto de versiones validadas por Spring en esta rama.

---

## Build System — `build.gradle.kts`

### Por qué Kotlin DSL en lugar de Groovy

Gradle admite dos lenguajes para sus scripts de build: **Groovy** (`.gradle`) y **Kotlin** (`.kts`). Este proyecto usa Kotlin DSL por razones concretas:

| Aspecto | Groovy (`.gradle`) | Kotlin DSL (`.kts`) |
|---|---|---|
| Tipado | Dinámico — los errores aparecen al ejecutar | Estático — los errores se detectan al compilar |
| Autocompletado en IDE | Limitado | Completo (el IDE conoce los tipos) |
| Navegación de código | Difícil | Ctrl+Click funciona en cualquier símbolo |
| Consistencia del proyecto | Mezcla Java + Groovy | Todo el proyecto en un solo lenguaje (Java/Kotlin) |

En la práctica: si escribes mal el nombre de una dependencia o una tarea en Groovy, te enteras cuando corres el build. En Kotlin DSL te enteras en el momento que lo escribís, con el error subrayado en rojo en el IDE.

---

### Las partes más importantes del archivo

**1. `plugins {}` — el corazón del build**

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.4"
}
```

- `java` — habilita la compilación de Java en Gradle.
- `org.springframework.boot` — agrega la tarea `bootJar`, que es la que el `Dockerfile` invoca (`gradle bootJar`). Esta tarea produce un **fat JAR** autoejectable con Tomcat embebido adentro. Sin este plugin, el proyecto compila pero no se puede correr con `java -jar`.
- `io.spring.dependency-management` — importa el BOM (Bill of Materials) de Spring Boot. Esto significa que para la mayoría de las dependencias de Spring **no necesitamos especificar versión**, el BOM las gestiona y garantiza compatibilidad entre ellas.

---

**2. `java {}` — versión del compilador**

```kotlin
java {
    toolchain {
        sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    }
}
```

Le dice a Gradle que use el JDK 25 para compilar y generar bytecode. El Java Toolchain API (disponible desde Gradle 7+) es la forma moderna de especificar la versión: en lugar de `sourceCompatibility`/`targetCompatibility`, Gradle localiza o descarga automáticamente el JDK correcto. Esto debe coincidir con la imagen `eclipse-temurin:21-jre` del `Dockerfile`.

---

**3. `configurations {}` — Lombok como annotation processor**

```kotlin
configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}
```

Lombok funciona en tiempo de compilación: lee las anotaciones (`@Getter`, `@Builder`, etc.) y genera código Java antes de que el compilador procese las clases. Este bloque hace que la dependencia declarada en `annotationProcessor` también esté disponible en `compileOnly`, evitando que Lombok termine empaquetado dentro del `.jar` final (no hace falta en runtime, ya cumplió su trabajo).

---

**4. `dependencies {}` — scopes de dependencias**

Los scopes controlan cuándo y dónde está disponible cada dependencia:

| Scope | Cuándo se usa en este proyecto | Ejemplo |
|---|---|---|
| `implementation` | Disponible en compilación y en el JAR final | Spring Boot starters, jjwt-api, Flyway |
| `runtimeOnly` | Solo necesaria en runtime, no al compilar | Driver PostgreSQL, jjwt-impl, jjwt-jackson |
| `compileOnly` | Solo al compilar, no va al JAR | Lombok (genera código y desaparece) |
| `annotationProcessor` | Procesador de anotaciones en tiempo de compilación | Lombok |
| `developmentOnly` | Solo en dev local, excluida del JAR de producción | Spring DevTools (live reload) |
| `testImplementation` | Solo disponible en tests | spring-boot-starter-test, spring-security-test |

El caso más importante aquí es el del **driver de PostgreSQL**: está en `runtimeOnly` porque el código fuente nunca importa clases de `org.postgresql` directamente — Spring Data JPA abstrae todo eso. El driver solo se necesita cuando la aplicación realmente arranca y abre una conexión a NeonDB.

De igual forma, **jjwt** se divide en tres artefactos: la API (`jjwt-api`) va en `implementation` porque el código importa sus interfaces, pero la implementación (`jjwt-impl`) y el serializador Jackson (`jjwt-jackson`) van en `runtimeOnly` porque son detalles internos que jjwt resuelve por reflexión en runtime.

---

**5. `tasks.withType<Test>` — runner de tests**

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
}
```

Le indica a Gradle que use JUnit 5 (JUnit Platform) para correr los tests. Spring Boot 3.x usa JUnit 5 por defecto; sin esta línea, Gradle intentaría usar JUnit 4 y los tests no se ejecutarían.

---

## Patrones de Diseño

### 1. Repository Pattern

**Archivo:** `repository/CredentialRepository.java`

El patrón Repository actúa como una capa intermedia entre la lógica de negocio y la base de datos. Toda la comunicación con NeonDB pasa exclusivamente por el repositorio, y el resto del código nunca escribe queries directamente.

```
Controller → AuthClient → CredentialRepository → NeonDB
                                 ↑
                nadie más sabe que existe PostgreSQL aquí
```

**Por qué lo usamos:**

- Desacopla completamente la lógica de autenticación del motor de base de datos. Si migramos de NeonDB a otro proveedor, solo se toca el repositorio.
- Permite expresar las consultas como métodos descriptivos (`findByEmail`, `existsByEmail`) en lugar de queries SQL embebidas en el código.
- Hace que la capa de negocio sea independiente y testeable sin necesidad de una base de datos real.

```java
// En lugar de SQL directo (acoplamiento):
"SELECT * FROM user_credentials WHERE email = ?"

// Con Repository Pattern:
credentialRepository.findByEmail(email);
```

---

### 2. Facade Pattern

**Archivo:** `client/AuthClient.java`

El patrón Facade proporciona una interfaz simplificada que oculta la complejidad de coordinar múltiples subsistemas. En este microservicio, autenticar a un usuario implica verificar la contraseña con BCrypt, consultar la base de datos y generar un JWT firmado. El Controller no necesita saber nada de esto.

```
Controller llama un solo método:
    authClient.login(dto)

Por dentro el Facade coordina:
    1. Busca las credenciales en DB via CredentialRepository
    2. Verifica si el usuario está activo
    3. Valida la contraseña con BCrypt (PasswordEncoder)
    4. Genera el token JWT con JwtUtil (email + role + userId)
    5. Retorna AuthResponseDTO con el token
```

**Por qué lo usamos:**

- El Controller se mantiene limpio: solo recibe la petición HTTP y delega. No contiene lógica de negocio.
- Centraliza la lógica de autenticación en un único lugar. Si cambia el algoritmo de hashing o el proveedor de JWT, solo se modifica `AuthClient`.
- Mejora la legibilidad: el flujo complejo de autenticación queda expresado en pasos claros dentro del Facade.

```java
// El Controller solo ve esto:
return ResponseEntity.ok(authClient.login(dto));

// La complejidad (BCrypt + JWT + DB) está encapsulada dentro del Facade
```

---

## Estructura del Proyecto

```
src/main/java/com/hospital/msauth/
├── MsAuthApplication.java
├── config/
│   └── SecurityConfig.java           ← Spring Security stateless + BCrypt bean
├── controller/
│   └── AuthController.java           ← recibe HTTP, delega al Facade
├── dto/
│   ├── request/
│   │   ├── RegisterCredentialRequestDTO.java  ← recibido desde MS-USER
│   │   └── LoginRequestDTO.java               ← recibido desde el cliente
│   └── response/
│       ├── AuthResponseDTO.java               ← devuelve el JWT
│       └── TokenValidationResponseDTO.java    ← resultado de validar token
├── entity/
│   ├── UserCredential.java           ← tabla user_credentials en NeonDB
│   └── enums/
│       └── UserRole.java
├── repository/
│   └── CredentialRepository.java     ← Patron Repository
├── client/
│   └── AuthClient.java               ← Patron Facade (capa de negocio)
├── util/
│   └── JwtUtil.java                  ← genera y valida tokens JWT
└── exception/
    ├── GlobalExceptionHandler.java
    ├── InvalidCredentialsException.java
    └── CredentialAlreadyExistsException.java
```

---

## Endpoints

| Método | Ruta | Llamado por | Descripción |
|---|---|---|---|
| `POST` | `/api/auth/register` | MS-USER (interno) | Guarda credenciales encriptadas |
| `POST` | `/api/auth/login` | Frontend / App | Valida credenciales y devuelve JWT |
| `GET` | `/api/auth/validate?token=` | Otros microservicios | Valida un JWT y retorna sus claims |

---

## Flujo de Autenticación

### Registro (llamado internamente por MS-USER)
```
MS-USER  →  POST /api/auth/register  →  AuthController
                                              ↓
                                        AuthClient (Facade)
                                           ├── BCrypt.encode(password)
                                           └── Guarda en NeonDB  →  tabla: user_credentials
```

### Login (llamado por el cliente)
```
Frontend  →  POST /api/auth/login  →  AuthController
                                           ↓
                                     AuthClient (Facade)
                                        ├── Busca credenciales en DB
                                        ├── BCrypt.matches(password, hash)
                                        └── JwtUtil.generateToken(email, role, userId)
                                                ↓
                                     ← { token, userId, email, role }
```

### Validación de token (llamado por otros microservicios)
```
Microservicio  →  GET /api/auth/validate?token=xxx  →  AuthClient
                                                             ↓
                                                       JwtUtil.extractClaims()
                                                             ↓
                                                  ← { valid, email, role, userId }
```

---

## JWT — Estructura del Token

El token contiene los siguientes claims:

```json
{
  "sub": "usuario@hospital.com",
  "role": "DOCTOR",
  "userId": "uuid-del-usuario-en-ms-user",
  "iat": 1714900000,
  "exp": 1714986400
}
```

La expiración por defecto es **24 horas** (`JWT_EXPIRATION=86400000` ms).

---

## Docker

### Por qué usamos Docker

Este microservicio corre en un ecosistema de múltiples servicios (MS-AUTH, MS-USER, etc.), y cada uno tiene sus propias dependencias de runtime. Sin Docker, cada desarrollador necesita instalar manualmente la versión correcta de Java, configurar variables de entorno y asegurarse de que el entorno local coincida con el de producción.

Docker resuelve los tres problemas centrales que tendríamos en este proyecto:

**1. Reproducibilidad total del entorno**
El `Dockerfile` especifica exactamente `eclipse-temurin:21-jdk` para compilar y `eclipse-temurin:21-jre` para correr. Sin importar si el host tiene Java 11, 21 o ninguno instalado, el contenedor siempre construye y ejecuta el mismo binario de la misma forma.

**2. Aislamiento entre microservicios**
MS-AUTH corre en su propio contenedor con su propio JRE, puertos y variables de entorno. No comparte classpath ni configuración con MS-USER ni con ningún otro servicio. Esto refleja directamente el principio de independencia de microservicios: cada uno es dueño de su proceso.

**3. Deploy consistente entre ambientes**
La imagen que se construye localmente es exactamente la misma que va a staging o producción. No hay sorpresas de "en mi máquina funciona" porque el artefacto desplegado es siempre el mismo contenedor.

---

### Estructura del Dockerfile (multi-stage build)

```dockerfile
# --- Etapa 1: Build ---
FROM eclipse-temurin:21-jdk AS builder   # imagen con Gradle + JDK 25
WORKDIR /app
COPY . .
RUN gradle bootJar -x test --no-daemon   # compila y empaqueta el .jar

# --- Etapa 2: Runtime ---
FROM eclipse-temurin:21-jre            # imagen ligera, solo JRE (sin herramientas de build)
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Usamos **multi-stage build** deliberadamente: la primera etapa compila usando la imagen completa de Gradle (pesada), pero la imagen final solo contiene el JRE mínimo necesario para ejecutar el `.jar`. Esto reduce el tamaño de la imagen final y elimina herramientas de compilación que no deben estar en producción.

| Stage | Imagen base | Propósito |
|---|---|---|
| builder | `eclipse-temurin:21-jdk` | Compilar el proyecto con Gradle + JDK 25 |
| runtime | `eclipse-temurin:21-jre` | Ejecutar el `.jar` con el JRE mínimo necesario |

---

### Correr con Docker

```bash
# Construir la imagen
docker build -t ms-auth .

# Correr el contenedor pasando las variables de entorno
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://hostneondb/neondb?sslmode=require \
  -e DB_USERNAME=neondb_owner \
  -e DB_PASSWORD= **** \
  -e JWT_SECRET= ***** base64 \
  -e JWT_EXPIRATION=86400000 \
  ms-auth
```

> El archivo `.env` también puede montarse como volumen o pasarse con `--env-file .env` en lugar de declarar cada variable individualmente.

### Docker Compose local con volúmenes propios

Para desarrollo local, `docker-compose.yml` usa volúmenes del proyecto en `./volumes/`:

- `./volumes/postgresql/data` guarda los datos de PostgreSQL.
- `./volumes/ms-auth/keys/private_key.pem` y `./volumes/ms-auth/keys/public_key.pem` guardan las llaves RSA que usa MS-AUTH.

Esto evita depender de archivos sueltos en la raíz del repositorio y mantiene todo el estado local del servicio en una sola carpeta.

---

## Configuración

### Local

Crear el archivo `.env` basado en `.env.example`:

```env
DB_URL=jdbc:postgresql://Host-neondb/neondb?sslmode=require&channel_binding=require
DB_USERNAME=neondb_owner
DB_PASSWORD=******
JWT_SECRET= ******* base64_"minimo_32_caracteres"
JWT_EXPIRATION=86400000
```

```bash
./gradlew bootRun
```

El servicio inicia en el puerto **8080**.

---

## Kubernetes

Despliegue mínimo para correr `ms-auth` en Kubernetes local:

- `k8s/namespace.yaml`: crea el namespace `ms-auth`.
- `k8s/postgresql-pvc.yaml`: reserva `1Gi` para PostgreSQL.
- `k8s/postgresql.yaml`: despliega PostgreSQL y su `Service` interno.
- `k8s/ms-auth-secrets.yaml`: monta las llaves RSA como archivos en `/keys`.
- `k8s/ms-auth.yaml`: despliega `ms-auth` y su `Service` interno.

Aplicación:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/postgresql-pvc.yaml
kubectl apply -f k8s/postgresql.yaml
kubectl apply -f k8s/ms-auth-secrets.yaml
kubectl apply -f k8s/ms-auth.yaml
```

Prueba local:

```bash
kubectl get pods -n ms-auth
kubectl port-forward svc/ms-auth 8080:8080 -n ms-auth
```

### Railway (producción)

El servicio está desplegado en Railway y es accesible públicamente en:

**`https://ms-auth-production-38c7.up.railway.app`**

Railway corre el servicio usando el `Dockerfile` del repositorio. La base de datos PostgreSQL corre también en Railway en la misma red interna, por lo que MS-AUTH la alcanza sin exponerla a internet.

Variables de entorno configuradas en Railway:

| Variable | Descripción |
|----------|-------------|
| `DB_URL` | `jdbc:postgresql://postgres.railway.internal:5432/msauth_db` — apunta a PostgreSQL en la red interna de Railway |
| `DB_USER` | Usuario de la base de datos |
| `DB_PASS` | Contraseña de la base de datos |
| `JWT_SECRET` | Clave base64 usada para firmar y verificar los tokens JWT |
| `JWT_EXPIRATION` | `86400000` — tiempo de expiración del token en milisegundos (24 horas) |
| `SERVER_PORT` | `8080` — puerto en el que escucha Spring Boot |

---

## Base de Datos

- **Proveedor:** NeonDB (PostgreSQL serverless) — base de datos **independiente** de MS-USER
- **Tabla principal:** `user_credentials`
- **DDL:** generado automáticamente por Hibernate (`ddl-auto: update`)

> Cada microservicio es dueño de su propia base de datos. MS-AUTH nunca accede a la DB de MS-USER y viceversa. La comunicación entre ellos ocurre únicamente a través de HTTP (REST).

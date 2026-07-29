# Quickstart: Validate OryxOS Web Info Hello

## Prerequisites

- JDK 21
- Maven 3.9+
- Run commands from the repository root

The repository-local tools can be used on this workstation:

```powershell
$env:JAVA_HOME = (Resolve-Path ".tools/jdk-dist/jdk-21.0.12+8").Path
$env:Path = "$env:JAVA_HOME/bin;$((Resolve-Path '.tools/maven-dist/apache-maven-3.9.16/bin').Path);$env:Path"
```

## Automated verification

Run the affected module and its dependencies:

```powershell
mvn -pl oryxos-web -am test
```

Package the complete nine-module reactor:

```powershell
mvn clean package
```

Expected result: both commands finish with `BUILD SUCCESS`.

## End-to-end verification

Start the executable application:

```powershell
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT.jar
```

In a second terminal:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/info
```

Expected response values:

```text
name    = OryxOS
stage   = project-initialization
message = Hello from OryxOS
```

The authoritative HTTP shape is defined in [contracts/openapi.yaml](contracts/openapi.yaml).

# Actor-Scan

Extracts class → method → actor relationships from Java bytecode.

An **actor** is any entity that receives method calls within a method body:
- `FIELD` - Instance/static field (`this.cache.get()`)
- `PARAM` - Method parameter (`request.getBody()`)
- `LOCAL` - Local variable (`response.setStatus()`)
- `STATIC` - Static method call (`Utils.helper()`)
- `NEW` - Newly created object (`new HashMap().put()`)

## Usage

```bash
java -jar target/state-scan-1.0.0-SNAPSHOT.jar /path/to/project
```

## Output

```
com.example.UserService:
  getUser:
    FIELD cache java.util.Map#get
    PARAM userId java.lang.String#isEmpty
  saveUser:
    FIELD repository com.example.UserRepository#save
    NEW HashMap java.util.HashMap#<init>
```

## Options

```
-p, --packages    Package prefixes to include (comma-separated)
--maven           Scan as Maven multi-module project
--show-empty      Show methods with no actors
--public-only     Show only public methods
```

## Building

```bash
mvn clean package -DskipTests
```

## Architecture

```
model/
  ActorType       # FIELD, PARAM, LOCAL, STATIC, NEW
  Actor           # type, name, typeFqn, methodsCalled
  MethodInfo      # name, descriptor, actors
  ClassInfo       # fqn, methods, fields
  ScanResult      # all scanned classes

bytecode/
  ActorTrackingVisitor  # Stack-tracking MethodVisitor
  ClassScanner          # ClassVisitor → ClassInfo
  ProjectScanner        # File/JAR traversal
  DescriptorParser      # JVM descriptor parsing
```

## v1 Archive

The original detector-based implementation (risk levels, path finding, DI resolution) is preserved in `archive/v1/`.

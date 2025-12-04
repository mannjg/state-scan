# Actor-Scan (v2)

## Purpose
Extracts class → method → actor relationships from Java bytecode.

## Actor Types
- `FIELD` - Field receiving method calls
- `PARAM` - Parameter receiving method calls
- `LOCAL` - Local variable receiving method calls
- `STATIC` - Static method calls on a class
- `NEW` - Newly created object

## Output Format
```
<Class FQN>:
  <method name>:
    <ActorType> <name> <TypeFQN>#<methodCalled>
```

## Key Files
- `ActorTrackingVisitor.java` - Core stack-tracking MethodVisitor
- `ClassScanner.java` - ClassVisitor building ClassInfo
- `ProjectScanner.java` - File/JAR traversal
- `ConsoleOutput.java` - Output formatting

## v1 Archive
Original detector-based implementation in `archive/v1/` (path finding, risk levels, DI resolution).

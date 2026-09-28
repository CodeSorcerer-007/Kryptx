# ADR 001: Manual Dependency Injection over Reflection-Heavy DI Frameworks

## Status
Accepted

## Context
Standard Android architecture commonly employs dependency injection (DI) frameworks such as Dagger, Hilt, or Koin. While these tools provide automated graph generation and lifecycle binding, they introduce significant trade-offs for high-security applications:
1. **Reflection & Code Generation Overhead**: Hilt/Dagger rely heavily on annotation processors (`kapt`/`ksp`), generating extensive bytecode wrappers that expand the binary attack surface and complicate runtime code auditing.
2. **Hidden Execution Paths**: Reflection-based DI can obfuscate object lifecycle boundaries and memory retention semantics, making it harder to verify when sensitive cryptographic components are instantiated, scoped, and dereferenced.
3. **Build Determinism & Binary Reproducibility**: Complex code generation plugins increase build variability and potential supply chain vectors.
4. **App Startup Latency**: Initialization of large dependency graphs adds noticeable startup delay, degrading the responsive, weightless feel required for instant biometric unlock.

## Decision
Kryptx adopts explicit manual dependency injection centered on `KryptxDependencies`:
- Dependencies are composed in a strictly ordered, strongly-typed manual container at application startup (`KryptxApplication`).
- Cryptographic engines, database helpers, session managers, and security watchdogs are injected directly via constructor arguments.
- Fake and mock implementations for unit testing are supplied through standard Kotlin interfaces without framework scaffolding.

## Consequences
### Positive
- **Complete Transparency**: Every component dependency is 100% visible, traceable in a single call hierarchy, and auditable during security reviews.
- **Deterministic Lifecycles**: Key material holders and session managers are cleanly controlled; there is zero risk of reflection retaining expired cryptographic instances in hidden singletons.
- **Fast Startup**: Zero reflection or DI initialization overhead during cold start.
- **Reduced Attack Surface**: No external DI library dependencies in the runtime classpath.

### Negative
- **Manual Wiring**: Introducing new shared services requires explicit wiring through `KryptxDependencies` or view model factories.
- **Boilerplate**: ViewModel providers require custom `ViewModelProvider.Factory` implementations.

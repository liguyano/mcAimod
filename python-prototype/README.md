# Kurumi embedded Python proof of concept

This is a **separate, opt-in GraalPy JVM prototype**. It does **not** run the
main Kurumi agent or execute AI-provided scripts. The original pseudocode
interpreter, scripts, and NeoForge packaging remain unchanged.

## Run

From the repository root (Java 25 and Gradle available):

```powershell
cd python-prototype
gradle run
```

Expected: a mock `goto` invocation and `Python interpreter works`.
Tools are mocks, **not** Minecraft calls.

## Next integration gates

1. Verify dependency resolution and Java 25 compatibility on the real client.
2. Build a bounded main-thread dispatcher + asynchronous completion bridge
   (avoid blocking the Minecraft tick thread).
3. Establish a hardened execution budget and shutdown policy for untrusted code.
4. Map tool failures to Python values, preserve task cancellation and owner gates.
5. Package dependencies correctly for NeoForge or choose an isolated process.
6. Only then enable an opt-in Python engine and migrate skills incrementally.

GraalPy is not a standalone security boundary for arbitrary LLM output.

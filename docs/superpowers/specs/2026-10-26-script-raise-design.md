# Restricted Script Exceptions

The owner approved `raise Exception(message)` for Kurumi's restricted Python-like scripts.

- Accept a quoted string or an existing variable/property path (for example `result.message`) as the single argument. Reject unsupported exception names, imports, nested calls and argument lists.
- Run entirely within the existing parser and executor, never arbitrary Python.
- Immediately stop the script, preserving its observation report and adding the source line and exception reason. Mark the stage as failed so the existing next AI turn can diagnose and choose whether to retry, change method, or quit. No automatic retry.
- Apply equally inside user-defined functions, `if` and loops. Do not change current `observe()` semantics in this narrow exception feature; non-blocking observation is a distinct planned change.

Approach: add `RAISE` to the existing script AST and interpreter. Alternative: implement as a tool call (would obscure source line and failure semantics); a real Java/Python exception bridge is intentionally excluded.

# Script nesting depth

Allow up to 16 nested script blocks in Kurumi's Java-based interpreter, preserving the existing 200-statement budget and other validation. Both the indentation parser and JSON program validation must accept the same depth and reject deeper blocks. No Python runtime is introduced.

Approaches considered: remove the limit (unbounded recursion risk), increase two literal limits (easy to drift), or share a named limit (chosen for consistent validation).

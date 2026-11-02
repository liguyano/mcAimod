# Java Agent Framework — Phase 1 Design

Kurumi remains a Java/NeoForge autonomous agent using the existing bounded Java pseudocode executor. While idle it may observe, converse, follow its owner, eat and flee; resource gathering, building and changing storage require an owner-supplied goal. These policies remain enforced by existing game controls in phase 1; this phase does not change action authorization.

Phase 1 isolates prompt/context assembly and tool discovery from the controller. A task-scoped catalog sends signatures from only relevant categories, while retaining safe universal tools and an explicit help/discovery route. Unknown requests fall back to the full catalog rather than deprive the agent of capabilities. Context compaction preserves system rules, the original user goal, task state, and most recent feedback, with a smaller ordinary-message budget; older items can be summarized as omitted counts rather than silently pretending they were retained. Record model request elapsed time, input/output character estimates, and retry attempt without exposing keys or raw private content.

Approaches: (1) only lower the current maximum (fast but still sends irrelevant tools); (2) route tools and compact task-focused context (chosen); (3) replace controller/executor at once (high regression risk, deferred).

No API credentials, tool semantics, Minecraft actions, model selection, or emergency interruption behavior are changed in phase 1.

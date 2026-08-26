# Privacy and security

Caddie can observe UI content, send selected context to a model service, and act
in other apps. A safe deployment depends on both the Android app and the
services you configure.

## Data flow

During a normal run, model requests can contain the task, recent conversation,
screen-derived descriptions, retrieved guidance, tool definitions, and tool
results. That material leaves the phone when the model endpoint is remote.
Additional MCP tools can send their declared inputs to their own endpoints.

The bundled embedding model runs on the phone. This does not make the overall
system offline: generative inference remains external.

## Local storage

Caddie uses app-private Room databases for runtime recovery, context, and study
state. Runtime, context, study, MCP-setting, and study-export paths are excluded
from Android backup and device transfer by the shipped backup rules. Context
encryption uses a non-exportable Android Keystore key on the encrypted
persistence path. Uninstalling the app normally removes app-private data and its
key; exported files outside app-private storage require separate deletion.

## Safer operation

- Prefer an endpoint you control and HTTPS with authenticated access.
- Review the endpoint operator's logging and retention policy.
- Use a dedicated test device or profile and disposable accounts first.
- Enable only the permissions and MCP servers you need.
- Keep the phone visible and be prepared to pause or reject an action.
- Do not paste secrets into tasks or screenshots.
- Never expose the study portal or an MCP endpoint directly to an untrusted
  network.
- Rotate a token if it appears in logs, screenshots, issue reports, or commits.

Normal-mode confirmations are a safety layer, not a correctness guarantee.
Models can misunderstand tasks, target the wrong app, or incorrectly report
completion. Caddie is not suitable for unattended financial, medical,
emergency, authentication, or irreversible workflows.

This repository does not operate a hosted service. Data handling by a configured
model or MCP endpoint remains the responsibility of that endpoint's operator.

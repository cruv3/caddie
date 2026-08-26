# Model backend contract

Caddie owns the agent state and tools on Android. A separately operated service
generates model output. The service is not bundled with this repository.

## Connection settings

Caddie needs an absolute HTTP(S) base URL, the model identifier sent in each
request, and optionally a bearer token. Prefer HTTPS. Plain HTTP exposes task
context and screen-derived information to the network and may be rejected by
Android's network security policy. Never commit a real endpoint, token, or
private network address.

Use placeholders in shared notes and examples:

```text
Base URL: https://gateway.example
Model ID: example-tool-model
Bearer token: <key-if-required>
```

## Required endpoints

The configured base URL is used for:

- `GET /v1/models` for the reachability indicator; and
- `POST /v1/chat/completions` for generation.

The reachability check requires a successful HTTP response from `/v1/models`;
it does not validate model quality or parse the catalog. The configured model
identifier must be accepted by the chat-completions endpoint.

## Request

Caddie sends JSON containing `model`, `stream: true`, role-tagged `messages`,
OpenAI-style function tools with JSON-object parameter schemas, and—for selected
Qwen configurations—`chat_template_kwargs.enable_thinking`. Assistant history
can contain a `tool_calls` item; tool results include its `tool_call_id`.

Synthetic example:

```json
{
  "model": "example-tool-model",
  "stream": true,
  "messages": [
    {"role": "user", "content": "Open Display settings."}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "android.example_action",
        "description": "Synthetic documentation example.",
        "parameters": {"type": "object", "properties": {}}
      }
    }
  ]
}
```

The example tool name is deliberately not a claim about the runtime catalog.
Caddie supplies its actual definitions at request time. A compatible model must
reliably produce structured function calls when Android actions are required;
text-only chat compatibility is not enough.

## Streaming response

The response must have content type `text/event-stream`. Each server-sent event
uses one or more `data:` lines containing an OpenAI-style chat-completion chunk.
Caddie consumes:

- `choices[0].delta.content` for assistant text;
- `choices[0].delta.reasoning_content` when supplied; and
- streamed `choices[0].delta.tool_calls` fragments with `index`, `id`, function
  `name`, and JSON-object `arguments`.

Finish the stream with:

```text
data: [DONE]

```

The stream is rejected if it ends without `[DONE]`, a completed tool call lacks
an ID or name, or its arguments do not assemble into one JSON object. Caddie can
retry only before streamed data has been accepted, which avoids silently
duplicating a partially generated action.

## Authentication and logging

If configured, Caddie sends `Authorization: Bearer <token>` on model and health
requests. The saved token is encrypted with an Android Keystore-backed key.
Backend operators should apply their own access control, rate limits, retention
policy, and redaction. Do not log prompts, tool results, or authorization
headers by default. See [Privacy and security](privacy-security.md).

## Compatibility check

Before attempting UI automation, verify that the service:

1. returns a successful response at `/v1/models`;
2. returns `text/event-stream`;
3. streams a short text completion ending in `[DONE]`; and
4. preserves streamed function-call IDs, names, and argument fragments.

There is no maintained list of compatible products. Compatibility depends on
the protocol and the selected model's tool-use behavior, not a brand name.

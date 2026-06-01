# OpenAI Function Calling Announcement

## Metadata

- **Authors / organization**: OpenAI (Atty Eleti, Jeff Harris, Logan Kilpatrick, et al.)
- **Affiliation**: OpenAI
- **Venue + year**: OpenAI Blog, June 13, 2023
- **URL**: https://openai.com/index/function-calling-and-other-api-updates/
- **First read**: <TODO>
- **Relevance to thesis**: **Spec reference.** Establishes the JSON-schema-arg convention Caddie's MCP server inherits. Cite as origin of the now-ubiquitous function-call shape that MCP's tool surface mirrors.

## TL;DR (my words, after reading)

Introduces structured function calling in `gpt-4-0613` and `gpt-3.5-turbo-0613`: developers describe functions via a JSON-Schema-like spec; the model returns a JSON object choosing a function and its arguments. Effectively standardised "tool use" as an API primitive across the industry.

## Direct quotes

> "A new way to more reliably connect GPT's capabilities with external tools and APIs." — blog

## Paraphrases / my notes

- <TODO>

## Key concepts / terms

- **JSON-Schema-described function call** — model returns structured JSON arg object. (source: blog)
- **Industry-standard tool-use shape** — convention later generalised by MCP's `tools/call`. (source: blog)

## How I plan to use this in the thesis

- **Section**: Background (tool-use conventions).
- **Role**: **Spec reference (historical).**
- **Specific claims it supports**:
  - "The JSON-Schema function-calling shape was popularised by OpenAI in June 2023; MCP generalises it. Caddie's tools follow this lineage."

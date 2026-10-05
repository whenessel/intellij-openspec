Captured from the real OpenRouter HTTPS API on 2026-10-05 through the normal environment proxy.

- `models.json`: subset of GET `/api/v1/models`, preserving original entry fields and prices. The catalog currently advertises text output for every returned model. Non-text/unknown modality rejection is tested by mutating captured entries.
- `completion.json`: POST `/api/v1/chat/completions`, `liquid/lfm-2.5-2.6b:free`, synthetic prompt "Reply with exactly OK.", 256 output tokens, nonstreaming, zero-price provider ceiling. HTTP 200, finish `stop`, reported cost 0.
- `completion-length.json`: same synthetic prompt/model with 32 tokens. HTTP 200, finish `length`, null assistant content, reported cost 0.

Capture reads OPENROUTER_API_KEY only inside process memory for the Authorization header; requests and credentials are never saved. Completion ID, timestamp and system fingerprint were removed. No project or personal text was sent. Negative malformed/error cases in tests are robustness inputs, not claimed live captures.

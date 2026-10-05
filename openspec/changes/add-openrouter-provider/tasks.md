## 1. Provider and transport

- [x] 1.1 Add provider, isolated model preference and PasswordSafe support; verify settings/credential regression tests.
- [x] 1.2 Implement completion/catalog codecs and cancellation-safe transport; verify captured response contract and mocked transport tests.

## 2. Settings and workflows

- [x] 2.1 Add asynchronous model refresh, preserve selection and guard provider/key races; verify UI state tests and platform review.
- [x] 2.2 Verify shared REST backend routing with OpenRouter and no Codex/provider fallback through execution tests.

## 3. Acceptance and documentation

- [x] 3.1 Update user documentation and validation evidence; record real zero-cost HTTP smoke separately from UI end-to-end.
- [x] 3.2 Run full test/check/JaCoCo/buildPlugin/verifyPlugin and OpenSpec validation; record results and deliver local ZIP.
- [ ] 3.3 Commit and push only plan/local-codex-provider-architecture; verify published SHA and clean tree.

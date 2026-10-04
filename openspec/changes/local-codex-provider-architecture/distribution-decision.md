# Authentication and distribution decision record — 2026-10-04

Development scope is the user's local, open-source plugin fork (repository LICENSE is Apache-2.0). The installed CLI remains the only owner of login, persistence and refresh. The plugin checks account mode and limits, sends reviewed prompts locally over stdio and never reads/copies auth files, supplies external ChatGPT tokens, starts a new login grant, or provides a hosted subscription proxy.

The current official [app-server authentication documentation](https://learn.chatgpt.com/docs/app-server#authentication) permits continued local/open-source use while recommending Sign in with ChatGPT for clearer user control; it excludes commercial/hosted services from app-server authentication. [CLI authentication documentation](https://learn.chatgpt.com/docs/auth) distinguishes managed ChatGPT use from API-key billing. These constraints support the bounded development scope; they are not a release approval or a promise of unlimited subscription inference.

Commercial/hosted distribution is outside this implementation. Before changing that scope, the owner must review the then-current eligibility rules and separately assess Sign in with ChatGPT registration, supported auth/session lifecycle, billing/privacy UX and distribution requirements. No SIWC implementation, partner registration, commercial rollout or new grant is authorized by this change.

Release remains blocked on actual SDK/IDE/supported-platform acceptance and owner confirmation of the intended distribution. Task 5.7 stays open until that release decision is reviewed; this record supplies the technical/auth assessment without silently granting broader scope.

# Project-wide product assumptions

Confirmed by the product owner on 2026-10-19. These assumptions apply to V1, V2, and future versions unless the product requirements explicitly change. They amend the requirements baseline without authorizing application implementation.

## English-only university context

The system is designed for a university in the United States, assuming teachers, students, and administrators are native English speakers. This is a design assumption, not a requirement to verify nationality or native language, and does not introduce an administrator role or administrator features.

All interfaces, announcements, course instructions, student questions, and AI responses are in English. AI ingestion and retrieval need support only English-language content. Multilingual support, translation, localization, and cross-language retrieval are outside project requirements; do not plan extensibility or acceptance coverage for them unless the requirements explicitly change.

Assistant questions that are not in English receive an English request to ask in English rather than an answer or translation. Names, quotations, and occasional foreign terms in an otherwise English question do not by themselves require rejection. Assistant answers remain English even when asked to switch languages. Non-English course sources are outside the supported content assumption; the previously proposed cross-language answering capability is withdrawn. Do not infer language-verification or ASCII-only validation requirements for ordinary course content or identity fields.

## Architecture and acceptance consequences

Use English interfaces, course content, rules, retrieval fixtures, assistant questions, and expected answers for functional acceptance. The AI quality benchmark contains at least sixty reviewed English questions with the separately agreed correctness, grounding, abstention, and citation thresholds. Test the assistant's English-only response and unsupported-language feedback; multilingual quality and translation tests are not required.

English-only scope does not remove Unicode-safe storage, identity preservation, CSV encoding, escaping, or security testing. Previously recorded test outcomes remain historical evidence rather than claims that this amendment has been implemented or newly verified.

## University timezone

Q59 replaces the former Asia/Taipei requirement project-wide with `America/New_York`, including V1, V2, future versions, deadline entry, display, validation, and related acceptance tests. Use the IANA timezone and its daylight-saving rules, not a fixed UTC offset. Reject both nonexistent spring-forward wall times and ambiguous fall-back wall times, asking the teacher to choose an unambiguous time. This policy was explicitly confirmed by the product owner.

Persist deadlines as instants and compare against authoritative backend/database time for future-deadline and cutoff validation. Existing stored instants remain unchanged and render in the new university timezone. Include winter/summer offsets, both daylight-saving transitions, impossible dates, and device-timezone independence in verification. Historical evidence remains a record of the configuration actually tested at that time.

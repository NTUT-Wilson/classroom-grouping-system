# V2 discovery — confirmed decision record

Status: Interview complete. On 2026-10-19 the owner confirmed the shared understanding at Q79 and authorized final documentation. The consolidated [V2 PRD](v2-PRD.md), [architecture](v2-architecture.md), [user flows](v2-user-flows.md), and [implementation roadmap](v2-implementation-roadmap.md) now govern the V2 plan; this record retains the decisions and superseded proposals, not implementation authorization. V1 documents remain authoritative for V1 except the explicit project-wide amendments.

## Confirmed product decisions

- Q1–Q6: Voluntary departure is available only before the current grouping deadline, without approval. A departing manager is replaced by a randomly selected remaining member without acceptance. If no members remain, the system-created group retains its name and capacity, has no manager, and its pending requests are canceled with a distinct reason; completed history remains. Switching uses departure followed by normal joining, with no destination or old-place guarantee. Member removal is excluded.
- Q7: A manager may deliberately transfer authority immediately to any other current member without acceptance.
- Q8: Pending applications and invitations survive a manager change; the new manager takes responsibility.
- Q9: The new manager can read the group's earlier application and invitation history. Former managers lose management access but retain access to requests involving themselves as applicant or recipient.
- Q10–Q11: Manual application withdrawal and manual invitation cancellation are excluded, superseding the initial V2 proposal. Agreed automatic lifecycle cancellation remains.
- Q12: Immediate return to the former group is allowed through normal joining rules, without a cooldown or automatic manager-role restoration.
- Q13: No separate course-assignment feature. Knowledge sources are announcements, existing activity instructions, and grouping rules.
- Q14–Q18: Announcement creation publishes immediately. Required title and plain-text body; no attachments, comments, or rich formatting. Any assigned course teacher may manage any announcement in that course. Currently enrolled students may read all current announcements, including those predating enrollment; access ends with enrollment. No email, push, or read receipts.
- Q19: Assistant knowledge includes maintained documentation of enforced grouping rules and current activity settings.
- Q20: Assistant answers only; it cannot perform business actions.
- Q21: Student identities, profiles, memberships, applications, invitations, and rosters are excluded from assistant knowledge.
- Q22: Insufficient permitted evidence produces an explicit insufficient-information response rather than an answer from general knowledge.
- Q23: Conflicting teacher content and enforced rules are flagged; explain the enforced rule and suggest asking the teacher.
- Q24: Substantive answers include clickable citations to the relevant announcement, activity instructions, or rule section.
- Q25: Each conversation is scoped to one selected course. Check enrollment on every question and authorization on citation access; never search other courses in that conversation.
- Q26–Q27: New answers exclude superseded and deleted source versions even during indexing lag. Earlier answers remain visible with a source-changed/unavailable indicator; citation access exposes only currently authorized content.
- Q28–Q29: Temporary conversations support follow-ups but clear on leaving/reloading. Assigned teachers may use their course assistant without reading student conversations.
- Q30, amended after Q52 and finalized at Q53–Q55: Assistant accepts English questions only, replacing the earlier bilingual proposal. The project-wide English-only assumption also excludes cross-language source answering and translation.
- Q31–Q32: No paid AI usage. External AI processing is permitted; no provider is selected.
- Q33–Q35: Free-capacity exhaustion shows temporary unavailability and course-source links, without disrupting the core application or using paid fallback. The user imposes no requirement excluding provider training use; selected-provider policy still needs documentation. Per-user, input-length, and course-concurrency limits are permitted; Q56 later specifies the provisional values.
- Q36–Q39: Announcement deletion requires confirmation, immediately removes content from student views and future answers, and has no restore feature. Concurrent stale announcement edits are blocked rather than overwriting newer changes. Retrieved content is not trusted as instructions. The assistant explains rules but directs personal membership questions to normal screens.
- Q40: Planning estimates are five courses, forty students per course, and ten simultaneous assistant users; these are not hard limits.
- Q41–Q44: Announcement title limit 150 characters and body limit 10,000; both required and nonblank. Newest publication first; show published and last-edited times; edits do not reorder. Unresolved contradictions between announcements are cited and flagged rather than resolved by recency alone. Follow-ups retrieve current evidence and do not treat earlier answers as authoritative.
- Q45–Q46: Any observed cross-course disclosure or unauthorized action in agreed security tests blocks acceptance. Use a fixed reviewed benchmark covering answerable, unsupported, conflicting, changed/deleted-source, and malicious-instruction cases; score correctness, grounding, and citations separately.
- Q47: Plan PostgreSQL-backed, course-filtered hybrid keyword and semantic retrieval rather than a separate vector database, subject to capacity validation. The later English-only amendment removes the proposed multilingual retrieval requirement. Model and embedding providers remain undecided.
- Q48–Q49: Source saves remain available during indexing outages; durable retries update indexes later. New unindexed content may be temporarily absent, but obsolete/deleted versions remain excluded. Read current authoritative activity settings for each question instead of trusting embedded copies.
- Q50, amended: At least sixty reviewed English-only benchmark questions. At least 90% fully correct answers on answerable cases, 95% factual-claim citation support, 95% correct abstention on insufficient-evidence cases, and 100% authorized valid citation targets. Report category raw counts; security failures block independently.
- Q51: Provisional warm-application target: 90% of requests answered within fifteen seconds at ten simultaneous users; stop waiting after thirty seconds with retry feedback. Validate against free-provider capacity and measure cold starts separately.
- Q52: Operational logs exclude question text, retrieved content, and generated answers. Permitted metadata includes latency, error category, model version, token counts, and source identifiers.

## Later confirmed decisions and project-wide amendments

- Q74: If no free provider meets security, quality, or performance gates, keep the assistant disabled until the owner approves revised requirements. Lifecycle improvements and announcements may release independently; no silent downgrade or paid fallback.
- Q75: Start AI implementation planning with a synthetic-data comparison of free generation and embedding providers. Owner approval is required before provider selection; the final PRD may leave named providers pending this milestone.
- Q76: Show a concise external-processing notice asking users not to submit sensitive information. Do not promise automatic personal-information detection or redaction.
- Q77: Conversation history lives only in browser memory; no conversation database, browser persistent storage, or application response cache. Follow-ups send limited context to the provider; provider retention is separate.
- Q78: If indexing is incomplete or unavailable, disclose potentially incomplete sources and link to normal course pages. Empty retrieval does not prove the teacher supplied no information.

- Q68: Minimum benchmark comprises forty answerable questions and twenty insufficient-evidence questions. Acceptance requires at least 36/40 fully correct answers, 38/40 successful retrievals, and 19/20 correct abstentions, alongside claim-level grounding and citation/security gates. Security and source-change scenarios receive additional coverage.
- Q69: For multi-source questions, retrieval success requires all necessary sources among the top five results.
- Q70: Humans review benchmark reference answers and final scored results. Automated evaluation may assist but cannot alone determine acceptance.
- Q71: Provisional indexing target is searchable updated content within sixty seconds while application/indexing services are available. Outage recovery is measured separately; stale/deleted sources remain excluded immediately.
- Q72: Provider failures and timeouts count toward short-term request limits. Show retry guidance; never automatically resubmit a question.
- Q73: Preserve one grouping activity per course, fixed participants/capacities, no reopening after cutoff, no teacher membership editing, and no activity reset.

- Q64: AI and indexing failures must not prevent login, announcements, or grouping operations.
- Q65: Operators may rebuild the search index from authoritative content without altering that content; no teacher-facing rebuild control.
- Q66: Retrieval evaluation is separate from answer scoring. The correct supporting source must appear in the top five results for at least 95% of answerable benchmark questions.
- Q67: Teachers cannot access student assistant questions, answers, or conversations, including through administrative reporting. Operational metadata follows the agreed logging policy.

- Q59: Project-wide university timezone is America/New_York, replacing Asia/Taipei. Correct daylight-saving handling, including rejection of both nonexistent and ambiguous input wall times, is confirmed. Preserve stored instants.
- Q60: Announcements and assistant are available without a grouping activity; explicitly state when no activity is configured.
- Q61: Grouping-rule documentation is maintained with application releases and is not teacher-editable.
- Q62–Q63: Final authorization and source-version checks precede complete-answer delivery; withhold an answer if source versions change or access is revoked and ask the user to retry. No partial-answer streaming.

Project-wide amendment confirmed 2026-10-19: [Project assumptions](project-assumptions.md) supersedes the earlier bilingual proposal across all versions. English-only interfaces, source content, questions, answers, and retrieval; no translation, localization, or future multilingual planning. No nationality or native-language verification.

- Q53–Q55: Apply the project-wide English-only policy. Non-English questions receive English unsupported-language feedback; occasional foreign terms/names do not alone invalidate English questions. Cross-language source answering is excluded; responses remain English.
- Q56: Provisional limits: 2,000 characters per question, five questions per minute per user, and two concurrent requests per course; excess requests receive retry feedback. Validate against launch estimates and free-provider quotas.
- Q57: Deliberate manager transfers stop at the grouping deadline; finalized manager roles remain unchanged.
- Q58: Concurrent departure, transfer, and approval serialize with fresh authority checks; stale former-manager commands fail. Preserve unique membership and capacities.

Q79 closes the product interview and confirms shared understanding. The final documents specify ingestion/retrieval, failure/concurrency outcomes, scoring/category sizes, complete flows, and staged acceptance. Provider selection, actual account policies/quotas, compatible dependency/vector configuration, and measured technical tuning remain explicit implementation-validation milestones rather than unresolved product questions.

## Evidence boundary

Existing roadmap records all twelve V1 slices locally completed. Current scheduler code invokes production due work and returns 204, superseding historical placeholder descriptions. Hosted validation and private remote CI remain open. The separately authorized project-wide timezone change did include frontend implementation/tests; [its evidence](timezone-amendment.md) records the actual passes and Docker-blocked integration checks. No other V2 feature or external provider has been implemented/validated during discovery. Final-document production requires content/diff checks only; it does not rerun or replace application evidence. Preserve the existing manual acceptance document except the requested targeted timezone alignment already made.

## Final documentation review — 2026-10-19

- PASS: Content review against all 79 interview decisions; original withdrawal/cancellation and bilingual proposals are explicitly superseded.
- PASS: Local-link/anchor and fence checks across fourteen final/supporting files: 131 local references resolved, all 79 decision numbers recorded, no errors. Seven Mermaid flowcharts received structural node-reference/fence checks.
- PASS: Architecture reviewed against the existing ArchUnit dependency rules; source-owned change records/public reads avoid source-to-Assistant/platform dependencies and cycles. No code or ArchUnit tests were changed in this documentation turn.
- PASS: `git diff --check`, with normal Windows line-ending notices only.
- NOT RUN: Full Mermaid parser/render validation; no parser is installed in this workspace. Structural checks are not a parser/render pass.
- NOT RUN: New application, real-provider, integration, E2E, load, or hosted runs for final documentation. Prior timezone results remain explicitly dated historical evidence for that separate amendment. All V2 implementation slices remain Not Started; provider selection remains an owner approval checkpoint.

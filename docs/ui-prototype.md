# V1 UI prototype

**Status:** Frontend design preview using synthetic in-memory data. This is not business-feature implementation or production authorization. The PRD, user flows, glossary, and approved technical direction remain authoritative. Outstanding Foundation checks remain in `foundation.md` and `hosting-validation.md`.

## Preview

From `frontend/`, run `npm run dev`, then open:

`http://127.0.0.1:5173/?prototype=1`

Use Vite's reported port if 5173 is occupied. The real course workspace at `/` is separate; open the explicit preview URL for comparison. The prototype itself needs no backend, database, credentials, or network API responses.

The amber preview toolbar is outside the product experience:

- **Preview as:** Alex (initially ungrouped), Grace (initially a member), Noah (initially a manager), or Dr. Evelyn Lee (teacher). Switching persona preserves in-memory interactions so you can inspect both parties' request outcomes.
- **Activity state:** changes the Interaction Design fixture between open, finalizing, finalized, and temporarily unavailable. This resets all mock data; it is not a user-facing lifecycle or assignment control. The other course fixtures include finalized and no-activity examples.
- **Scenarios:** simulate a competing empty-group join, an uncertain response, or an unavailable mock CSV download. Refresh reads the current mock state; it never resubmits a command.

The demo clock is 20 October 2026 at 10:00 Asia/Taipei. Open fixtures have a future deadline; closed fixtures have a past deadline. Browser date-input formatting follows the device locale, but the entered wall time is explicitly Asia/Taipei. Reloading the page clears all interactions.

## Suggested review journeys

1. Alex → Interaction Design → join Group 3 → confirm membership → inspect Requests and canceled attempts. The first joining student becomes manager; no leave/switch control exists.
2. Noah → Requests → approve Ethan's application. Group 2 fills; other pending requests for that group close with “Group full.” Switch to Alex to inspect the corresponding private outcome.
3. Alex → Invitations → reject Group 2's invitation. The separate pending application stays pending. Noah can reinvite Alex from Classmates; rejection does not prevent a new attempt.
4. Teacher → Visual Communication → enter a manual capacity list → review → create. Invalid totals are rejected. Creation immediately opens grouping; the saved layout is read-only. Switch to Alex to inspect the newly created empty groups.
5. Teacher → Interaction Design → edit instructions or extend the deadline. Earlier deadlines are rejected. Select Finalizing: instructions remain editable, deadline extension disappears, and the roster view offers read-only refresh without partial results or progress counts.
6. Select Results ready. Students see only their own final group and members; the teacher sees the complete roster and may download a CSV containing synthetic data only. One system-populated group has no manager.
7. After cutoff → Classmates → edit your own About me or read profiles. Other students' final assignments are absent. Requests retain completed/expired history with no response actions.
8. Sign out of the demo to review its sign-in layout. Narrow the browser to review stacked mobile layouts, course tabs, and dialogs.

## Boundaries and checks

Fixtures live in `frontend/src/prototype/data.ts`; mock interaction simulation in `mockActions.ts`; UI/components and styling in `Prototype.tsx` and `prototype.css`. The prototype is loaded separately from the real authentication shell and never imports `src/api.ts`. It neither stores browser data nor writes a database. A successful mock action does not establish backend correctness or access control.

Run `npm test`, `npm run build`, and `npm run test:prototype` in `frontend/`. The prototype Playwright configuration starts a frontend-only server on loopback port 5174; the existing real-backend Foundation smoke configuration remains separate. Browser checks cover role/lifecycle restrictions, cross-persona outcomes, creation validation, no API traffic, failure scenarios, CSV availability, and mobile overflow. Screenshot artifacts are generated in ignored `frontend/test-results/`.

Real API integration, backend business logic, durable state, actual allocation, deployment, and Foundation hosting validation remain outside this prototype task.

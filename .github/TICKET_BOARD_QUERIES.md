# Ticket-board searches

Use these GitHub issue searches:

- Inbox: `is:issue is:open label:"status:needs-triage"`
- Needs decision: `is:issue is:open label:"status:needs-decision"`
- Blocked: `is:issue is:open label:"status:blocked"`
- Ready: `is:issue is:open label:"status:ready"`
- Backlog: `is:issue is:open -label:"status:needs-triage" -label:"status:needs-decision" -label:"status:blocked" -label:"status:ready"`
- Done: `is:issue is:closed`

Draft, ready-for-review, and validated work is derived from linked pull requests and checks as defined in `docs/TICKET_BOARD.md`.

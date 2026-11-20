## Summary

<!-- What changes and why. Link the issue this closes, e.g. "Closes #12". -->

## How was this tested

<!-- Commands you ran, new or updated tests, and any manual checks (e.g. a job submitted against a sample repository). -->

## Checklist

- [ ] I understand which pipeline stage I am changing.
- [ ] I preserved the API contract or intentionally documented the change.
- [ ] I added or updated focused tests.
- [ ] I use repository-relative paths with `/` separators.
- [ ] I preserved timeout and cleanup behavior.
- [ ] I distinguished file-level errors from job-level errors.
- [ ] I ran `./mvnw test`.
- [ ] I ran `./mvnw verify` when appropriate.
- [ ] I ran `./mvnw spotless:apply` so formatting is clean.
- [ ] I updated related documentation.
- [ ] I did not commit `.env`, credentials, generated `target/` files, or secrets.

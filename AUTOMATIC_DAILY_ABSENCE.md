Attendance remains one record per student, internship and date. The submission
deadline is **12:00 noon** in `app.attendance.zone` (`ATTENDANCE_ZONE`, default
`Asia/Kolkata`). Submissions and new liveness sessions close at noon. All existing
liveness checks for submitted attendance remain in place.

The backend runs absence processing at noon, ten seconds after startup and every
five minutes thereafter. It catches up from each completed internship's start
date through its end date or the latest closed submission window, whichever is
earlier. Ended internships are included so downtime does not lose history. Today
is eligible only at/after noon; future days never are. No dashboard visit is needed.

Saturday/Sunday are excluded. Hybrid internships additionally require an existing
hybrid schedule entry for that weekday. Other modes have no weekday calendar in
the current implementation, so their eligible weekdays are Monday through Friday.
Only active student accounts with completed internship registration are processed.

There was no holiday or approved-leave model in the application. Configure known
institutional holidays and approved leave before enabling processing/backfill,
using the existing application's external YAML configuration mechanism:

```yaml
app:
  attendance:
    calendar:
      holidays:
        - 2026-10-20
      approved-leave:
        123: # student user ID; inclusive dates
          - start: 2026-10-21
            end: 2026-10-23
```

These are calendar exclusions, not a new leave approval workflow. Configuration
changes take effect on restart. Absence processing can be disabled with
`ATTENDANCE_ABSENCE_ENABLED=false` while preparing exclusions. The noon submission
deadline remains in force independently of this job switch.

Existing rows, including soft-deleted history, are never updated or regenerated.
New absences use `attendance_result=ABSENT`, `submission_type=AUTO_ABSENT`,
`verification_basis=AUTOMATIC`, and no evidence. Responses include
`automaticAbsent=true` and `livenessStatus=NOT_APPLICABLE`. Existing timestamp
columns store processing time for automatic rows; they do not represent a student
submission. Dashboards/reports label the source and time accordingly.

Migration V31 only extends source constraints and adds an automatic-source
consistency check; it does not change existing records or the daily unique key.
Each daily insert commits separately. Concurrent writers/submissions are resolved
by that database key, and failures leave missing dates eligible for the next retry.
No hourly records or location verification have been introduced.

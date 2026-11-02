# Student academic structure

Current student accounts use Computer Engineering and 8th Semester. Students choose their assigned Section A or B when completing or editing their profile; a missing section starts blank. The API rejects other student branches, semesters and sections.

The Student profile form, profile view and completion indicators use this structure. Mentor and IIIC student directories display and group sections, with A/B and unassigned filters. Weekly and monthly monitoring views provide independent section filters. Shared report catalogs, roster/attendance summaries and monthly exports include section details. Monthly PDFs show the student's section and 8th Semester.

Student imports use the existing Excel workflow with `Name, UID, Email ID, Semester, Section` columns. Semester may be blank to use 8; any supplied semester must be 8. Section must be A or B. Existing UIDs, names, account ownership and session assignment rules continue to apply.

## Migration V52

`backend/src/main/resources/db/migration/V52__standardize_student_academics.sql` changes current academic details only. It adds nullable A/B section columns to accounts and profiles and saves original academic values in `student_academic_normalization_audit` before normalization. Existing valid sections are preserved; missing or invalid sections remain unassigned for review. The migration does not choose a section for a student.

Branches and semesters are normalized to Computer Engineering and 8, with database defaults and constraints. IDs, UIDs, roll numbers, academic years, academic sessions, timestamps, internships, attendance, mentor assignments and submitted report histories are retained.

The old branch-scoped roll constraint is replaced by a nullable section-scoped normalized roll key. Historical collisions retain their original rolls and valid sections and receive `legacy_roll_identity_conflict = true`; they do not stop the migration or force an arbitrary section change. New saves validate section-scoped roll uniqueness and use the corresponding database index.

## Verification

- Full Flyway H2 profile/import verification: 21 tests passed.
- Data-preserving H2 migration fixture: passed, including old branch/semester audit values, preserved A/B sections, missing/invalid assignments, roll collisions and database constraints.
- Disposable local PostgreSQL profile/report verification: 48 tests passed, including section-filtered attendance/roster CSV exports, monthly section fields and mentor authorization.
- Frontend profile, completion, weekly and monthly interaction tests cover required section selection, preserved existing sections, POST/PUT saves, and independent report filtering; TypeScript passed.

Tests use in-memory databases or newly created disposable schemas. They do not insert dummy records into the existing local application schema or production.

# Internship Management System – Pages and Features

This document lists the main pages in the application and the key features available on each page.

---

## 1. Login / Authentication

### `/login`
- User sign-in for students, mentors, and admin users.
- Validates institutional email and password.
- Redirects users based on role and password-change requirement.
- Shows role-based landing behavior:
  - Admin → admin dashboard
  - Mentor → mentor dashboard
  - Student → student dashboard
- Handles login errors with clear validation messages.

---

## 2. Student Pages

### `/student/dashboard`
- Central student workspace and status overview.
- Shows profile completion status for student profile and professional profile.
- Displays internship onboarding progress and workflow status.
- Allows the student to:
  - complete or edit personal profile
  - upload or review professional resume
  - start internship registration if not already secured
  - view internship details once available
  - browse internship opportunities
- Shows today’s attendance summary card.

### `/student/profile`
- Student institutional profile management page.
- Allows students to add or edit academic and personal institutional information.
- Supports view mode and edit mode using a toggle.
- Saves data to the backend and refreshes profile status.

### `/student/professional-profile`
- Professional profile page for career information.
- Allows students to:
  - add/edit headline
  - add/edit career interests
  - upload PDF or DOCX resume
  - view/download resume
  - run AI/analysis on the resume
- Displays resume analysis and skill profile information.
- Includes a direct link to skill-gap intelligence.

### `/student/skill-gaps`
- Skill gap analysis page.
- Compares the student’s profile against current internship opportunities.
- Highlights:
  - skills to strengthen
  - existing strengths
  - related skills
- Displays guidance when profile is incomplete or analysis is unavailable.

### `/student/internship`
- Internship onboarding workflow page.
- Allows students to:
  - mark internship as secured
  - select internship source
  - upload required onboarding documents
  - view uploaded documents
  - confirm receipt of internship diary
- Tracks document completion and workflow status.

### `/student/internship/details`
- Internship details entry and viewing page.
- Available only after diary confirmation/required onboarding steps.
- Allows students to:
  - enter internship details
  - update internship information
  - submit company, mode, stipend, duration, and full-time employment info
  - review saved internship details
- Prevents completion before required workflow steps are done.

### `/student/opportunities`
- Student internship opportunities listing page.
- Features:
  - search by company or opportunity title
  - filter by work mode
  - sort by newest or deadline soon
  - pagination
  - view cards with company name, stipend, deadline, and required skills
- Click-through to opportunity detail page.

### `/student/opportunities/[id]`
- Detailed internship opportunity page.
- Shows:
  - company information
  - opportunity description
  - mode, duration, stipend, and deadline
  - eligibility criteria (branches, semesters, CGPA)
  - required and preferred skills
  - company website link
  - opportunity matching analysis panel

### `/student/change-password`
- Student password change form.
- Lets students set a new password after login when required.
- Redirects back to the student dashboard after successful change.

---

## 3. Mentor Pages

### `/mentor/dashboard`
- Mentor overview page.
- Shows mentor profile details such as:
  - employee ID
  - department
  - designation
- Lists assigned students and their attendance status.
- Displays:
  - company name
  - internship mode
  - today's work mode
  - attendance verification result
- Helps mentors monitor student internship attendance.

### `/mentor/change-password`
- Mentor password reset/change page.
- Required for first-time login or password update flow.
- Redirects to mentor dashboard after successful password change.

---

## 4. Admin Pages

### `/admin/dashboard`
- Admin monitoring dashboard for the III Cell.
- Provides institution-wide internship metrics, including:
  - total students
  - profile completion stats
  - internship secured vs not secured counts
  - document and diary completion metrics
  - internship source distribution
  - company type and internship mode breakdown
  - stipend summary
  - full-time opportunity summary
  - top companies by student count
  - students requiring attention
- Includes quick links to the student directory and filtered attention views.

### `/admin/student-accounts`
- Bulk student account import page.
- Allows admin to:
  - download the Excel template
  - upload an `.xlsx` student list
  - create student accounts in bulk
  - review import summary results
  - identify invalid or duplicate rows

### `/admin/mentor-accounts`
- Mentor account management page.
- Allows admin to:
  - create mentor accounts
  - assign employee ID as temporary password
  - view all mentor accounts
  - manage assigned students for each mentor

### `/admin/students`
- Student directory and search page.
- Features:
  - search by name, UID, roll number, or email
  - filter by branch, academic year, semester, internship status, profile status, source, mode, company type, and more
  - view student summary table
  - pagination
  - access each student detail page
- Helps admins track and filter students by progress state.

### `/admin/students/[id]`
- Detailed student record management page.
- Shows:
  - student profile details
  - professional profile summary
  - internship onboarding records
  - internship details
  - internship location and hybrid schedule management when applicable
  - status badges and student workflow progress

### `/admin/opportunities`
- Opportunity management page for III Cell staff.
- Allows admin to:
  - create new internship opportunities
  - edit existing opportunities
  - publish or close opportunities
  - search and filter opportunities by status, company, and mode
  - view opportunity details in a dialog
  - open candidate talent discovery for each opportunity

### `/admin/opportunities/[id]/talent`
- Talent discovery page for a specific opportunity.
- Provides candidate analysis for students matching or nearly matching the posting.
- Features:
  - search candidate list by name, UID, semester, or skill
  - filter by eligibility level and branch
  - view skill-gap summary for the opportunity
  - review candidate alignment explanation and evidence
  - see required/preferred skill matching details

---

## 5. Shared / System Features Across the App

These are common capabilities used throughout the system:
- Protected routes based on user role.
- Role-based redirects after login.
- Workflow tracking for internship progress.
- Status badges and workflow badges for quick monitoring.
- Form validation and server-side error handling.
- Pagination and filtering for large student/opportunity datasets.
- Resume upload and profile creation flows.
- Internship lifecycle tracking from securing internship to final details submission.

---

## Summary

The system is structured around three primary user roles:
- Student: profile, professional profile, internship onboarding, opportunities, and internship tracking.
- Mentor: student assignment and attendance monitoring.
- Admin: monitoring, student management, mentor account management, and internship opportunity publishing.

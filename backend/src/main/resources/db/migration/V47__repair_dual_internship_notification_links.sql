UPDATE student_notifications
SET action_url = '/admin/second-internship-requests/' || SUBSTRING(event_key FROM 27)
WHERE notification_type = 'SECOND_INTERNSHIP_REQUEST'
  AND event_key LIKE 'second-internship-request:%';

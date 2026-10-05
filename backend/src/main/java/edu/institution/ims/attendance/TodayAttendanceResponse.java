package edu.institution.ims.attendance;

import java.time.LocalDate;

public record TodayAttendanceResponse(LocalDate attendanceDate, AttendanceResponse attendance) {}

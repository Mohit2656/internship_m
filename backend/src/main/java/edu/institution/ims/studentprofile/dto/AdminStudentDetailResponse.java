package edu.institution.ims.studentprofile.dto;

import edu.institution.ims.studentprofile.ProfileStatus;
import edu.institution.ims.internship.dto.OnboardingResponse;
import edu.institution.ims.internship.dto.InternshipDetailsResponse;

public record AdminStudentDetailResponse(Long id, String loginEmail, ProfileStatus profileStatus, StudentProfileResponse profile, String aadhaarNumber,
        OnboardingResponse internshipOnboarding, InternshipDetailsResponse internshipDetails) {}

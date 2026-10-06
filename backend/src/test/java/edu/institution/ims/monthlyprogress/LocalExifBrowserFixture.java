package edu.institution.ims.monthlyprogress;

import edu.institution.ims.ImsApplication;
import edu.institution.ims.user.*;
import edu.institution.ims.mentor.*;
import edu.institution.ims.internship.*;
import org.springframework.boot.SpringApplication;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.math.BigDecimal;
import java.time.*;

/** Opt-in local browser fixture; never pointed at a production host or the public schema. */
public class LocalExifBrowserFixture {
    public static void main(String[] args) {
        String url=System.getenv("SPRING_DATASOURCE_URL"),schema=System.getenv("SPRING_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA");
        if(url==null || !url.matches("jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*") || schema==null || !schema.startsWith("exif_validation_")) throw new IllegalArgumentException("Isolated loopback PostgreSQL schema required");
        var app=SpringApplication.run(ImsApplication.class,args);
        var users=app.getBean(UserRepository.class);var encoder=app.getBean(PasswordEncoder.class);
        String password=encoder.encode("ExifLocalTest!2026");
        User admin=users.save(new User("admin@exif-test.invalid",password,Role.ADMIN,true));
        User mentor=users.save(new User("mentor@exif-test.invalid",password,Role.MENTOR,true));
        User other=users.save(new User("other-mentor@exif-test.invalid",password,Role.MENTOR,true));
        var mentors=app.getBean(MentorProfileRepository.class);
        mentors.save(new MentorProfile(mentor,"EXIF Test Mentor","EXIF-M1","Computer Engineering","Professor","+91 9876543210"));
        mentors.save(new MentorProfile(other,"EXIF Other Mentor","EXIF-M2","Computer Engineering","Professor","+91 9876543211"));
        User student=users.save(new User("student@exif-test.invalid",password,Role.STUDENT,true));
        app.getBean(StudentAccountRepository.class).save(new StudentAccount(student,"EXIF Test Student","EXIF-LOCAL-1",6));
        var assignments=app.getBean(MentorStudentAssignmentRepository.class);assignments.save(new MentorStudentAssignment(mentor,student,admin,Instant.now()));
        var onboardings=app.getBean(InternshipOnboardingRepository.class);var o=new InternshipOnboarding(student);o.markDocumentsComplete();o.confirmDiary();o.markInternshipDetailsComplete();o=onboardings.saveAndFlush(o);
        var details=new InternshipDetails(o);details.apply("EXIF Test Company",CompanyType.IT,null,"Local test address","+91 9876543210","Test Supervisor","Manager","+91 9876543211",12,LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),52,InternshipMode.OFFICE_REPORTING,BigDecimal.ZERO,false);
        app.getBean(InternshipDetailsRepository.class).saveAndFlush(details);
        System.out.println("EXIF_BROWSER_FIXTURES_READY");
    }
}

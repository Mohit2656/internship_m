package edu.institution.ims.monthlyprogress;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="EXIF_TEST_DB_URL", matches="jdbc:postgresql://(localhost|127[.]0[.]0[.]1):.*")
class WorkplaceApprovalMigrationPostgresTest {
 @Test void upgradePreservesLegacyReferenceAttendanceAndPhotoSnapshots() {
  String outer=System.getenv("EXIF_TEST_DB_SCHEMA"), url=System.getenv("EXIF_TEST_DB_URL");
  if(outer==null || !outer.matches("ims_deletion_test_[0-9a-f]{32}") || !url.contains("currentSchema="+outer)) throw new IllegalStateException("An isolated local test schema is required");
  String schema="ims_workplace_migration_"+UUID.randomUUID().toString().replace("-","");
  if(!schema.matches("ims_workplace_migration_[0-9a-f]{32}")) throw new IllegalStateException("Invalid generated schema");
  String username=System.getenv("EXIF_TEST_DB_USERNAME"), password=System.getenv("EXIF_TEST_DB_PASSWORD");
  var admin=new JdbcTemplate(new DriverManagerDataSource(url,username,password));
  admin.execute("create schema "+schema);
  try {
   String isolated=url.replace("currentSchema="+outer,"currentSchema="+schema);
   Flyway.configure().dataSource(isolated,username,password).schemas(schema).target("26").load().migrate();
   var jdbc=new JdbcTemplate(new DriverManagerDataSource(isolated,username,password));
   jdbc.update("insert into users(id,email,password_hash,role,active,created_at,updated_at) values (101,'legacy.student@example.test','test-only','STUDENT',true,now(),now()),(102,'legacy.admin@example.test','test-only','ADMIN',true,now(),now())");
   jdbc.update("insert into internship_onboarding(id,student_user_id,workflow_status,secured_at,created_at,updated_at) values (101,101,'INTERNSHIP_SECURED',now(),now(),now())");
   jdbc.update("insert into internship_details(id,onboarding_id,company_name,company_type,company_address,company_phone,industry_supervisor_name,industry_supervisor_designation,industry_supervisor_contact,duration_months,start_date,end_date,total_weeks,internship_mode,stipend_per_month,full_time_employment_offered,created_at,updated_at) values (101,101,'Legacy branch','IT','Legacy address','1234567890','Supervisor','Manager','1234567890',2,'2026-09-01','2026-10-31',8,'OFFICE_REPORTING',0,false,now(),now())");
   jdbc.update("insert into internship_locations(id,internship_id,latitude,longitude,geofence_radius_meters,status,created_at,updated_at,confirmed_by,confirmed_at) values (101,101,21.125,79.0875,200,'CONFIRMED',now(),now(),102,now())");
   jdbc.update("insert into attendance_records(id,student_user_id,internship_id,attendance_date,attendance_result,verification_status,submission_type,server_submitted_at,created_at,updated_at) values (101,101,101,'2026-09-18','PRESENT','VERIFIED','BASIC',now(),now(),now())");
   jdbc.update("insert into attendance_evidence(attendance_id,expected_latitude,expected_longitude,distance_meters,geofence_radius_meters,camera_capture_used,verification_reason,image_media_type,image_size_bytes,created_at) values (101,21.125,79.0875,10,200,true,'VERIFIED_GEOFENCE','image/jpeg',100,now())");
   jdbc.update("insert into monthly_progress_reports(student_internship_id,report_month_number,period_start,period_end,work_description,photo_storage_key,photo_original_name,photo_mime_type,photo_size,submitted_at,created_at,updated_at,workplace_latitude,workplace_longitude,workplace_verification_status) values (101,1,'2026-09-01','2026-09-30','Legacy monthly work','test-reference-only','legacy.jpg','image/jpeg',100,now(),now(),now(),21.125,79.0875,'WITHIN_RADIUS')");
   Flyway.configure().dataSource(isolated,username,password).schemas(schema).load().migrate();
   assertThat(jdbc.queryForObject("select status from internship_locations",String.class)).isEqualTo("CONFIRMED");
   assertThat(jdbc.queryForObject("select mentor_decision from internship_locations",String.class)).isNull();
   assertThat(jdbc.queryForObject("select latitude from internship_locations",java.math.BigDecimal.class)).isEqualByComparingTo("21.125");
   assertThat(jdbc.queryForObject("select verification_status from attendance_records",String.class)).isEqualTo("VERIFIED");
   assertThat(jdbc.queryForObject("select distance_meters from attendance_evidence",java.math.BigDecimal.class)).isEqualByComparingTo("10");
   assertThat(jdbc.queryForObject("select location_verification_status from attendance_evidence",String.class)).isNull();
   assertThat(jdbc.queryForObject("select location_captured_at from attendance_evidence",java.sql.Timestamp.class)).isNull();
   assertThat(jdbc.queryForObject("select workplace_revision from attendance_evidence",Long.class)).isNull();
   assertThat(jdbc.queryForObject("select count(*) from attendance_location_verification_audit",Integer.class)).isZero();
   assertThat(jdbc.queryForObject("select workplace_verification_status from monthly_progress_reports",String.class)).isEqualTo("WITHIN_RADIUS");
   assertThatThrownBy(() -> jdbc.update("update internship_locations set status='APPROVED'")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  } finally {
   // Only this exact schema, created by this test, is removed. Never public or existing schemas.
   admin.execute("drop schema "+schema+" cascade");
  }
 }
}

package edu.institution.ims.dashboard.report;

import edu.institution.ims.security.UserPrincipal;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static edu.institution.ims.dashboard.report.DashboardReportCatalog.Kind.*;

/** Report types extend the dashboard's authorization, filters and streaming POI/CSV writer. */
@Service
@PreAuthorize("hasAnyRole('ADMIN','MENTOR')")
public class InstitutionalReportService {
    private final DashboardReportRepository repository;
    private final DashboardReportCatalog catalog;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    public InstitutionalReportService(DashboardReportRepository repository, DashboardReportCatalog catalog, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.repository=repository; this.catalog=catalog; this.jdbc=jdbc; this.clock=clock;
    }
    public record Type(String id, String title, String description, boolean available, String historicalSheet, List<DashboardReportCatalog.Field> fields) {}
    public record Options(List<Type> types, List<DashboardReportCatalog.Filter> filters, String scope, String date) {}
    public record Table(List<DashboardReportCatalog.Field> fields, List<Map<String,Object>> rows, long totalRows, int page, Map<String,Object> summary, String title) {}
    private static DashboardReportCatalog.Field field(String id,String label,DashboardReportCatalog.Kind kind) {
        return new DashboardReportCatalog.Field(id,label,"Report columns",kind,true,null,false);
    }
    private static List<DashboardReportCatalog.Field> fields(String... pairs) {
        List<DashboardReportCatalog.Field> result=new ArrayList<>();
        for(int i=0;i<pairs.length;i+=2) result.add(field(pairs[i],pairs[i+1],pairs[i].equals("category")||pairs[i].equals("company")||pairs[i].equals("parameter")?TEXT:NUMBER));
        return result;
    }
    public Options options(UserPrincipal user) {
        catalog.isAdmin(user);
        List<Type> types=new ArrayList<>();
        types.add(new Type("ROSTER","Student / Faculty Mentor Allotment","Current student, company, source, stipend and faculty mentor details.",true,"Main report ",rosterFields()));
        types.add(type("OVERALL","Overall Internship Summary","Source categories, including students without a recorded source.",fields("category","Category","total","Number of Students","percentage","Percentage of Total")));
        types.add(type("STIPEND","Stipend Analysis","Paid, unpaid and unavailable stipend data by source.",fields("category","Category","paid","Stipend Students","unpaid","Non-Stipend Students","unknown","Stipend Unavailable","total","Total")));
        types.add(type("STIPEND_DISTRIBUTION","Stipend Distribution","Distribution by actual monthly stipend amount, in INR.",List.of(field("stipend","Monthly stipend (INR)",NUMBER),field("total","Students",NUMBER))));
        types.add(type("COMPANY","Company-wise Student Distribution","Companies and student counts by internship source.",fields("company","Company","department","Department","cdc","CDC","self","Self","unknown","Source Unavailable","total","Total")));
        types.add(type("COMPANY_STIPEND","Company + Stipend Analysis","Company counts, stipend status and recorded full-time offers.",fields("company","Company","total","Total Students","paid","Stipend Students","unpaid","Non-Stipend Students","offers","Placement Offers")));
        types.add(type("COMPARISON","Department / CDC / Self Comparison","Available source comparisons. Diary issued is not diary submitted.",fields("parameter","Parameter","department","Department","cdc","CDC","self","Self","unknown","Source Unavailable","total","Total")));
        types.add(type("PLACEMENT","Placement Offer Analysis","Uses the recorded full-time employment offered flag; unknowns remain separate.",fields("category","Category","total","Total Students","offers","Placement Offer","noOffers","No Placement Offer","unknown","Offer Unavailable")));
        types.add(type("PLACEMENT_COMPANY","Company-wise Placement Offers","Recorded full-time offers grouped by company.",fields("company","Company","total","Total Students","offers","Placement Offer","noOffers","No Placement Offer")));
        types.add(type("WORKFLOW","Internship Status Analysis","Actual onboarding workflow states; not a completion or joining status.",fields("category","Category","total","Students")));
        types.add(new Type("MONTHLY_PROGRESS","Monthly Progress Photos","Original image EXIF/GPS metadata; this does not prove physical presence.",true,"Attedance report ",monthlyFields()));
        types.add(new Type("ATTENDANCE","Attendance Records","One row per stored attendance record. Missing submissions are not absent records.",true,"Attedance report ",attendanceFields()));
        types.add(new Type("ATTENDANCE_SUMMARY","Student Attendance Summary","Present/absent counts and percentages among stored records. Working-day totals are unavailable.",true,"Attedance report ",summaryFields()));
        types.add(new Type("DIARY_ISSUANCE","Diary Issuance","Uses the existing diary-issued flag. Submission and evaluation data are unavailable.",true,"DATA reguired  from students",fields("category","Category","issued","Diary Issued","notIssued","Diary Not Issued","total","Total")));
        types.add(new Type("PARTICIPATION","Student Participation / Joined","Unavailable: no selected, joined or completed internship status exists.",false,"CDC self  Dept wise student rep",List.of()));
        types.add(new Type("EVALUATION","Evaluation and Diary Submission","Unavailable: no evaluation or diary-submission model exists. Diary issuance is available separately.",false,"Marks report format ",List.of()));
        types.add(new Type("SEMINAR","Project Seminar Evaluation","Unavailable: no project, seminar scores, evaluation date or remarks model exists.",false,"Marks report format ",List.of()));
        List<DashboardReportCatalog.Filter> filters=new ArrayList<>(catalog.filters(user));
        filters.removeIf(f->Set.of("attendanceStatus","verificationStatus","historyFrom","historyTo","attendanceDate").contains(f.id()));
        filters.add(new DashboardReportCatalog.Filter("historyFrom","Attendance from","date",List.of(),"Inclusive attendance record date"));
        filters.add(new DashboardReportCatalog.Filter("historyTo","Attendance through","date",List.of(),"Inclusive attendance record date"));
        filters.add(new DashboardReportCatalog.Filter("attendanceStatus","Attendance status","select",List.of(new DashboardReportCatalog.Choice("PRESENT","Present"),new DashboardReportCatalog.Choice("ABSENT","Absent")),"Filters stored attendance records"));
        filters.add(new DashboardReportCatalog.Filter("verificationStatus","Verification status","select",List.of(new DashboardReportCatalog.Choice("VERIFIED","Verified"),new DashboardReportCatalog.Choice("UNVERIFIED","Unverified")),"Filters stored attendance records"));
        filters.add(new DashboardReportCatalog.Filter("geoVerificationStatus","Geo tag status","select",List.of(new DashboardReportCatalog.Choice("VERIFIED","Verified"),new DashboardReportCatalog.Choice("MISSING_GPS","Missing GPS"),new DashboardReportCatalog.Choice("INVALID_GPS","Invalid GPS"),new DashboardReportCatalog.Choice("METADATA_READ_FAILED","Metadata read failed")),"Monthly progress only"));
        for (String id : List.of("gpsPresent","exifPresent")) filters.add(new DashboardReportCatalog.Filter(id,id,"select",List.of(new DashboardReportCatalog.Choice("true","Yes"),new DashboardReportCatalog.Choice("false","No")),"Monthly progress only"));
        if(!catalog.isAdmin(user)) {
            filters.add(new DashboardReportCatalog.Filter("paid","Stipend","select",List.of(new DashboardReportCatalog.Choice("PAID","Paid"),new DashboardReportCatalog.Choice("UNPAID","Unpaid")),""));
            filters.add(new DashboardReportCatalog.Filter("fullTimeOffered","Full-time employment offered","select",List.of(new DashboardReportCatalog.Choice("YES","Yes"),new DashboardReportCatalog.Choice("NO","No")),""));
        }
        return new Options(types,filters,catalog.isAdmin(user)?"Authorized institutional students":"Only your currently assigned students",LocalDate.now(clock).toString());
    }
    private Type type(String id,String title,String description,List<DashboardReportCatalog.Field> fields) {
        return new Type(id,title,description,true,"CDC self  Dept wise student rep",fields);
    }
    private List<DashboardReportCatalog.Field> identity() {
        return List.of(field("studentId","Student account ID",TEXT),field("uid","UID",TEXT),field("studentName","Student",TEXT),field("branch","Branch",TEXT),field("mentorName","Faculty Mentor",TEXT),field("company","Company",TEXT),field("workflow","Internship workflow",TEXT));
    }
    private List<DashboardReportCatalog.Field> monthlyFields() {
        var f = new ArrayList<>(identity());
        f.add(field("monthNumber","Report Month",NUMBER));
        f.add(new DashboardReportCatalog.Field("geoVerificationStatus","Geo Tag Status","Photo metadata",TEXT,false,null,false));
        f.add(new DashboardReportCatalog.Field("gpsLatitude","GPS Latitude","Photo metadata",NUMBER,false,null,false));
        f.add(new DashboardReportCatalog.Field("gpsLongitude","GPS Longitude","Photo metadata",NUMBER,false,null,false));
        f.add(new DashboardReportCatalog.Field("gpsAltitude","GPS Altitude","Photo metadata",NUMBER,false,null,false));
        f.add(new DashboardReportCatalog.Field("gpsTimestamp","GPS Timestamp","Photo metadata",DATETIME,false,null,false));
        f.add(new DashboardReportCatalog.Field("imageCaptureTimestamp","Image Capture Timestamp","Photo metadata",DATETIME,false,null,false));
        f.add(new DashboardReportCatalog.Field("cameraMake","Camera Make","Photo metadata",TEXT,false,null,false));
        f.add(new DashboardReportCatalog.Field("cameraModel","Camera Model","Photo metadata",TEXT,false,null,false));
        f.add(new DashboardReportCatalog.Field("exifPresent","EXIF Present","Photo metadata",BOOLEAN,false,null,false));
        f.add(new DashboardReportCatalog.Field("gpsPresent","GPS Present","Photo metadata",BOOLEAN,false,null,false));
        f.add(new DashboardReportCatalog.Field("captureTimeStatus","Capture Time Status","Photo metadata",TEXT,false,null,false));
        f.add(new DashboardReportCatalog.Field("exifVerificationStatus","EXIF Verification Status","Photo metadata",TEXT,false,null,false));
        f.add(new DashboardReportCatalog.Field("geoVerificationMessage","EXIF Verification Message","Photo metadata",TEXT,false,null,false));
        f.add(new DashboardReportCatalog.Field("imageSha256","Image SHA-256","Photo metadata",TEXT,false,null,false));
        return f;
    }
    private List<DashboardReportCatalog.Field> attendanceFields() {
        List<DashboardReportCatalog.Field> f=new ArrayList<>(identity());
        f.addAll(List.of(field("date","Attendance date",DATE),field("submittedAt","Submitted at",DATETIME),field("result","Attendance status",TEXT),field("verification","Verification status",TEXT),field("submissionType","Submission type",TEXT),field("workMode","Expected work mode",TEXT),field("reason","Verification reason",TEXT),field("liveness","Liveness passed",BOOLEAN),field("locationPassed","Location check passed",BOOLEAN)));
        return f;
    }
    private List<DashboardReportCatalog.Field> rosterFields() {
        List<DashboardReportCatalog.Field> f=new ArrayList<>(identity());
        f.addAll(List.of(field("source","Internship source",TEXT),field("stipend","Monthly stipend (INR)",NUMBER),field("offer","Full-time offer",BOOLEAN)));
        return f;
    }
    private List<DashboardReportCatalog.Field> summaryFields() {
        List<DashboardReportCatalog.Field> f=new ArrayList<>(identity());
        f.addAll(fields("total","Stored Records","present","Present","absent","Absent","verified","Verified","unverified","Unverified","percentage","Present / Stored Records (%)"));return f;
    }
    private record Query(Type type,List<DashboardReportCatalog.Field> fields,String sql,MapSqlParameterSource params) {}
    private Query query(UserPrincipal user,ReportTableRequest request) {
        var options=options(user);
        Type type=options.types().stream().filter(t->t.id().equals(request.reportType())).findFirst().orElseThrow(()->new IllegalArgumentException("Unsupported report type"));
        if(!type.available()) throw new IllegalArgumentException(type.description());
        boolean attendance=type.id().startsWith("ATTENDANCE");
        if(request.fields()!=null && request.fields().isEmpty()) throw new IllegalArgumentException("Select at least one report field");
        Map<String,String> filters=request.filters()==null?new LinkedHashMap<>():new LinkedHashMap<>(request.filters());
        for(var entry:filters.entrySet()) {
            var f=options.filters().stream().filter(v->v.id().equals(entry.getKey())).findFirst().orElseThrow(()->new IllegalArgumentException("Unsupported report filter"));
            if(entry.getValue()==null||entry.getValue().length()>254) throw new IllegalArgumentException("Invalid report filter");
            if(!entry.getValue().isBlank()&&f.type().equals("select")&&f.choices().stream().noneMatch(c->c.value().equals(entry.getValue()))) throw new IllegalArgumentException("Invalid filter value");
        }
        for(String id:List.of("historyFrom","historyTo","attendanceStatus","verificationStatus")) if(!attendance&&filters.containsKey(id)&&!filters.get(id).isBlank()) throw new IllegalArgumentException("Attendance filters require an attendance report");
        Map<String,String> studentFilters=new LinkedHashMap<>(filters);
        for(String id:List.of("historyFrom","historyTo","attendanceStatus","verificationStatus")) studentFilters.remove(id);
        // Mentor report filters share the same predicates; access still derives from the principal.
        String paid=studentFilters.remove("paid"), offer=studentFilters.remove("fullTimeOffered");
        String monthlyWindow = "";
        for (String id : List.of("geoVerificationStatus","gpsPresent","exifPresent")) {
            String value = studentFilters.remove(id);
            if (value != null && !value.isBlank()) {
                if (!type.id().equals("MONTHLY_PROGRESS")) throw new IllegalArgumentException("Photo filters require a monthly progress report");
                String column = id.equals("geoVerificationStatus") ? "geo_verification_status" : id.equals("gpsPresent") ? "gps_present" : "exif_present";
                monthlyWindow += " and m." + column + " = :" + id;
            }
        }
        var scope=repository.scope(user,studentFilters);
        String studentSql=repository.reportStudentsSql(scope);
        MapSqlParameterSource params=new MapSqlParameterSource(scope.params().getValues());
        if(paid!=null&&!paid.isBlank()) studentSql+=" and i.stipend_per_month "+(paid.equals("PAID")?"> 0":"= 0");
        if(offer!=null&&!offer.isBlank()) { studentSql+=" and i.full_time_employment_offered = :reportOffer";params.addValue("reportOffer",offer.equals("YES")); }
        if(request.studentIds()!=null&&!request.studentIds().isEmpty()) {
            if(new HashSet<>(request.studentIds()).size()!=request.studentIds().size()) throw new IllegalArgumentException("Duplicate student IDs");
            params.addValue("reportIds",request.studentIds());
            // Validate against unfiltered authorized membership, so additional filters cannot mask guessed IDs.
            var unfiltered=repository.scope(user,Map.of());
            Long authorized=jdbc.queryForObject("select count(*) from ("+repository.reportStudentsSql(unfiltered)+") authorized where sid in (:reportIds)",new MapSqlParameterSource(unfiltered.params().getValues()).addValue("reportIds",request.studentIds()),Long.class);
            if(authorized==null||authorized!=request.studentIds().size()) throw new AccessDeniedException("One or more students are outside your authorized report scope");
            studentSql+=" and u.id in (:reportIds)";
        }
        for (String id : List.of("geoVerificationStatus","gpsPresent","exifPresent")) if (filters.containsKey(id) && !filters.get(id).isBlank()) params.addValue(id,id.equals("geoVerificationStatus") ? filters.get(id) : Boolean.valueOf(filters.get(id)));
        String base="("+studentSql+") s";
        String window="";
        LocalDate from=null,to=null;
        try {
            if(filters.containsKey("historyFrom")&&!filters.get("historyFrom").isBlank()) from=LocalDate.parse(filters.get("historyFrom"));
            if(filters.containsKey("historyTo")&&!filters.get("historyTo").isBlank()) to=LocalDate.parse(filters.get("historyTo"));
        } catch(RuntimeException ex) {throw new IllegalArgumentException("Attendance dates must use YYYY-MM-DD");}
        if(from!=null&&to!=null&&from.isAfter(to)) throw new IllegalArgumentException("Attendance from must be on or before through");
        if(from!=null) {window+=" and ar.attendance_date >= :reportFrom";params.addValue("reportFrom",java.sql.Date.valueOf(from));}
        if(to!=null) {window+=" and ar.attendance_date <= :reportTo";params.addValue("reportTo",java.sql.Date.valueOf(to));}
        for(String[] pair:new String[][]{{"attendanceStatus","attendance_result"},{"verificationStatus","verification_status"}}) if(filters.containsKey(pair[0])&&!filters.get(pair[0]).isBlank()) {
            window+=" and ar."+pair[1]+" = :"+pair[0];params.addValue(pair[0],filters.get(pair[0]));
        }
        String sql=switch(type.id()) {
            case "MONTHLY_PROGRESS" -> "select s.sid as studentId,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow,m.id as recordId,m.report_month_number as monthNumber,m.geo_verification_status as geoVerificationStatus,m.exif_latitude as gpsLatitude,m.exif_longitude as gpsLongitude,m.gps_altitude as gpsAltitude,m.gps_timestamp as gpsTimestamp,m.exif_captured_at as imageCaptureTimestamp,m.camera_make as cameraMake,m.camera_model as cameraModel,m.exif_present as exifPresent,m.gps_present as gpsPresent,m.geo_verification_status as exifVerificationStatus,m.capture_time_status as captureTimeStatus,m.geo_verification_message as geoVerificationMessage,m.image_sha256 as imageSha256 from "+base+" join monthly_progress_reports m on m.student_internship_id=s.internshipId where 1=1"+monthlyWindow;
            case "ROSTER" -> "select s.sid as studentId,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow,s.source,s.stipend,s.offer from "+base;
            case "OVERALL" -> "select source as category,count(*) as total,cast(cast(count(*) as decimal(18,4))*100/nullif((select count(*) from "+base+"),0) as decimal(12,4)) as percentage from "+base+" group by source";
            case "STIPEND" -> "select source as category,count(*) as total,"+sums()+",sum(case when stipend is null then 1 else 0 end) as \"unknown\" from "+base+" group by source";
            case "STIPEND_DISTRIBUTION" -> "select stipend,count(*) as total from "+base+" where stipend is not null group by stipend";
            case "COMPANY" -> "select company,"+sources("1=1")+" from "+base+" where company is not null group by company";
            case "COMPANY_STIPEND" -> "select company,count(*) as total,"+sums()+",sum(case when offer=true then 1 else 0 end) as offers from "+base+" where company is not null group by company";
            case "PLACEMENT", "PLACEMENT_COMPANY" -> "select "+(type.id().equals("PLACEMENT")?"source as category":"company")+",count(*) as total,sum(case when offer=true then 1 else 0 end) as offers,sum(case when offer=false then 1 else 0 end) as noOffers"+(type.id().equals("PLACEMENT")?",sum(case when offer is null then 1 else 0 end) as \"unknown\"":"")+" from "+base+(type.id().equals("PLACEMENT")?" group by source":" where company is not null group by company");
            case "WORKFLOW" -> "select workflow as category,count(*) as total from "+base+" group by workflow";
            case "DIARY_ISSUANCE" -> "select source as category,count(*) as total,sum(case when diary=true then 1 else 0 end) as issued,sum(case when diary=false or diary is null then 1 else 0 end) as notIssued from "+base+" group by source";
            case "COMPARISON" -> comparison(base);
            case "ATTENDANCE" -> "select s.sid as studentId,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow,ar.id as recordId,ar.attendance_date as date,ar.server_submitted_at as submittedAt,ar.attendance_result as result,ar.verification_status as verification,ar.submission_type as submissionType,e.expected_work_mode as workMode,e.verification_reason as reason,e.ai_liveness_passed as liveness,e.location_check_passed as locationPassed from "+base+" join attendance_records ar on ar.student_user_id=s.sid and ar.internship_id=s.internshipId left join attendance_evidence e on e.attendance_id=ar.id where 1=1"+window;
            case "ATTENDANCE_SUMMARY" -> "select s.sid as studentId,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow,count(ar.id) as total,sum(case when ar.attendance_result='PRESENT' then 1 else 0 end) as present,sum(case when ar.attendance_result='ABSENT' then 1 else 0 end) as absent,sum(case when ar.verification_status='VERIFIED' then 1 else 0 end) as verified,sum(case when ar.verification_status='UNVERIFIED' then 1 else 0 end) as unverified,cast(100.0*sum(case when ar.attendance_result='PRESENT' then 1 else 0 end)/nullif(count(ar.id),0) as decimal(12,4)) as percentage from "+base+" left join attendance_records ar on ar.student_user_id=s.sid and ar.internship_id=s.internshipId"+window+" group by s.sid,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow";
            default -> throw new IllegalArgumentException("Unsupported report type");
        };
        List<DashboardReportCatalog.Field> selected=type.id().equals("MONTHLY_PROGRESS") ? type.fields().stream().filter(DashboardReportCatalog.Field::defaultSelected).toList() : type.fields();
        if(request.fields()!=null&&!request.fields().isEmpty()) {
            if(new HashSet<>(request.fields()).size()!=request.fields().size()) throw new IllegalArgumentException("Duplicate report fields");
            selected=request.fields().stream().map(id->type.fields().stream().filter(f->f.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unsupported report field"))).toList();
        }
        return new Query(type,selected,sql,params);
    }
    private static String sums() {return "sum(case when stipend>0 then 1 else 0 end) as paid,sum(case when stipend=0 then 1 else 0 end) as unpaid";}
    private static String sources(String condition) {
        return "sum(case when source='DEPARTMENT' and "+condition+" then 1 else 0 end) as department,sum(case when source='CDC' and "+condition+" then 1 else 0 end) as cdc,sum(case when source='SELF' and "+condition+" then 1 else 0 end) as self,sum(case when source='UNRECORDED' and "+condition+" then 1 else 0 end) as \"unknown\",sum(case when "+condition+" then 1 else 0 end) as total";
    }
    private static String comparison(String base) {
        return List.of(new String[]{"Total Students","1=1"},new String[]{"Stipend Students","stipend>0"},new String[]{"Non-Stipend Students","stipend=0"},new String[]{"Placement Offers","offer=true"},new String[]{"Diary Issued","diary=true"},new String[]{"Details Complete","workflow='INTERNSHIP_DETAILS_COMPLETE'"}).stream().map(p->"select '"+p[0]+"' as parameter,"+sources(p[1])+" from "+base).collect(Collectors.joining(" union all "));
    }
    private List<Map<String,Object>> rows(Query query,int offset,int size) {
        String order=query.type().id().equals("ATTENDANCE")?"date,studentId,recordId":Set.of("ATTENDANCE_SUMMARY","ROSTER").contains(query.type().id())?"studentId":"1";
        return jdbc.query("select * from ("+query.sql()+") report order by "+order+" limit :reportSize offset :reportOffset",new MapSqlParameterSource(query.params().getValues()).addValue("reportSize",size).addValue("reportOffset",offset),(rs,num)->{
            Map<String,Object> row=new LinkedHashMap<>();
            for(var f:query.fields()) {Object value=rs.getObject(f.id());if(value instanceof java.sql.Timestamp t) value=t.toInstant().atZone(clock.getZone()).toOffsetDateTime();row.put(f.id(),value);}return row;
        });
    }
    private Map<String,Object> summary(Query query) {
        if(query.type().id().equals("ATTENDANCE")) return jdbc.queryForMap("select count(*) as records,sum(case when result='PRESENT' then 1 else 0 end) as present,sum(case when result='ABSENT' then 1 else 0 end) as absent,sum(case when verification='VERIFIED' then 1 else 0 end) as verified,sum(case when verification='UNVERIFIED' then 1 else 0 end) as unverified,cast(100.0*sum(case when result='PRESENT' then 1 else 0 end)/nullif(count(*),0) as decimal(12,4)) as presentRecordPercentage from ("+query.sql()+") report",query.params());
        return Map.of();
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Table preview(UserPrincipal user,ReportTableRequest request) {
        Query q=query(user,request);Long total=jdbc.queryForObject("select count(*) from ("+q.sql()+") report",q.params(),Long.class);
        return new Table(q.fields(),rows(q,request.page()*50,50),total==null?0:total,request.page(),summary(q),q.type().title());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public DashboardReportService.Download export(UserPrincipal user,ReportTableRequest request) throws IOException {
        Query q=query(user,request);
        String extension=request.format()==DashboardReportRequest.Format.XLSX?"xlsx":"csv";
        Path file=Files.createTempFile("ims-institutional-report-","."+extension);
        try {
            int count;
            try(DashboardReportWriter writer=new DashboardReportWriter(file,request.format(),q.fields(),clock.getZone(),q.type().title(),q.type().historicalSheet())) {
                int offset=0;
                while(true) {var batch=rows(q,offset,250);if(batch.isEmpty())break;for(var row:batch)writer.row(row);offset+=batch.size();}
                count=writer.count();Map<String,String> metadata=new LinkedHashMap<>();
                metadata.put("Generated at",ZonedDateTime.now(clock).toString());metadata.put("Generated by",user.email());metadata.put("Access scope",options(user).scope());metadata.put("Record count",Integer.toString(count));
                metadata.put("Applied filters",Objects.toString(request.filters(),"None"));metadata.put("Selected students",request.studentIds()==null||request.studentIds().isEmpty()?"All within authorized filters":request.studentIds().toString());
                metadata.put("Report definition",q.type().description());metadata.put("Historical reference",q.type().historicalSheet());
                writer.finish(metadata);
            }
            return new DashboardReportService.Download(file,q.type().id().toLowerCase(Locale.ROOT)+"-"+LocalDate.now(clock)+"."+extension,extension.equals("xlsx")?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"text/csv;charset=UTF-8",count);
        }catch(IOException|RuntimeException ex){Files.deleteIfExists(file);throw ex;}
    }
}

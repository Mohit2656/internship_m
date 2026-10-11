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
        types.add(new Type("MONTHLY_PROGRESS","Monthly Progress Reports","Uploaded reports and explicit faculty mentor review decisions.",true,"Attedance report ",monthlyFields()));
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
        filters.add(new DashboardReportCatalog.Filter("verificationStatus","Liveness status","select",List.of(new DashboardReportCatalog.Choice("VERIFIED","Liveness verified"),new DashboardReportCatalog.Choice("FAILED","Liveness failed"),new DashboardReportCatalog.Choice("NOT_PERFORMED","Liveness not performed"),new DashboardReportCatalog.Choice("HISTORICAL","Historical verification")),"Filters current liveness outcomes; historical location decisions are not reassessed"));
        filters.add(new DashboardReportCatalog.Filter("monthlyStatus","Monthly report review","select",List.of(new DashboardReportCatalog.Choice("PENDING_MENTOR_REVIEW","Pending faculty mentor review"),new DashboardReportCatalog.Choice("APPROVED","Approved"),new DashboardReportCatalog.Choice("REJECTED","Rejected")),"Monthly progress only"));
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
        f.add(field("recordId","Monthly record ID",NUMBER));
        f.add(field("monthNumber","Report Month",NUMBER));
        f.addAll(List.of(field("reviewStatus","Review status",TEXT),field("revision","Revision",NUMBER),field("periodStart","Period start",DATE),field("periodEnd","Period end",DATE),field("workDescription","Work description",TEXT),field("submittedAt","Uploaded at",DATETIME),field("reviewerId","Reviewer ID",NUMBER),field("reviewedAt","Reviewed at",DATETIME),field("rejectionReason","Rejection reason",TEXT),field("imageSha256","Image SHA-256",TEXT)));
        return f;
    }
    private List<DashboardReportCatalog.Field> attendanceFields() {
        List<DashboardReportCatalog.Field> f=new ArrayList<>(identity());
        f.add(field("recordId","Attendance record ID",NUMBER));
        f.add(field("imageSizeBytes","Camera evidence bytes",NUMBER));
        f.add(field("metadataConsistent","Metadata consistent",BOOLEAN));
        f.addAll(List.of(field("attendanceLatitude","Submission latitude",NUMBER),field("attendanceLongitude","Submission longitude",NUMBER),field("gpsAccuracyMeters","Submission GPS accuracy (m)",NUMBER)));
        f.addAll(List.of(field("date","Attendance date",DATE),field("submittedAt","Submitted at",DATETIME),field("result","Attendance status",TEXT),field("verification","Liveness status",TEXT),field("submissionType","Submission type",TEXT),field("workMode","Expected work mode",TEXT),field("reason","Liveness reason",TEXT),field("liveness","Current liveness verified",BOOLEAN)));
        return f;
    }
    private List<DashboardReportCatalog.Field> rosterFields() {
        List<DashboardReportCatalog.Field> f=new ArrayList<>(identity());
        f.addAll(List.of(field("source","Internship source",TEXT),field("stipend","Monthly stipend (INR)",NUMBER),field("offer","Full-time offer",BOOLEAN)));
        return f;
    }
    private List<DashboardReportCatalog.Field> summaryFields() {
        List<DashboardReportCatalog.Field> f=new ArrayList<>(identity());
        f.addAll(fields("total","Stored Records","present","Present","absent","Absent","verified","Liveness verified","unverified","Liveness failed","notPerformed","Liveness not performed","historical","Historical verification","percentage","Present / Stored Records (%)"));return f;
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
        String monthlyStatus = studentFilters.remove("monthlyStatus");
        if (monthlyStatus!=null && !monthlyStatus.isBlank()) {
            if (!type.id().equals("MONTHLY_PROGRESS")) throw new IllegalArgumentException("Monthly review filters require a monthly report");
            monthlyWindow=" and m.review_status = :monthlyStatus";
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
        if (monthlyStatus!=null && !monthlyStatus.isBlank()) params.addValue("monthlyStatus",monthlyStatus);
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
        if(filters.containsKey("attendanceStatus")&&!filters.get("attendanceStatus").isBlank()) {
            window+=" and ar.attendance_result = :attendanceStatus";params.addValue("attendanceStatus",filters.get("attendanceStatus"));
        }
        if(filters.containsKey("verificationStatus")&&!filters.get("verificationStatus").isBlank()) {
            window+=" and ("+AttendanceLivenessSql.status("ar","e")+") = :verificationStatus";params.addValue("verificationStatus",filters.get("verificationStatus"));
        }
        String liveness=AttendanceLivenessSql.status("ar","e");
        String summaryAttendance = "(select ar.*, " + liveness + " as liveness_outcome from attendance_records ar left join attendance_evidence e on e.attendance_id=ar.id where ar.deleted_at is null" + window + ") ar";
        String sql=switch(type.id()) {
            case "MONTHLY_PROGRESS" -> "select s.sid as studentId,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow,m.id as recordId,m.report_month_number as monthNumber,m.review_status as reviewStatus,m.revision,m.period_start as periodStart,m.period_end as periodEnd,m.work_description as workDescription,m.submitted_at as submittedAt,m.reviewer_id as reviewerId,m.reviewed_at as reviewedAt,m.rejection_reason as rejectionReason,m.image_sha256 as imageSha256 from "+base+" join monthly_progress_reports m on m.student_internship_id=s.internshipId and m.deleted_at is null where 1=1"+monthlyWindow;
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
            case "ATTENDANCE" -> "select s.sid as studentId,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow,ar.id as recordId,case when e.latitude between -90 and 90 and e.longitude between -180 and 180 then e.latitude end as attendanceLatitude,case when e.latitude between -90 and 90 and e.longitude between -180 and 180 then e.longitude end as attendanceLongitude,case when e.latitude between -90 and 90 and e.longitude between -180 and 180 and e.gps_accuracy_meters between 0 and 999999.99 then e.gps_accuracy_meters end as gpsAccuracyMeters,ar.attendance_date as date,ar.server_submitted_at as submittedAt,ar.attendance_result as result,"+liveness+" as verification,ar.submission_type as submissionType,e.expected_work_mode as workMode,"+AttendanceLivenessSql.reason("ar","e")+" as reason,case when ("+liveness+")='VERIFIED' then true when ("+liveness+")='FAILED' then false else null end as liveness,e.image_size_bytes as imageSizeBytes,e.metadata_consistent as metadataConsistent from "+base+" join attendance_records ar on ar.student_user_id=s.sid and ar.internship_id=s.internshipId and ar.deleted_at is null left join attendance_evidence e on e.attendance_id=ar.id where 1=1"+window;
            case "ATTENDANCE_SUMMARY" -> "select s.sid as studentId,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow,count(ar.id) as total,sum(case when ar.attendance_result='PRESENT' then 1 else 0 end) as present,sum(case when ar.attendance_result='ABSENT' then 1 else 0 end) as absent,sum(case when ar.liveness_outcome='VERIFIED' then 1 else 0 end) as verified,sum(case when ar.liveness_outcome='FAILED' then 1 else 0 end) as unverified,sum(case when ar.liveness_outcome='NOT_PERFORMED' then 1 else 0 end) as notPerformed,sum(case when ar.liveness_outcome='HISTORICAL' then 1 else 0 end) as historical,cast(100.0*sum(case when ar.attendance_result='PRESENT' then 1 else 0 end)/nullif(count(ar.id),0) as decimal(12,4)) as percentage from "+base+" left join "+summaryAttendance+" on ar.student_user_id=s.sid and ar.internship_id=s.internshipId"+" group by s.sid,s.uid,s.studentName,s.branch,s.mentorName,s.company,s.workflow";
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
        if(query.type().id().equals("ATTENDANCE")) return jdbc.queryForMap("select count(*) as records,sum(case when result='PRESENT' then 1 else 0 end) as present,sum(case when result='ABSENT' then 1 else 0 end) as absent,sum(case when verification='VERIFIED' then 1 else 0 end) as verified,sum(case when verification='FAILED' then 1 else 0 end) as unverified,sum(case when verification='NOT_PERFORMED' then 1 else 0 end) as \"notPerformed\",sum(case when verification='HISTORICAL' then 1 else 0 end) as historical,cast(100.0*sum(case when result='PRESENT' then 1 else 0 end)/nullif(count(*),0) as decimal(12,4)) as \"presentRecordPercentage\" from ("+query.sql()+") report",query.params());
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

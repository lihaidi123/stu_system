package edu.stusystem.service;

import edu.stusystem.api.ApiException;
import edu.stusystem.security.CurrentUser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GradeImportService {
    private static final List<String> HEADERS=List.of("学号","课程名称","学分","考核性质","学期编码","成绩");
    private final JdbcTemplate db;
    private final XlsxWorkbookReader reader;
    private final DomainService domain;

    public GradeImportService(JdbcTemplate db,XlsxWorkbookReader reader,DomainService domain){this.db=db;this.reader=reader;this.domain=domain;}

    @Audited(action="GRADE_IMPORT",targetType="grade_batch")
    public Map<String,Object> importGrades(CurrentUser actor,MultipartFile file){
        if(actor==null||!List.of("ADMIN","TEACHER").contains(actor.role()))throw new ApiException(HttpStatus.FORBIDDEN,"ROLE_REQUIRED","仅管理员或教师可导入成绩");
        var rows=reader.read(file);validateHeader(rows);
        if(rows.size()==1)throw invalid(1);
        var items=new ArrayList<Map<String,Object>>();var sourceRows=new ArrayList<Integer>();var keys=new HashSet<String>();
        for(int i=1;i<rows.size();i++){
            var row=rows.get(i);
            try {
                if(row.cells().size()!=HEADERS.size())throw invalid(row.sourceRow());
                String studentNo=textCell(row,0),courseName=textCell(row,1),assessment=textCell(row,3),termCode=textCell(row,4);
                long studentId=resolve("SELECT id FROM student_profile WHERE student_no=?",studentNo,row.sourceRow());
                long courseId=resolve("SELECT id FROM course_catalog WHERE name=?",courseName,row.sourceRow());
                long termId=resolve("SELECT id FROM academic_term WHERE code=?",termCode,row.sourceRow());
                BigDecimal credits=decimalCell(row,2),score=decimalCell(row,5);
                if(credits.signum()<0||credits.scale()>2||credits.compareTo(new BigDecimal("999.99"))>0||score.signum()<0||score.compareTo(BigDecimal.valueOf(100))>0||score.scale()>2)throw invalid(row.sourceRow());
                String type=switch(assessment){case "考试"->"EXAM";case "考查"->"ASSESSMENT";default->throw invalid(row.sourceRow());};
                if(!keys.add(studentId+":"+courseId+":"+termId))throw invalid(row.sourceRow());
                items.add(Map.of("studentId",Long.toString(studentId),"courseId",Long.toString(courseId),"termId",Long.toString(termId),"credits",credits.toPlainString(),"assessmentType",type,"score",score.toPlainString()));
                sourceRows.add(row.sourceRow());
            } catch(ApiException e){throw e;}
            catch(DataAccessException unavailable){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"DATA_UNAVAILABLE","成绩关联数据暂不可用");}
            catch(RuntimeException e){throw invalid(row.sourceRow());}
        }
        try {
            var result=domain.addGradesBatch(actor,items);
            return Map.of("created",items.size(),"rows",sourceRows.size(),"data",result);
        } catch(ApiException e){
            Integer index=batchIndex(e.getMessage());
            String message=index!=null&&index>0&&index<=sourceRows.size()?"第"+sourceRows.get(index-1)+"行：成绩批次校验失败":"成绩批次校验失败，未写入任何记录";
            throw new ApiException(e.status(),"GRADE_IMPORT_REJECTED",message);
        } catch(DataAccessException unavailable){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"DATA_UNAVAILABLE","成绩导入存储暂不可用");
        }
    }

    private long resolve(String sql,String value,int sourceRow){
        try { Long id=db.queryForObject(sql,Long.class,value);if(id==null)throw invalid(sourceRow);return id; }
        catch(EmptyResultDataAccessException missing){throw invalid(sourceRow);}
    }
    private String textCell(XlsxWorkbookReader.WorkbookRow row,int column){
        var cell=row.cells().get(column);
        if(cell.type()!=org.apache.poi.ss.usermodel.CellType.STRING||cell.text().isBlank())throw invalid(row.sourceRow());
        return cell.text().trim();
    }
    private BigDecimal decimalCell(XlsxWorkbookReader.WorkbookRow row,int column){
        var cell=row.cells().get(column);
        if(!List.of(org.apache.poi.ss.usermodel.CellType.NUMERIC,org.apache.poi.ss.usermodel.CellType.STRING).contains(cell.type())||cell.text().isBlank())throw invalid(row.sourceRow());
        try{return new BigDecimal(cell.text().trim());}catch(NumberFormatException e){throw invalid(row.sourceRow());}
    }
    private void validateHeader(List<XlsxWorkbookReader.WorkbookRow> rows){
        if(rows.isEmpty()||rows.getFirst().sourceRow()!=1||rows.getFirst().cells().size()!=HEADERS.size())throw invalid(1);
        for(int i=0;i<HEADERS.size();i++)if(rows.getFirst().cells().get(i).type()!=org.apache.poi.ss.usermodel.CellType.STRING||!HEADERS.get(i).equals(rows.getFirst().cells().get(i).text()))throw invalid(1);
    }
    private static Integer batchIndex(String message){
        if(message==null||!message.startsWith("第"))return null;
        int end=message.indexOf('条');if(end<2)return null;
        try{return Integer.parseInt(message.substring(1,end));}catch(NumberFormatException ignored){return null;}
    }
    private static ApiException invalid(int row){return new ApiException(HttpStatus.BAD_REQUEST,"GRADE_IMPORT_INVALID_ROW","第"+row+"行：成绩文件数据无效");}
}

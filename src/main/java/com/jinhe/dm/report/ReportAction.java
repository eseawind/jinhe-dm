package com.jinhe.dm.report;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.jinhe.dm.Constants;
import com.jinhe.dm.report.permission.ReportPermissionsFull;
import com.jinhe.dm.report.permission.ReportResourceView;
import com.jinhe.dm.report.timer.ReportJob;
import com.jinhe.tss.framework.component.param.Param;
import com.jinhe.tss.framework.component.param.ParamConstants;
import com.jinhe.tss.framework.component.param.ParamManager;
import com.jinhe.tss.framework.component.param.ParamService;
import com.jinhe.tss.framework.component.timer.SchedulerBean;
import com.jinhe.tss.framework.web.dispaly.tree.LevelTreeParser;
import com.jinhe.tss.framework.web.dispaly.tree.TreeEncoder;
import com.jinhe.tss.framework.web.dispaly.xform.XFormEncoder;
import com.jinhe.tss.framework.web.mvc.BaseActionSupport;
import com.jinhe.tss.um.permission.PermissionHelper;
import com.jinhe.tss.util.EasyUtils;

@Controller
@RequestMapping("/auth/rp")
public class ReportAction extends BaseActionSupport {
    
    @Autowired private ReportService reportService;
    
    @RequestMapping("/all")
    public void getAllReport(HttpServletResponse response) {
        List<?> list = reportService.getAllReport();
        TreeEncoder treeEncoder = new TreeEncoder(list, new LevelTreeParser());
        print("SourceTree", treeEncoder);
    }
    
    @RequestMapping("/groups")
    public void getAllReportGroups(HttpServletResponse response) {
        List<?> list = reportService.getAllReportGroups();
        TreeEncoder treeEncoder = new TreeEncoder(list, new LevelTreeParser());
        treeEncoder.setNeedRootNode(true);
        print("SourceTree", treeEncoder);
    }
    
    @RequestMapping(value = "/detail/{type}")
    public void getReport(HttpServletRequest request, HttpServletResponse response, @PathVariable("type") int type) {
        String uri = null;
        if(Report.TYPE0 == type) {
            uri = "template/report_group_xform.xml";
        } else {
            uri = "template/report_xform.xml";
        }
        
        XFormEncoder xformEncoder;
        String reportIdValue = request.getParameter("reportId");
        
        if( reportIdValue == null) {
            Map<String, Object> map = new HashMap<String, Object>();
            
            String parentIdValue = request.getParameter("parentId"); 
            if("_root".equals(parentIdValue)) {
            	parentIdValue = null;
            }
            
            Long parentId = parentIdValue == null ? Report.DEFAULT_PARENT_ID : EasyUtils.convertObject2Long(parentIdValue);
            map.put("parentId", parentId);
            map.put("type", type);
            xformEncoder = new XFormEncoder(uri, map);
        } 
        else {
            Long reportId = EasyUtils.convertObject2Long(reportIdValue);
            Report report = reportService.getReport(reportId);
            xformEncoder = new XFormEncoder(uri, report);
        }
        
        if( Report.TYPE1 == type ) {
            List<Param> datasources = null;
            try {
                datasources = ParamManager.getComboParam(Constants.DATASOURCE_LIST);
            } catch (Exception e) {
            }
            
            if(datasources != null) {
            	 Object[] objs = EasyUtils.generateComboedit(datasources, "value", "text", "|");
                 xformEncoder.setColumnAttribute("datasource", "editorvalue", (String) objs[0]);
                 xformEncoder.setColumnAttribute("datasource", "editortext",  (String) objs[1]);
            }
        }
 
        print("SourceInfo", xformEncoder);
    }

    @RequestMapping(method = RequestMethod.POST)
    public void saveReport(HttpServletResponse response, Report report) {
        boolean isnew = (null == report.getId());
        reportService.saveReport(report);
        doAfterSave(isnew, report, "SourceTree");
    }
    
    @RequestMapping(value = "/{id}", method = RequestMethod.DELETE)
    public void delete(HttpServletResponse response, @PathVariable("id") Long id) {
        reportService.delete(id);
        printSuccessMessage();
    }

    @RequestMapping(value = "/disable/{id}/{disabled}", method = RequestMethod.POST)
    public void startOrStop(HttpServletResponse response, 
            @PathVariable("id") Long id, @PathVariable("disabled") int disabled) {
        
        reportService.startOrStop(id, disabled);
        printSuccessMessage();
    }
 
    @RequestMapping(value = "/sort/{startId}/{targetId}/{direction}", method = RequestMethod.POST)
    public void sort(HttpServletResponse response, 
            @PathVariable("startId") Long startId, 
            @PathVariable("targetId") Long targetId, 
            @PathVariable("direction") int direction) {
        
        reportService.sort(startId, targetId, direction);
        printSuccessMessage();
        
    }

    @RequestMapping(value = "/copy/{reportId}/{groupId}", method = RequestMethod.POST)
    public void copy(HttpServletResponse response, 
            @PathVariable("reportId") Long reportId, @PathVariable("groupId") Long groupId) {
        
        List<?> result = reportService.copy(reportId, groupId);
        TreeEncoder encoder = new TreeEncoder(result, new LevelTreeParser());
        encoder.setNeedRootNode(false);
        print("SourceTree", encoder);
    }

    @RequestMapping(value = "/move/{reportId}/{groupId}", method = RequestMethod.POST)
    public void move(HttpServletResponse response, 
            @PathVariable("reportId") Long reportId, @PathVariable("groupId") Long groupId) {
        
        reportService.move(reportId, groupId);
        printSuccessMessage();
    }
    
	@RequestMapping("/operations/{resourceId}")
    public void getOperations(HttpServletResponse response, @PathVariable("resourceId") Long resourceId) {
        List<String> list = PermissionHelper.getInstance().getOperationsByResource(resourceId,
                        ReportPermissionsFull.class.getName(), ReportResourceView.class);

        print("Operation", EasyUtils.list2Str(list));
    }
	
	
	@Autowired ParamService paramService;
	
	@RequestMapping(value = "/schedule", method = RequestMethod.POST)
    public void saveJobParam(HttpServletResponse response, Long reportId, String configVal) {
		Param jobParam = paramService.getParam(SchedulerBean.TIMER_PARAM_CODE);
		if(jobParam == null) {
			jobParam = new Param();
			jobParam.setCode(SchedulerBean.TIMER_PARAM_CODE);
			jobParam.setName("定时报表配置");
			jobParam.setParentId(ParamConstants.DEFAULT_PARENT_ID);
			jobParam.setType(ParamConstants.NORMAL_PARAM_TYPE);
			jobParam.setModality(ParamConstants.COMBO_PARAM_MODE);
	        paramService.saveParam(jobParam);
    	}
		
		Param jobParamItem = null;
		String jobCode = "ReportJob-" + reportId;
		List<Param> jobParamItems = paramService.getParamsByParentCode(SchedulerBean.TIMER_PARAM_CODE);
		for(Param temp : jobParamItems) {
			if(temp.getDescription().equals(jobCode)) {
				jobParamItem = temp;
				break;
			}
		}
		if(jobParamItem == null) {
			jobParamItem = new Param();
			jobParamItem.setText(reportService.getReport(reportId).getName());
			jobParamItem.setDescription(jobCode);
			jobParamItem.setParentId(jobParam.getId());
			jobParamItem.setType(ParamConstants.ITEM_PARAM_TYPE);
			jobParamItem.setModality(jobParam.getModality());
		}
		jobParamItem.setValue(ReportJob.class.getName() + " | " + configVal);
        paramService.saveParam(jobParamItem);
        
        printSuccessMessage();
    }

	@RequestMapping(value = "/schedule", method = RequestMethod.GET)
	@ResponseBody
    public Object[] getJobParam(HttpServletResponse response, Long reportId) {
		String jobCode = "ReportJob-" + reportId;
		List<Param> jobParamItems = paramService.getParamsByParentCode(SchedulerBean.TIMER_PARAM_CODE);
		if(jobParamItems != null) {
			for(Param temp : jobParamItems) {
				if(temp.getDescription().equals(jobCode)) {
					String value = temp.getValue();
					return EasyUtils.split(value, "|");
				}
			}
		}
		return null;
    }
}

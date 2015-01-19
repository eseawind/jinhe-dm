package com.jinhe.dm.report.timer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import com.jinhe.dm.Constants;
import com.jinhe.dm.data.sqlquery.SQLExcutor;
import com.jinhe.dm.data.util.DataExport;
import com.jinhe.dm.report.ReportService;
import com.jinhe.tss.framework.Global;
import com.jinhe.tss.framework.component.param.ParamManager;
import com.jinhe.tss.framework.component.timer.AbstractJob;
import com.jinhe.tss.framework.component.timer.MailUtil;
import com.jinhe.tss.framework.exception.BusinessException;
import com.jinhe.tss.um.helper.dto.OperatorDTO;
import com.jinhe.tss.um.service.ILoginService;
import com.jinhe.tss.util.EasyUtils;

/**
 * com.jinhe.dm.report.timer.ReportJob | 0 36 10 * * ? | 268:各省日货量流向:pjjin@800best.com,BL01037:param1=today-1
 * 261:各省生产货量:BL00618,BL01037:param1=today-0
 */
public class ReportJob extends AbstractJob {
	
	ReportService reportService = (ReportService) Global.getBean("ReportService");
	ILoginService loginService  = (ILoginService) Global.getBean("LoginService");

	/* 
	 * jobConfig的格式为
	 *  
	 *  1:报表一:x1@x.com
     *  2:报表二:x2@x.com
	 *	3:报表三:x3@x.com,x4@x.com:param1=a,param2=b
	 *  
	 */
	protected void excuteJob(String jobConfig) {
		
		String[] jobConfigs = EasyUtils.split(jobConfig, "\n");
		
		for(int i = 0; i < jobConfigs.length; i++) {
			String reportInfo[] = EasyUtils.split(jobConfigs[i], ":");
			Long reportId = EasyUtils.obj2Long(reportInfo[0]);
			String title = reportInfo[1];
			String receiver[] = getEmails( reportInfo[2].trim().split(",") );
					
	    	Map<String, String> paramsMap = new HashMap<String, String>();
	    	if(reportInfo.length > 3) {
	    		String[] params = reportInfo[3].split(",");
	    		for(String param : params) {
	    			String[] keyValue = param.split("=");
	    			paramsMap.put(keyValue[0].trim(), keyValue[1].trim());
	    		}
	    	}
	        SQLExcutor excutor = reportService.queryReport(reportId, paramsMap, 0, 0, null);
	        
	        send(title, receiver, excutor.result, excutor.selectFields);
		}
	}

	private String[] getEmails(String[] receiver) {
		// 将登陆账号转换成该用户的邮箱
		List<String> emails = new ArrayList<String>();
		for(int j = 0; j < receiver.length; j++) {
			String temp = receiver[j];
			
			// 判断配置的是否已经是email，如不是，做loginName处理
			// TODO 增强，能发给用户组、辅助组 或 角色
			if(temp.indexOf("@") < 0) {
				try {
					OperatorDTO user = loginService.getOperatorDTOByLoginName(temp);
					emails.add( (String) user.getAttribute("email") );
				} catch(Exception e) {
				}
			}
			else {
				emails.add(temp);
			}
		}
		receiver = new String[emails.size()];
		receiver = emails.toArray(receiver);
		
		return receiver;
	}
	
	private void send(String title, String receiver[], List<Map<String, Object>> data, List<String> fields) {
		JavaMailSenderImpl sender = (JavaMailSenderImpl) MailUtil.getMailSender();
		MimeMessage mailMessage = sender.createMimeMessage();
		
		try {
			// 设置utf-8或GBK编码，否则邮件会有乱码
			MimeMessageHelper messageHelper = new MimeMessageHelper(mailMessage, true, "utf-8");
			messageHelper.setTo(receiver);   // 接受者
			messageHelper.setFrom(MailUtil.getEmailFrom());  // 发送者
			messageHelper.setSubject("定时报表：" + title); // 主题
			
			// 邮件内容，注意加参数true
			StringBuffer html = new StringBuffer();
			html.append("<html>");
			html.append("<head>");
			html.append("<style type='text/css'> " );
			html.append("	table { border-collapse:collapse; border-spacing:0; }");
			html.append("	td { line-height: 1.42857143; vertical-align: top;  border: 1px solid black; text-align: left;}");
			html.append("	td { margin:0; padding:0; padding: 2px 2px 2px 2px; font-family: 微软雅黑; font-size: 15px;}");
			html.append("</style>");
			html.append("</head>");
			html.append("<body>");
			
			if(data.size() > 100) {
				html.append("<h1>详细见附件</h1>");
			} else {
				html.append("<table>");
				if(fields != null) {
					html.append("<tr>");
	            	for(String field : fields) {
	            		html.append("<td>").append("&nbsp;").append(field).append("&nbsp;").append("</td>");
	            	}
	            	html.append("</tr>");
	            }
	        	for( Map<String,Object> row : data) {
	        		html.append("<tr>");
	        		for(String field : fields) {
	            		html.append("<td>").append(row.get(field)).append("</td>");
	            	}
	        		html.append("</tr>");
	        	}
				html.append("</table>");
			}
			
			html.append("</body>");
			html.append("</html>");
			
			messageHelper.setText(html.toString(), true);
			log.debug(html);
			
			// 附件内容
			String fileName = title + "-" + System.currentTimeMillis() + ".csv";
	        String exportPath = ParamManager.getValue(Constants.TEMP_EXPORT_PATH).replace("\n", "") + "/" + fileName;
	        DataExport.exportCSV(exportPath, data, fields);
	        
			fileName = MimeUtility.encodeWord(fileName); // 使用MimeUtility.encodeWord()来解决附件名称的中文问题
			messageHelper.addAttachment(MimeUtility.encodeWord(fileName), new File(exportPath));
			
			sender.send(mailMessage);
		} 
		catch (Exception e) {
			throw new BusinessException("发送报表邮件时出错了：", e);
		}
	}
}

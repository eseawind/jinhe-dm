package com.jinhe.dm.analyse.btr;

import org.apache.log4j.Logger;

import com.jinhe.tss.framework.Global;
import com.jinhe.tss.framework.sso.IPWDOperator;
import com.jinhe.tss.um.service.ILoginService;
import com.jinhe.tss.um.sso.UMPasswordIdentifier;

/**
 * <p>
 * UM本地用户密码身份认证器<br>
 * 根据用户帐号、密码等信息，通过UM本地数据库进行身份认证
 * </p>
 */
public class BTRUserIdentifier extends UMPasswordIdentifier {
    
    protected Logger log = Logger.getLogger(this.getClass());
    
    ILoginService loginservice = (ILoginService) Global.getBean("LoginService");
    
    protected boolean customizeValidate(IPWDOperator operator, String password){
        
    	log.debug("用户登陆时密码在TSS的主用户组中验证不通过，转向V5进行再次验证。");
        
        BaseService btrService = (BaseService) Global.getBean("BaseService");
        
        String loginName = operator.getLoginName();
        boolean result = btrService.login(loginName, password);
 
        if(result) {
            log.info("用户【" + loginName + "】的密码在V5中验证通过。");
            return true;
        } 
        else {
            log.warn("用户【" + loginName + "】的密码在V5中验证不通过。");
            return false;
        } 
    }
}

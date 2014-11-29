package com.jinhe.dm.report.permission;

import javax.persistence.Entity;
import javax.persistence.Table;

import com.jinhe.tss.um.permission.AbstractPermission;

/** 
 *报表资源权限补齐表
 */
@Entity
@Table(name = "dm_permission_report")
public class ReportPermission extends AbstractPermission {

}
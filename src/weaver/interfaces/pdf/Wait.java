package weaver.interfaces.pdf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.rmi.RemoteException;

import org.apache.axis.encoding.Base64;

import weaver.conn.RecordSet;
import weaver.docs.webservices.DocAttachment;
import weaver.docs.webservices.DocInfo;
import weaver.docs.webservices.DocService;
import weaver.docs.webservices.DocServiceImpl;
import weaver.general.BaseBean;
import weaver.general.Util;
import weaver.workflow.webservices.WorkflowRequestInfo;
import weaver.workflow.webservices.WorkflowService;
import weaver.workflow.webservices.WorkflowServiceImpl;

public class Wait extends BaseBean implements WaitService {

	private String server;
	private String port;
	private String userName;
	private String userPassword;
	private String directory;
	private String requestid;
	private String pdfName;
	private String status;
	private String errorCode;
	private String storePath;
	private String loginname;
	private String loginpwd;
	private String dirid;
	private String message;
	private String PDFDocName;

	public String StatusChange(String serialID, String status, String pdfFileName, String ErrorCode, String RequestID, String PDFDocName) {
		writeLog("接收参数：serialID:"+serialID + " status:" + status + " pdfFileName:" + pdfFileName + " ErrorCode:"+ErrorCode + " RequestID:"+RequestID + " PDFDocName:"+PDFDocName);
		this.server = Util.null2String(getPropValue("html2pdf", "ftpserver"));
		this.port = Util.null2String(getPropValue("html2pdf", "port"));
		this.userName = Util.null2String(getPropValue("html2pdf", "ftpuser"));
		this.userPassword = Util.null2String(getPropValue("html2pdf", "ftppassword"));
		this.directory = Util.null2String(getPropValue("html2pdf", "directory"));
		this.storePath = Util.null2String(getPropValue("html2pdf", "downPath"));
		this.loginname = Util.null2String(getPropValue("html2pdf", "loginname"));// gw
		this.loginpwd = Util.null2String(getPropValue("html2pdf", "loginpwd"));// 1
		this.dirid = Util.null2String(getPropValue("html2pdf", "dirid"));// 1
		this.requestid = RequestID;
		this.pdfName = pdfFileName;
		this.status = status;
		this.errorCode = ErrorCode;
		this.PDFDocName = PDFDocName;
		return execute();
	}

	public String execute() {
		if (!"1".equals(status)) {
			writeLog("{\"status\":\"0\",\"message\":\"接收为失败消息，不处理，errorcode为：" + errorCode + "\"}");
			return "{\"status\":\"0\",\"message\":\"接收为失败消息，不处理，errorcode为：" + errorCode + "\"}";
		}
		
		String xgfj = getXGFJ(requestid);
		String xgfj1 = getXGFJ1(requestid);
		
		FTPUtil util = new FTPUtil(this.server, Integer.parseInt(this.port), this.userName, this.userPassword);
		boolean isLogin = util.login();
		writeLog("登录："+isLogin);
		if (isLogin) {
			String[] pdfNames = pdfName.split("\\\\");
			String[] PDFDocNames = PDFDocName.split("\\\\");
			StringBuffer sb = new StringBuffer(",");
			for(int i = 0; i < pdfNames.length; i++) {
				if(!PDFDocNames[i].equals("")) {
					boolean isDownload = util.downloadFile(this.directory, pdfNames[i], this.storePath);
					writeLog("下载："+isDownload);
					if (isDownload) {
						int docid = createDocFile(PDFDocNames[i], PDFDocNames[i], this.storePath + File.separator + pdfNames[i]);
						writeLog("创建文档："+docid);
						if (docid > 0) {
							sb.append(docid);
							sb.append(",");
						} else {
							return "{\"status\":\"0\",\"message\":\"" + this.message + "\"}";
						}
					} else {
						writeLog("{\"status\":\"0\",\"message\":\"" + util.message + "\"}");
						return "{\"status\":\"0\",\"message\":\"" + util.message + "\"}";
					}
				}
			}
			String sbs = "";
			if(!",".equals(sb.toString())) {
				sbs = sb.toString();
				sbs = sbs.substring(1,sbs.length()-1);
			} else {
				sbs = "";
			}
			if("".equals(xgfj1)) {
				xgfj1 = xgfj1 + sbs;
			} else {
				xgfj1 = xgfj1 + "," + sbs;
			}
			
			writeFileIDOTHER(xgfj1, requestid);
			writeFileIDOTHER1(xgfj, requestid);
		} else {
			writeLog("{\"status\":\"0\",\"message\":\"" + util.message + "\"}");
			return "{\"status\":\"0\",\"message\":\"" + util.message + "\"}";
		}
		RecordSet rs = new RecordSet();
		rs.execute("select id from hrmresource where loginid = '" + this.loginname + "'");
		rs.next();
		String loginid = Util.null2String(rs.getString("id"));
		boolean isSubmit = false;
		try {
			isSubmit = SubmitRequest(Integer.parseInt(this.requestid), Integer.parseInt(loginid));
		} catch (NumberFormatException e) {
			e.printStackTrace();
			writeLog("{\"status\":\"0\",\"message\":\"" + e.getMessage() + "\"}");
			return "{\"status\":\"0\",\"message\":\"" + e.getMessage() + "\"}";
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		writeLog("流程自动提交：" + isSubmit);
		if (!isSubmit) {
			writeLog("{\"status\":\"0\",\"message\":\"流程自动提交失败\"}");
			return "{\"status\":\"0\",\"message\":\"流程自动提交失败\"}";
		}
		writeLog("{\"status\":\"1\",\"message\":\"处理成功\"}");
		return "{\"status\":\"1\",\"message\":\"处理成功\"}";
	}

	/**
	 * 获得流程的详细信息
	 * 
	 * @param requestid
	 * @return
	 * @throws RemoteException
	 */
	public static WorkflowRequestInfo getRequestInfo(int requestid, int userid) throws RemoteException {
		WorkflowService WorkflowServicePortTypeProxy = new WorkflowServiceImpl();
		WorkflowRequestInfo WorkflowRequestInfo = WorkflowServicePortTypeProxy.getWorkflowRequest(requestid, userid, 0);// 调用接口获取对应requestid的数据
		return WorkflowRequestInfo;
	}

	/**
	 * 提交流程
	 * 
	 * @throws RemoteException
	 */
	public boolean SubmitRequest(int requestid, int userid) throws RemoteException {
		WorkflowRequestInfo WorkflowRequestInfo = getRequestInfo(requestid, userid);
		WorkflowService WorkflowServicePortTypeProxy = new WorkflowServiceImpl();
		String str = WorkflowServicePortTypeProxy.submitWorkflowRequest(WorkflowRequestInfo, requestid, userid,
				"submit", "");
		writeLog("流程自动提交：" + str);
		return "success".equals(str);
	}

	public void writeFileID(int docid) {
		RecordSet rs = new RecordSet();
		rs.execute(
				"select formid from workflow_base where id = (select workflowid from workflow_requestbase where REQUESTID = '"
						+ this.requestid + "')");
		writeLog("查找表明SQL: "
				+ "select formid from workflow_base where id = (select workflowid from workflow_requestbase where REQUESTID = '"
				+ this.requestid + "')");
		rs.next();
		String formid = Util.null2String(rs.getString("formid"));
		String tableName = "formtable_main_" + Math.abs(Integer.parseInt(formid));
		String sql = "update " + tableName + " set pdf = '" + docid + "' where requestid = '" + this.requestid + "'";
		writeLog("更新表字段pdf SQL: " + sql);
		rs.execute(sql);
	}

	public int createDocFile(String Subject, String fileName, String path) {
		int docid = 0;
		DocService service = new DocServiceImpl();
		String session;
		try {
			session = service.login(this.loginname, this.loginpwd, 0, "127.0.0.1");
			DocInfo doc = service.getDoc(Integer.parseInt(this.dirid), session);// 44711
			DocAttachment da = doc.getAttachments()[0];

			byte[] content = null;
			// 上传附件，创建文档s
			content = null;
			try {
				int byteread;
				byte data[] = new byte[1024];
				InputStream input = new FileInputStream(new File(path));
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				while ((byteread = input.read(data)) != -1) {
					out.write(data, 0, byteread);
					out.flush();
				}
				content = out.toByteArray();
				input.close();
				out.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			da.setDocid(0);
			da.setImagefileid(0);
			da.setFilename(fileName+".pdf");
			da.setFilecontent(Base64.encode(content));
			da.setIszip(1);

			doc.setId(0);
			doc.setDocSubject(Subject);
			doc.setAttachments(new DocAttachment[] { da });
			docid = service.createDoc(doc, session);
		} catch (RemoteException e1) {
			e1.printStackTrace();
			this.message = "创建文档失败: " + e1.getMessage();
		} catch (Exception e) {
			e.printStackTrace();
			this.message = "创建文档失败: " + e.getMessage();
		}
		return docid;
	}
	
	public String getXGFJ(String requestid) {
		String sbs = "";
		StringBuffer sb = new StringBuffer(",");
		RecordSet rs = new RecordSet();
		rs.execute(
				"select formid from workflow_base where id = (select workflowid from workflow_requestbase where REQUESTID = '"
						+ requestid + "')");
		rs.next();
		String formid = Util.null2String(rs.getString("formid"));
		String tableName = "formtable_main_" + Math.abs(Integer.parseInt(formid));
		String sql = "select xgfj from " + tableName + " where requestid = '" + requestid + "'";
		rs.execute(sql);
		rs.next();
		String xgfj = Util.null2String(rs.getString("xgfj"));
		if(!"".equals(xgfj)) {
			String sql1 = "select a.docid,b.imagefilename,b.filerealpath,b.filesize,b.aescode from DocImageFile a,imagefile b where a.imagefileid = b.imagefileid and a.docid in ("
					+ xgfj + ")";
			rs.execute(sql1);
			while (rs.next()) {
				String docid = Util.null2String(rs.getString("docid"));
				String imagefilename = Util.null2String(rs.getString("imagefilename"));
				String[] strs = imagefilename.split("\\.");
				String type = strs[strs.length - 1];
				if ((imagefilename.contains(".") && type.equals("ceb"))) {
					sb.append(docid);
					sb.append(",");
				} 
			}
			sbs = sb.toString();
			sbs = sbs.substring(1,sbs.length()-1);
		}
		return sbs;
	}
	
	public String getXGFJ1(String requestid) {
		String sbs = "";
		StringBuffer sb = new StringBuffer(",");
		RecordSet rs = new RecordSet();
		rs.execute(
				"select formid from workflow_base where id = (select workflowid from workflow_requestbase where REQUESTID = '"
						+ requestid + "')");
		rs.next();
		String formid = Util.null2String(rs.getString("formid"));
		String tableName = "formtable_main_" + Math.abs(Integer.parseInt(formid));
		String sql = "select xgfj from " + tableName + " where requestid = '" + requestid + "'";
		rs.execute(sql);
		rs.next();
		String xgfj = Util.null2String(rs.getString("xgfj"));
		if(!"".equals(xgfj)) {
			String sql1 = "select a.docid,b.imagefilename,b.filerealpath,b.filesize,b.aescode from DocImageFile a,imagefile b where a.imagefileid = b.imagefileid and a.docid in ("
					+ xgfj + ")";
			rs.execute(sql1);
			while (rs.next()) {
				String docid = Util.null2String(rs.getString("docid"));
				String imagefilename = Util.null2String(rs.getString("imagefilename"));
				String[] strs = imagefilename.split("\\.");
				String type = strs[strs.length - 1];
				if ((imagefilename.contains(".") && type.equals("ceb"))) {
				} else {
					sb.append(docid);
					sb.append(",");
				}
			}
			if(!",".equals(sb.toString())) {
				sbs = sb.toString();
				sbs = sbs.substring(1,sbs.length()-1);
			} else {
				sbs = "";
			}
		}
		return sbs;
	}
	
	public void writeFileIDOTHER(String docid, String requestid) {
		RecordSet rs = new RecordSet();
		rs.execute(
				"select formid from workflow_base where id = (select workflowid from workflow_requestbase where REQUESTID = '"
						+ requestid + "')");
		rs.next();
		String formid = Util.null2String(rs.getString("formid"));
		String tableName = "formtable_main_" + Math.abs(Integer.parseInt(formid));
		String sql = "update " + tableName + " set xgfj = '" + docid + "' where requestid = '" + requestid + "'";
		writeLog("更新表字段xgfj SQL: " + sql);
		rs.execute(sql);
	}
	
	public void writeFileIDOTHER1(String docid, String requestid) {
		RecordSet rs = new RecordSet();
		rs.execute(
				"select formid from workflow_base where id = (select workflowid from workflow_requestbase where REQUESTID = '"
						+ requestid + "')");
		rs.next();
		String formid = Util.null2String(rs.getString("formid"));
		String tableName = "formtable_main_" + Math.abs(Integer.parseInt(formid));
		String sql = "update " + tableName + " set pdf = '" + docid + "' where requestid = '" + requestid + "'";
		writeLog("更新表字段pdf SQL: " + sql);
		rs.execute(sql);
	}
}

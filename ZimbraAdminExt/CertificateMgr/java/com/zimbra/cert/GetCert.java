package com.zimbra.cert;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.rmgmt.RemoteManager;
import com.zimbra.cs.rmgmt.RemoteResult;
import com.zimbra.cs.rmgmt.RemoteResultParser;
import com.zimbra.cs.service.admin.AdminDocumentHandler;
import com.zimbra.soap.ZimbraSoapContext;


public class GetCert extends AdminDocumentHandler {
    final static String CERT_TYPE_MAILBOX = "mailbox" ;
    final static String CERT_TYPE_SERVER = "server" ;
    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException{
        ZimbraSoapContext lc = getZimbraSoapContext(context);
        Provisioning prov = Provisioning.getInstance();
        try {
            //get a server
            List<Server> serverList =  prov.getAllServers();
            Server server = ZimbraCertMgrExt.getCertServer(serverList);
            
            if (server == null) {
                throw ServiceException.INVALID_REQUEST("No valid server was found", null);
            }
            String certType = request.getAttribute("certtype");
            RemoteManager rmgr = RemoteManager.getRemoteManager(server);
            Element response = lc.createElement(ZimbraCertMgrService.GET_CERT_RESPONSE);
            if (certType == null || certType.length() == 0 || certType.equals("all")) {
                addCertInfo(response, rmgr.execute(ZimbraCertMgrExt.GET_CERT_CMD + " " + CERT_TYPE_MAILBOX), CERT_TYPE_MAILBOX) ;
                addCertInfo(response, rmgr.execute(ZimbraCertMgrExt.GET_CERT_CMD + " " + CERT_TYPE_SERVER), CERT_TYPE_SERVER) ;
            }else if (certType.equals(CERT_TYPE_MAILBOX)) {
                addCertInfo(response, rmgr.execute(ZimbraCertMgrExt.GET_CERT_CMD + " " + CERT_TYPE_MAILBOX), CERT_TYPE_MAILBOX) ;
            }else if (certType.equals(CERT_TYPE_SERVER)) {
                addCertInfo(response, rmgr.execute(ZimbraCertMgrExt.GET_CERT_CMD + " " + CERT_TYPE_SERVER), CERT_TYPE_SERVER) ; 
            }
            return response;
        }catch (IOException ioe) {
            throw ServiceException.FAILURE("exception occurred handling command", ioe);
        }
    }
    
    public void addCertInfo(Element parent, RemoteResult rr, String certType) throws ServiceException, IOException{
        Element el = parent.addElement(certType);
        byte[] stdOut = rr.getMStdout() ;
        //String out = new String (stdOut) ;
        //el.addText(out) ;
        
        HashMap <String, String> output = ZimbraCertMgrExt.parseOuput(stdOut) ;
        for (String k: output.keySet()) {
            System.out.println("Adding attribute " + k + " = " + output.get(k)) ;
            el.addAttribute(k, output.get(k));
        }
    }
}

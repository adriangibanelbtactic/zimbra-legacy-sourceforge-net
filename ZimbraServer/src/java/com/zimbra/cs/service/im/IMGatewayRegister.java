package com.zimbra.cs.service.im;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.IMConstants;
import com.zimbra.cs.im.IMPersona;
import com.zimbra.cs.im.interop.Interop.ServiceName;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;

public class IMGatewayRegister extends IMDocumentHandler 
{
    @Override
    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext zsc = getZimbraSoapContext(context);
        OperationContext octxt = getOperationContext(zsc, context);
        
        Element response = zsc.createElement(IMConstants.IM_GATEWAY_REGISTER_RESPONSE);
        Object lock = super.getLock(zsc);
        
        String op = request.getAttribute("op");
        String serviceStr = request.getAttribute("service");
        boolean result = true;
        if ("reg".equals(op)) {
            String nameStr = request.getAttribute("name");
            String pwStr = request.getAttribute("password");
            result = register((Mailbox) lock, octxt, ServiceName.valueOf(serviceStr), nameStr, pwStr);
        } else if ("reconnect".equals(op)) {
            reconnect((Mailbox) lock, octxt, ServiceName.valueOf(serviceStr));
        } else {
            unregister((Mailbox) lock, octxt, ServiceName.valueOf(serviceStr));
        }
        response.addAttribute("result", result);
        
        return response;
    }
    
    public static boolean register(Mailbox mbox, OperationContext octxt, ServiceName service, 
        String name, String password) throws ServiceException {
        synchronized (mbox) {
            IMPersona persona = getRequestedPersona(octxt, mbox);
            persona.gatewayRegister(service, name, password);
            return true;
        }
    }
    
    public static void unregister(Mailbox mbox, OperationContext octxt, ServiceName service) throws ServiceException {
        synchronized (mbox) {
            IMPersona persona = getRequestedPersona(octxt, mbox);
            persona.gatewayUnRegister(service);
        }
    }
    
    public static boolean reconnect(Mailbox mbox, OperationContext octxt, ServiceName service) throws ServiceException {
        synchronized (mbox) {
            IMPersona persona = getRequestedPersona(octxt, mbox);
            persona.gatewayReconnect(service);
            return true;
        }
    }
    
}

/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1
 * 
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 ("License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.zimbra.com/license
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 * 
 * The Original Code is: Zimbra Collaboration Suite Server.
 * 
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2006, 2007 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */
package org.jivesoftware.wildfire.commands.admin.group;

import org.dom4j.Element;
import org.jivesoftware.wildfire.commands.AdHocCommand;
import org.jivesoftware.wildfire.commands.SessionData;
import org.jivesoftware.wildfire.group.Group;
import org.jivesoftware.wildfire.group.GroupManager;
import org.jivesoftware.wildfire.group.GroupNotFoundException;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command that allows to retrieve list members of a given group.
 *
 * @author Gaston Dombiak
 *
 * TODO Use i18n
 */
public class GetListGroupUsers extends AdHocCommand {
    protected void addStageInformation(SessionData data, Element command) {
        DataForm form = new DataForm(DataForm.Type.form);
        form.setTitle("Requesting List of Group Members");
        form.addInstruction("Fill out this form to request list of group members and admins.");

        FormField field = form.addField();
        field.setType(FormField.Type.hidden);
        field.setVariable("FORM_TYPE");
        field.addValue("http://jabber.org/protocol/admin");

        field = form.addField();
        field.setType(FormField.Type.text_single);
        field.setLabel("Group Name");
        field.setVariable("group");
        field.setRequired(true);

        // Add the form to the command
        command.add(form.getElement());
    }

    public void execute(SessionData data, Element command) {
        Group group;
        try {
            group = GroupManager.getInstance().getGroup(data.getData().get("group").get(0));
        } catch (GroupNotFoundException e) {
            // Group not found
            Element note = command.addElement("note");
            note.addAttribute("type", "error");
            note.setText("Group name does not exist");
            return;
        }

        DataForm form = new DataForm(DataForm.Type.result);

        form.addReportedField("jid", "User", FormField.Type.jid_single);
        form.addReportedField("admin", "Description", FormField.Type.boolean_type);

        // Add group members the result
        for (JID memberJID : group.getMembers()) {
            Map<String,Object> fields = new HashMap<String,Object>();
            fields.put("jid", memberJID.toString());
            fields.put("admin", false);
            form.addItemFields(fields);
        }
        // Add group admins the result
        for (JID memberJID : group.getAdmins()) {
            Map<String,Object> fields = new HashMap<String,Object>();
            fields.put("jid", memberJID.toString());
            fields.put("admin", true);
            form.addItemFields(fields);
        }
        command.add(form.getElement());
    }

    public String getCode() {
        return "http://jabber.org/protocol/admin#get-group-members";
    }

    public String getDefaultLabel() {
        return "Get List of Group Members";
    }

    protected List<Action> getActions(SessionData data) {
        return Arrays.asList(AdHocCommand.Action.complete);
    }

    protected AdHocCommand.Action getExecuteAction(SessionData data) {
        return AdHocCommand.Action.complete;
    }

    public int getMaxStages(SessionData data) {
        return 1;
    }
}

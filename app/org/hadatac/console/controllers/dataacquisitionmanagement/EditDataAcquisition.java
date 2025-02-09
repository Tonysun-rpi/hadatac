package org.hadatac.console.controllers.dataacquisitionmanagement;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import play.twirl.api.Html;
import play.data.FormFactory;

import org.hadatac.console.controllers.AuthApplication;
import org.hadatac.console.controllers.dataacquisitionmanagement.routes;
import org.hadatac.console.models.DataAcquisitionForm;
import org.hadatac.console.models.SysUser;
import org.hadatac.console.views.html.*;
import org.hadatac.console.views.html.dataacquisitionmanagement.*;
import org.hadatac.entity.pojo.ObjectAccessSpec;
import org.hadatac.entity.pojo.DataAcquisitionSchema;
import org.hadatac.entity.pojo.TriggeringEvent;
import org.hadatac.entity.pojo.User;
import org.hadatac.entity.pojo.UserGroup;
import org.hadatac.metadata.loader.URIUtils;
import org.hadatac.utils.ConfigProp;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.labkey.remoteapi.CommandException;

import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;

public class EditDataAcquisition extends Controller {

    @Inject
    private FormFactory formFactory;

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result index(String dir, String filename, String uri, boolean bChangeParam) {
        if (ConfigProp.getLabKeyLoginRequired() && 
                (session().get("LabKeyUserName") == null || session().get("LabKeyPassword") == null)
                && bChangeParam) {
            return redirect(org.hadatac.console.controllers.triplestore.routes.LoadKB.logInLabkey(
                    routes.EditDataAcquisition.index(dir, filename, uri, bChangeParam).url()));
        }

        final SysUser sysUser = AuthApplication.getLocalUser(session());
        try {
            if (uri != null) {
                uri = URLDecoder.decode(uri, "UTF-8");
            } else {
                uri = "";
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        if (!uri.equals("")) {
            ObjectAccessSpec dataAcquisition = ObjectAccessSpec.findByUri(uri);
            if (null == dataAcquisition) {
                return badRequest("Invalid data acquisition URI!");
            }

            Map<String, String> nameList = new HashMap<String, String>();
            User user = User.find(dataAcquisition.getOwnerUri());
            if(null != user){
                if(user.getUri() != dataAcquisition.getPermissionUri()){
                    nameList.put(user.getUri(), user.getName());
                }
                List<User> groups = UserGroup.find();
                for (User group : groups) {
                    nameList.put(group.getUri(), group.getName());
                }
            }

            Map<String, String> mapSchemas = new HashMap<String, String>();
            List<DataAcquisitionSchema> schemas = DataAcquisitionSchema.findAll();
            for (DataAcquisitionSchema schema : schemas) {
                mapSchemas.put(schema.getUri(), URIUtils.replaceNameSpaceEx(schema.getUri()));
            }

            return ok(editDataAcquisition.render(dir, filename, dataAcquisition, nameList, 
                    User.getUserURIs(), mapSchemas, sysUser.isDataManager(), bChangeParam));
        }

        return badRequest("Invalid data acquisition URI!");
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result postIndex(String dir, String filename, String uri, boolean bChangeParam) {
        return index(dir, filename, uri, bChangeParam);
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result processForm(String dir, String filename, String acquisitionUri, boolean bChangeParam) {
        final SysUser sysUser = AuthApplication.getLocalUser(session());

        Form<DataAcquisitionForm> form = formFactory.form(DataAcquisitionForm.class).bindFromRequest();
        DataAcquisitionForm data = form.get();
        List<String> changedInfos = new ArrayList<String>();

        if (form.hasErrors()) {
            return badRequest("The submitted form has errors!");
        }

        ObjectAccessSpec da = ObjectAccessSpec.findByUri(acquisitionUri);
        if (null != data.getNewDataAcquisitionUri()) {
            if (!data.getNewDataAcquisitionUri().equals("")) {
                if (null != ObjectAccessSpec.findByUri(data.getNewDataAcquisitionUri())) {
                    return badRequest("Data acquisition with this uri already exists!");
                }

                // Create new data acquisition
                da.setUri(data.getNewDataAcquisitionUri());
                da.setNumberDataPoints(0);
                da.setTriggeringEvent(TriggeringEvent.CHANGED_CONFIGURATION);
                da.setParameter(data.getNewParameter());

                DateTimeFormatter isoFormat = DateTimeFormat.forPattern("MM/dd/yyyy HH:mm a");
                da.setStartedAt(isoFormat.parseDateTime(data.getNewStartDate()));

                if (!data.getNewEndDate().equals("")) {
                    isoFormat = DateTimeFormat.forPattern("MM/dd/yyyy HH:mm a");
                    da.setEndedAt(isoFormat.parseDateTime(data.getNewEndDate()));
                }  

                da.saveToSolr();
                int nRowsAffected = 0;
                if (ConfigProp.getLabKeyLoginRequired()) {
                    nRowsAffected = da.saveToLabKey(session().get("LabKeyUserName"), session().get("LabKeyPassword"));
                }

                return ok(main.render("Results,", "", new Html("<h3>" 
                        + String.format("%d row(s) have been inserted in Table \"DataAcquisition\"", nRowsAffected) 
                        + "</h3>")));
            }
        }

        // Update current data acquisition
        if (bChangeParam) {
            if (da.getParameter() == null || !da.getParameter().equals(data.getNewParameter())) {
                da.setParameter(data.getNewParameter());
                changedInfos.add(data.getNewParameter());
            }
        }
        else {
            if (sysUser.isDataManager()) {
                if (da.getOwnerUri() == null || !da.getOwnerUri().equals(data.getNewOwner())) {
                    da.setOwnerUri(data.getNewOwner());
                    changedInfos.add(data.getNewOwner());
                }
            }
            if (da.getPermissionUri() == null || !da.getPermissionUri().equals(data.getNewPermission())) {
                da.setPermissionUri(data.getNewPermission());
                changedInfos.add(data.getNewPermission());
            }
            if (da.getSchemaUri() == null || !da.getSchemaUri().equals(data.getNewSchema())) {
                da.setSchemaUri(data.getNewSchema());
                changedInfos.add(data.getNewSchema());
            }
        }

        if (!changedInfos.isEmpty()) {
            da.save();
        }

        return ok(editDataAcquisitionConfirm.render(dir, filename, da, changedInfos, sysUser.isDataManager()));
    }
}

package org.hadatac.console.controllers.metadata.empirical;

import java.util.List;

import org.hadatac.entity.pojo.Platform;

import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import be.objectify.deadbolt.java.actions.SubjectPresent;

import org.hadatac.console.controllers.AuthApplication;
import org.hadatac.console.views.html.metadata.empirical.*;
import play.mvc.Result;
import play.mvc.Controller;

public class PlatformManagement extends Controller {

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result index(String dir, String filename, String da_uri) {
    	List<Platform> platforms = Platform.find();
    	//System.out.println("Number of platforms: " + platforms.size());
        return ok(platformManagement.render(dir, filename, da_uri, platforms));
    }
    
    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
	public Result postIndex(String dir, String filename, String da_uri) {
        return index(dir, filename, da_uri);
    }

    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
    public Result viewPlatform(String dir, String filename, String da_uri, String platform_uri) {
    	try {
    	    platform_uri = java.net.URLDecoder.decode(platform_uri,"UTF8");
    	} catch (Exception e) {
    	}    	
    	System.out.println("platform URI: [" + platform_uri + "]");
    	Platform platform = Platform.find(platform_uri);
        return ok(viewPlatform.render(dir, filename, da_uri, platform));
    }
    
    @Restrict(@Group(AuthApplication.DATA_OWNER_ROLE))
	public Result postViewPlatform(String dir, String filename, String da_uri, String platform_uri) {
        return viewPlatform(dir, filename, da_uri, platform_uri);
    }

}
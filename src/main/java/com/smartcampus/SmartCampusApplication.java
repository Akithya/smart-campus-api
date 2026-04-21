package com.smartcampus;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/api/v1")
public class SmartCampusApplication extends Application {
    // Jersey auto-discovers all resource classes in the package.
    // No manual registration needed when using ResourceConfig.packages() in Main.
}

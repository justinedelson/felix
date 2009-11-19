package org.apache.felix.webconsole.internal.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.felix.webconsole.ConfigurationPrinter;
import org.apache.felix.webconsole.internal.BaseWebConsolePlugin;
import org.apache.felix.webconsole.internal.Logger;
import org.apache.felix.webconsole.internal.Util;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.log.LogService;

public class ServicesServlet extends BaseWebConsolePlugin implements
        ConfigurationPrinter {

    public void activate(BundleContext bundleContext) {
        super.activate(bundleContext);
        configurationPrinter = bundleContext.registerService(
                ConfigurationPrinter.SERVICE, this, null);
    }

    private ServiceRegistration configurationPrinter;

    public void deactivate() {
        if (configurationPrinter != null) {
            configurationPrinter.unregister();
            configurationPrinter = null;
        }

        super.deactivate();
    }

    public static final String LABEL = "services";

    public static final String TITLE = "Services";

    public String getLabel() {
        return LABEL;
    }

    public String getTitle() {
        return TITLE;
    }

    protected void renderContent(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        // TODO Auto-generated method stub

    }

    private void appendServiceInfoCount(final StringBuffer buf, String msg,
            int count) {
        buf.append(count);
        buf.append(" service");
        if (count != 1)
            buf.append('s');
        buf.append(' ');
        buf.append(msg);
    }

    private String getStatusLine(final ServiceReference[] services) {
        final StringBuffer buffer = new StringBuffer();
        buffer.append("Services information: ");
        appendServiceInfoCount(buffer, "in total", services.length);
        return buffer.toString();
    }

    private void writeJSON(final Writer pw, final ServiceReference service,
            final String pluginRoot, final boolean fullDetails)
            throws IOException {
        final ServiceReference[] allServices = this.getServices();
        final String statusLine = this.getStatusLine(allServices);
        final ServiceReference[] services = (service != null) ? new ServiceReference[] { service }
                : allServices;
        Util.sort(services);

        final JSONWriter jw = new JSONWriter(pw);

        try {
            jw.object();

            jw.key("status");
            jw.value(statusLine);

            jw.key("data");

            jw.array();

            for (int i = 0; i < services.length; i++) {
                serviceInfo(jw, services[i], fullDetails || service != null,
                        pluginRoot);
            }

            jw.endArray();

            jw.endObject();

        } catch (JSONException je) {
            throw new IOException(je.toString());
        }

    }

    private void serviceInfo(JSONWriter jw, ServiceReference service,
            boolean details, final String pluginRoot) throws JSONException {
        jw.object();
        jw.key("id");
        jw.value(propertyAsString(service, Constants.SERVICE_ID));
        jw.key("types");
        jw.value(propertyAsString(service, Constants.OBJECTCLASS));
        jw.key("pid");
        jw.value(propertyAsString(service, Constants.SERVICE_PID));

        Bundle bundle = service.getBundle();

        jw.key("bundleId");
        jw.value(bundle.getBundleId());
        jw.key("bundleName");
        jw.value(Util.getName(bundle));
        jw.key("bundleVersion");
        jw.value(Util.getHeaderValue(bundle, Constants.BUNDLE_VERSION));
        jw.key("bundleSymbolicName");
        jw.value(Util.getHeaderValue(bundle, Constants.BUNDLE_SYMBOLICNAME));

        jw.key("actions");
        jw.array();

        jw.endArray();

        if (details) {
            serviceDetails(jw, service, pluginRoot);
        }

        jw.endObject();
    }
    

    private void keyVal( JSONWriter jw, String key, Object value ) throws JSONException
    {
        if ( key != null && value != null )
        {
            jw.object();
            jw.key( "key" );
            jw.value( key );
            jw.key( "value" );
            jw.value( value );
            jw.endObject();
        }
    }

    private void serviceDetails(JSONWriter jw, ServiceReference service,
            String pluginRoot) throws JSONException {
        String[] keys = service.getPropertyKeys();

        jw.key("props");
        jw.array();
        
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            keyVal(jw, key, service.getProperty(key));
            
        }
       
        jw.endArray();

    }

    private String propertyAsString(ServiceReference ref, String name) {
        Object value = ref.getProperty(name);
        if (value instanceof Object[]) {
            StringBuffer dest = new StringBuffer();
            Object[] values = (Object[]) value;
            for (int j = 0; j < values.length; j++) {
                if (j > 0)
                    dest.append(", ");
                dest.append(values[j]);
            }
            return dest.toString();
        } else if (value != null) {
            return value.toString();
        } else {
            return "n/a";
        }
    }

    private ServiceReference[] getServices() {
        try {
            return getBundleContext().getServiceReferences(null, null);
        } catch (InvalidSyntaxException e) {
            getLog().log(LogService.LOG_WARNING,
                    "Unable to access service reference list.", e);
            return new ServiceReference[0];
        }
    }

    public void printConfiguration(PrintWriter pw) {
        try {
            StringWriter w = new StringWriter();
            writeJSON(w, null, null, true);
            String jsonString = w.toString();
            JSONObject json = new JSONObject(jsonString);

            pw.println("Status: " + json.get("status"));
            pw.println();

            JSONArray data = json.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                if (!data.isNull(i)) {
                    JSONObject service = data.getJSONObject(i);

                    pw.println(MessageFormat.format(
                            "Service {0} - {1} (pid: {2})", new Object[] {
                                    service.get("id"), service.get("types"),
                                    service.get("pid") }));

                    JSONArray props = service.getJSONArray("props");
                    for (int pi = 0; pi < props.length(); pi++) {
                        if (!props.isNull(pi)) {
                            JSONObject entry = props.getJSONObject(pi);

                            pw.print("    " + entry.get("key") + ": ");

                            Object entryValue = entry.get("value");
                            if (entryValue instanceof JSONArray) {
                                pw.println();
                                JSONArray entryArray = (JSONArray) entryValue;
                                for (int ei = 0; ei < entryArray.length(); ei++) {
                                    if (!entryArray.isNull(ei)) {
                                        pw.println("        "
                                                + entryArray.get(ei));
                                    }
                                }
                            } else {
                                pw.println(entryValue);
                            }
                        }
                    }

                    pw.println();
                }
            }
        } catch (Exception e) {
            getLog()
                    .log(
                            LogService.LOG_ERROR,
                            "Problem rendering Bundle details for configuration status",
                            e);
        }
    }

}

/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.archive.crawler.restlet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang.StringEscapeUtils;
import org.archive.crawler.framework.CrawlJob;
import org.archive.crawler.framework.Engine;
import org.archive.spring.ConfigPath;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;
import org.restlet.Context;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.restlet.resource.WriterRepresentation;

/**
 * Restlet Resource representing a single local CrawlJob inside an
 * Engine.
 * 
 * @contributor gojomo
 */
public class JobResource extends Resource {
    public static final IOFileFilter EDIT_FILTER = 
        FileUtils.getRegexFileFilter(".*\\.((c?xml)|(txt))$");

    CrawlJob cj; 
    
    public JobResource(Context ctx, Request req, Response res) throws ResourceException {
        super(ctx, req, res);
        setModifiable(true);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        cj = getEngine().getJob((String)req.getAttributes().get("job"));
        if(cj==null) {
            throw new ResourceException(404);
        }
    }

    public Representation represent(Variant variant) throws ResourceException {
        Representation representation = new WriterRepresentation(
                MediaType.TEXT_HTML) {
            public void write(Writer writer) throws IOException {
                JobResource.this.writeHtml(writer);
            }
        };
        // TODO: remove if not necessary in future?
        representation.setCharacterSet(CharacterSet.UTF_8);
        return representation;
    }

    protected void writeHtml(Writer writer) {
        PrintWriter pw = new PrintWriter(writer); 
        String jobTitle = "Job "+cj.getShortName();
        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if(!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        // TODO: replace with use a templating system (FreeMarker?)
        pw.println("<head><title>"+jobTitle+"</title>");
        pw.println("<base href='"+baseRef+"'/>");
        pw.println("</head><body>");
        pw.print("<h1>Job <i>"+cj.getShortName()+"</i> (");
        
        pw.print(cj.getLaunchCount() + " launches");
        if(cj.getLastLaunch()!=null) {
            long ago = System.currentTimeMillis() - cj.getLastLaunch().getMillis();
            pw.println(", last "+ArchiveUtils.formatMillisecondsToConventional(ago, 2)+" ago)");
        }
        pw.println(")</h1>");
        
        
        // button controls
        pw.println("<form method='POST'>");
        // PREP, LAUNCH
        pw.print("<input style='width:6em' type='submit' name='action' value='build' ");
        pw.print(cj.isContainerValidated()?"disabled='disabled' title='build job'":"");
        pw.println("/>");
        pw.print("<input style='width:6em' type='submit' name='action' value='launch'");
        if(cj.isProfile()) {
            pw.print("disabled='disabled' title='profiles cannot be launched'");
        }
        if(!cj.isLaunchable()) {
            pw.print("disabled='disabled' title='launched OK'");
        }
        pw.println("/> - ");
        
        // PAUSE, UNPAUSE, CHECKPOINT
        pw.println("<input  style=\'width:6em\'");
        if(!cj.isPausable()) {
            pw.println(" disabled ");
        }
        pw.println(" type='submit' name='action' value='pause'/>");
        pw.println("<input style=\'width:6em\'");
        if(!cj.isUnpausable()) {
            pw.println(" disabled ");
        }
        pw.println(" type='submit' name='action' value='unpause'/>");
        pw.println("<input style='width:6em'");
        if(true /*!cj.isUnpausable()*/) { // TODO: not yet implemented
            pw.println(" disabled ");
        }
        pw.println(" type='submit' name='action' value='checkpoint'/> - ");

        
        // TERMINATE, RESET
        pw.println("<input style='width:6em' ");
        if(!cj.isRunning()) {
            pw.println(" disabled ");
        }
        pw.println(" type='submit' name='action' value='terminate'/>");
        pw.println("<input style='width:6em' type='submit' name='action' value='discard' ");
        pw.print(cj.isContainerOk()?"":"disabled='disabled' title='no instance'");
        pw.println("/><br/>");

        pw.println("</form>");
        
        // configuration 
        pw.println("configuration: ");
        printLinkedIfInJobDirectory(pw, cj.getPrimaryConfig());
        for(File f : cj.getImportedConfigs(cj.getPrimaryConfig())) {
            pw.println("imported: ");
            printLinkedIfInJobDirectory(pw,f);
        }
        
//        if(cj.isXmlOk()) {
//            pw.println("cxml ok<br/>");
//            if(cj.isContainerOk()) {
//                pw.println("container ok<br/>");
//                if(cj.isContainerValidated()) {
//                    pw.println("config valid<br/>");
//                } else {
//                    pw.println("CONFIG INVALID<br/>");
//                }
//            } else {
//                pw.println("CONTAINER BAD<br/>");
//            }
//        }else {
//            // pw.println("XML NOT WELL-FORMED<br/>");
//        }

        pw.println("<h2>Job Log ");
        pw.println("(<a href='jobdir/"
                +cj.getJobLog().getName()
                +"?format=paged&pos=-1&lines=-128&reverse=y'><i>more</i></a>)");
        pw.println("</h2>");
        pw.println("<div style='font-family:monospace; white-space:pre-wrap; white-space:normal; text-indent:-10px; padding-left:10px;'>");
        if(cj.getJobLog().exists()) {
            try {
                List<String> logLines = new LinkedList<String>();
                FileUtils.pagedLines(cj.getJobLog(), -1, -5, logLines);
                Collections.reverse(logLines);
                for(String line : logLines) {
                    pw.print("<p style='margin:0px'>");
                    StringEscapeUtils.escapeHtml(pw,line);
                    pw.print("</p>");
                }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe); 
            }
        }
        pw.println("</div>");
        
       
        if(!cj.isContainerOk()) {
            pw.println("<h2>Unbuilt Job</h2>");
        } else if(cj.isRunning()) {
            pw.println("<h2>Active Job: "+cj.getCrawlController().getState()+"</h2>");
        } else if(cj.isLaunchable()){
            pw.println("<h2>Ready Job</h2>");
        } else {
            pw.println("<h2>Finished Job: "+cj.getCrawlController().getCrawlExitStatus()+"</h2>");
        }

        if(cj.isContainerOk()) {
            pw.println("<b>Totals</b><br/>&nbsp;&nbsp;");
            pw.println(cj.uriTotalsReport());
            pw.println("<br/>&nbsp;&nbsp;");
            pw.println(cj.sizeTotalsReport());
                        
            pw.println("<br/><b>Alerts</b><br>&nbsp;&nbsp;");
            pw.println(cj.getAlertCount()==0 ? "<i>none</i>" : cj.getAlertCount()); 
            if(cj.getAlertCount()>0) {
                pw.println("<a href='jobdir"
                        +cj.jobDirRelativePath(
                                cj.getCrawlController().getLoggerModule().getAlertsLogPath().getFile())
                        +"?format=paged&pos=-1&lines=-128'>tail alert log...</a>");
            }
            
            pw.println("<br/><b>Rates</b><br/>&nbsp;&nbsp;");
            pw.println(cj.rateReport());
            
            pw.println("<br/><b>Load</b><br/>&nbsp;&nbsp;");
            pw.println(cj.loadReport());
            
            pw.println("<br/><b>Elapsed</b><br/>&nbsp;&nbsp;");
            pw.println(cj.elapsedReport());
            
            pw.println("<br/><b>Threads</b><br/>&nbsp;&nbsp;");
            pw.println(cj.threadReport());
    
            pw.println("<br/><b>Frontier</b><br/>&nbsp;&nbsp;");
            pw.println(cj.frontierReport());
            
            pw.println("<br/><b>Memory</b><br/>&nbsp;&nbsp;");
            pw.println(getEngine().heapReport());
            
            if(cj.isRunning() || (cj.isContainerOk() && !cj.isLaunchable())) {
                // show crawl log for running or finished crawls
                pw.println("<h3>Crawl Log");
                pw.println("(<a href='jobdir"
                        +cj.jobDirRelativePath(
                                cj.getCrawlController().getLoggerModule().getCrawlLogPath().getFile())
                        +"?format=paged&pos=-1&lines=-128&reverse=y'><i>more</i></a>)");
                pw.println("</h3>");
                pw.println("<pre style='overflow:auto'>");
                try {
                    List<String> logLines = new LinkedList<String>();
                    FileUtils.pagedLines(
                            cj.getCrawlController().getLoggerModule().getCrawlLogPath().getFile(),
                            -1, 
                            -10, 
                            logLines);
                    Collections.reverse(logLines);
                    for(String line : logLines) {
                        StringEscapeUtils.escapeHtml(pw,line);
                        pw.println();
                    }
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe); 
                }
                pw.println("</pre>");
            }
            
        }
        
        pw.println("<h2>Files</h2>");
        pw.println("<h3>Browse <a href='jobdir'>Job Directory</a></h3>");
        // specific paths from wired context
        pw.println("<h3>Configuration-referenced Paths</h3>");
        if(cj.getConfigPaths().isEmpty()) {
            pw.println("<i>build the job to discover referenced paths</i>");
        } else {
            pw.println("<dl>");
            for(ConfigPath cp : cj.getConfigPaths().values()) {
                pw.println("<dt>"+cp.getName()+"</dt>");
                pw.println("<dd>");
                printLinkedIfInJobDirectory(pw, cp.getFile());
                pw.println("</dd>");
            }
        }
        pw.println("</dl>");
        pw.println("<hr/>");
        pw.println(
            "<form method='POST'>Copy job to <input name='copyTo'/>" +
            "<input type='submit'/>" +
            "<input id='asProfile' type='checkbox' name='asProfile'/>" +
            "<label for='asProfile'>as profile</label></form>");
        pw.println("<hr/>");
        pw.close();
    }

    /**
     * Print the given File path, but only provide view/edit link if
     * path is within job directory.
     * 
     * @param pw PrintWriter
     * @param f File
     */
    protected void printLinkedIfInJobDirectory(PrintWriter pw, File f) {
        String jobDirRelative = cj.jobDirRelativePath(f);
        if(jobDirRelative==null) {
            pw.println(f);
            return;
        }
        pw.println("<a href='jobdir" 
                + jobDirRelative + "'>" 
                + f +"</a>");
        if(EDIT_FILTER.accept(f)) {
            pw.println("[<a href='jobdir" 
                    + jobDirRelative 
                    +  "?format=textedit'>edit</a>]<br/>");
        }
    }

    protected Engine getEngine() {
        return ((EngineApplication)getApplication()).getEngine();
    }

    @Override
    public void acceptRepresentation(Representation entity) throws ResourceException {
        // copy op?
        Form form = getRequest().getEntityAsForm();
        String copyTo = form.getFirstValue("copyTo");
        if(copyTo!=null) {
            copyJob(copyTo,"on".equals(form.getFirstValue("asProfile")));
            return;
        }
        String action = form.getFirstValue("action");
        if("launch".equals(action)) {
            cj.launch(); 
        } else if("checkXML".equals(action)) {
            cj.checkXML();
        } else if("instantiate".equals(action)) {
            cj.instantiateContainer();
        } else if("build".equals(action)||"validate".equals(action)) {
            cj.validateConfiguration();
        } else if("discard".equals(action)) {
            cj.reset(); 
        } else if("pause".equals(action)) {
            cj.getCrawlController().requestCrawlPause();
        } else if("unpause".equals(action)) {
            cj.getCrawlController().requestCrawlResume();
        } else if("terminate".equals(action)) {
            cj.terminate();
        }
        // default: redirect to GET self
        getResponse().redirectSeeOther(getRequest().getOriginalRef());
    }

    protected void copyJob(String copyTo, boolean asProfile) throws ResourceException {
        try {
            getEngine().copy(cj, copyTo, asProfile);
        } catch (IOException e) {
            throw new ResourceException(Status.CLIENT_ERROR_CONFLICT,e);
        }
        // redirect to destination job page
        getResponse().redirectSeeOther(copyTo);
    }
    
    
}

package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.comparator.NameFileComparator;
import org.apache.commons.lang3.StringUtils;
import org.goobi.beans.Batch;
import org.goobi.beans.JournalEntry;
import org.goobi.beans.JournalEntry.EntryType;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IPushPlugin;
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;
import org.omnifaces.cdi.PushContext;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.BeanHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.HelperSchritte;
import de.sub.goobi.helper.NIOFileUtils;
import de.sub.goobi.helper.ScriptThreadWithoutHibernate;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.persistence.managers.JournalManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import io.goobi.workflow.importer.model.LogMessage;
import io.goobi.workflow.importer.model.SimpleContent;
import io.goobi.workflow.importer.model.SimpleCorporate;
import io.goobi.workflow.importer.model.SimpleData;
import io.goobi.workflow.importer.model.SimpleGroup;
import io.goobi.workflow.importer.model.SimpleImportObject;
import io.goobi.workflow.importer.model.SimpleJournalEntry;
import io.goobi.workflow.importer.model.SimpleMetadata;
import io.goobi.workflow.importer.model.SimplePerson;
import io.goobi.workflow.importer.model.SimpleProperty;
import io.goobi.workflow.importer.model.WuWmaImportState;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.ContentFile;
import ugh.dl.Corporate;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.MetadataGroupType;
import ugh.dl.MetadataType;
import ugh.dl.NamePart;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.TypeNotAllowedForParentException;
import ugh.fileformats.mets.MetsMods;

@PluginImplementation
@Log4j2
public class WuWmaImportWorkflowPlugin implements IWorkflowPlugin, IPushPlugin, IWuWmaImportWorkflowPlugin {

    private static final long serialVersionUID = 7807903481239517297L;

    @Getter
    private String id = "intranda_workflow_wu_wma_import";

    @Getter
    private String title = "intranda_workflow_wu_wma_import";

    private boolean cleanup = false;
    private boolean assignPages = true;
    @Getter
    private transient List<IImportSet> importSets;
    private PushContext pusher;

    /**
     * Live import state. Held in the HttpSession (via a lib-module type) rather than in instance or
     * static fields, because Goobi (re)loads this base plugin in a throwaway classloader on every
     * page entry - see {@link WuWmaImportState} for the full reasoning. Resolved lazily so the
     * constructor (which also runs during plugin discovery without a FacesContext) does not need a
     * request. The reference is cached per instance and captured by the import worker thread.
     */
    private transient volatile WuWmaImportState state;

    /**
     * Get the session-scoped import state, resolving and caching it on first use. The get-or-create
     * is synchronized on the session so that concurrent requests of the same user (e.g. two tabs)
     * always end up with the one and only state instance stored in the session. Falls back to a
     * transient, non-shared state when no FacesContext/HttpSession is available (e.g. during plugin
     * discovery), so getters never throw.
     */
    private WuWmaImportState getState() {
        WuWmaImportState result = state;
        if (result == null) {
            FacesContext fc = FacesContext.getCurrentInstance();
            if (fc != null && fc.getExternalContext() != null) {
                HttpSession session = (HttpSession) fc.getExternalContext().getSession(true);
                if (session != null) {
                    synchronized (session) {
                        result = (WuWmaImportState) session.getAttribute(WuWmaImportState.SESSION_ATTRIBUTE);
                        if (result == null) {
                            result = new WuWmaImportState();
                            session.setAttribute(WuWmaImportState.SESSION_ATTRIBUTE, result);
                        }
                    }
                }
            }
            if (result == null) {
                result = new WuWmaImportState();
            }
            state = result;
        }
        return result;
    }

    public boolean isRun() {
        return getState().isRun();
    }

    public int getProgress() {
        return getState().getProgress();
    }

    public int getItemCurrent() {
        return getState().getItemCurrent();
    }

    public int getItemsTotal() {
        return getState().getItemsTotal();
    }

    public int getErrors() {
        return getState().getErrors();
    }

    public List<LogMessage> getLogQueue() {
        return getState().getLogMessages();
    }

    @Override
    public PluginType getType() {
        return PluginType.Workflow;
    }

    @Override
    public String getGui() {
        return "/uii/plugin_workflow_wu_wma_import.xhtml";
    }

    /**
     * Constructor
     */
    public WuWmaImportWorkflowPlugin() {
        log.info("WuWmaImport importer workflow plugin started");

        // read important configuration first
        readConfiguration();
    }

    /**
     * private method to read main configuration file
     */
    private void readConfiguration() {
        // internal setup only - log to the server log, not to the shared UI log, so the log shown
        // in the plugin does not accumulate config lines on every (re)construction of the plugin
        log.debug("Start reading the configuration");

        // set specific title
        title = ConfigPlugins.getPluginConfig(id).getString("title");

        // check if content shall be cleaned up after successfull import
        cleanup = ConfigPlugins.getPluginConfig(id).getBoolean("cleanup", false);
        assignPages = ConfigPlugins.getPluginConfig(id).getBoolean("assignPages", true);

        // read list of mapping configuration
        importSets = new ArrayList<>();
        List<HierarchicalConfiguration> mappings = ConfigPlugins.getPluginConfig(id).configurationsAt("importSet");
        for (HierarchicalConfiguration node : mappings) {
            String settitle = node.getString("[@title]", "-");
            String source = node.getString("[@source]", "-");
            String workflow = node.getString("[@workflow]", "-");
            importSets.add(new ImportSet(settitle, source, workflow));
        }

        log.debug("Configuration successfully read");
    }

    /**
     * cancel a running import
     */
    public void cancel() {
        getState().setRun(false);
    }

    /**
     * main method to start the actual import
     *
     * @param importset
     */
    @Override
    public void startImport(IImportSet importset) {
        // resolve the session state now (we are in a request), so the background thread and every
        // later page visit share the same object; reset it for a fresh run
        WuWmaImportState st = getState();
        // guard against a second start while an import is still running: it would reset the very
        // state the running worker thread keeps writing to
        if (st.isRun()) {
            log.debug("An import is already running - ignoring the additional start request");
            return;
        }
        st.reset();
        updateLog("Start import for: " + importset.getTitle());
        st.setProgress(0);
        BeanHelper bhelp = new BeanHelper();
        // ObjectMapper om = new XmlMapper();
        XmlMapper om = new XmlMapper();

        // run the import in a separate thread to allow a dynamic progress bar
        st.setRun(true);
        Runnable runnable = () -> {

            // read input file
            try {
                updateLog("Run through all import files");

                // every message below follows the same shape, so the cause is readable at a glance:
                // "Import set 'X': <what is wrong>. <where to fix it>"
                String setLabel = "Import set '" + importset.getTitle() + "': ";
                String configFile = "plugin_" + id + ".xml";

                // run through all goobi.xml files in the given folder
                File sourceFolder = new File(importset.getSource());
                if (!sourceFolder.isDirectory()) {
                    updateLog(setLabel + "the configured source folder '" + importset.getSource()
                            + "' does not exist or is not a directory. Please check the 'source' attribute of this"
                            + " importSet in " + configFile + ".", 3);
                    return;
                }
                String[] extensions = { "goobi.xml" };
                Collection<File> fileCollection = FileUtils.listFiles(sourceFolder, extensions, true);
                fileCollection.removeIf(File::isHidden);

                List<File> files = new ArrayList<>(fileCollection);
                files.sort(NameFileComparator.NAME_INSENSITIVE_COMPARATOR);

                st.setItemsTotal(files.size());
                st.setItemCurrent(0);

                // no import file at all: without this the run would just end with "Import completed."
                // and look like a successful import that happened to have no records
                if (files.isEmpty()) {
                    // listFiles() matches on "." + extension, so show the endings the same way
                    String expectedEndings = "." + String.join(", .", extensions);
                    updateLog(setLabel + "no import files were found in '" + importset.getSource()
                            + "', subfolders included. Expected file name endings are: " + expectedEndings
                            + ". Nothing was imported.", 3);
                    return;
                }

                // the process template and its ruleset are the same for every record, so resolve and
                // check them once here and reuse them in the loop below
                String workflowTitle = importset.getWorkflow();
                if (StringUtils.isBlank(workflowTitle) || "-".equals(workflowTitle)) {
                    updateLog(setLabel + "no process template is configured. Please set the 'workflow' attribute of"
                            + " this importSet in " + configFile + ".", 3);
                    return;
                }
                Process template = ProcessManager.getProcessByExactTitle(workflowTitle);
                if (template == null) {
                    updateLog(setLabel + "no process template named '" + workflowTitle + "' exists in Goobi."
                            + " Please check the 'workflow' attribute of this importSet in " + configFile
                            + " and the title of the process template.", 3);
                    return;
                }
                if (template.getRegelsatz() == null) {
                    updateLog(setLabel + "the process template '" + workflowTitle + "' has no ruleset assigned."
                            + " Please assign a ruleset to this process template in Goobi.", 3);
                    return;
                }
                // getPreferences() returns null if the ruleset file is missing or cannot be parsed
                Prefs prefs = template.getRegelsatz().getPreferences();
                if (prefs == null) {
                    updateLog(setLabel + "the ruleset '" + template.getRegelsatz().getTitel() + "' (file '"
                            + template.getRegelsatz().getDatei() + "') of process template '" + workflowTitle
                            + "' could not be loaded. Please check that this file exists in the ruleset folder and"
                            + " that it is valid.", 3);
                    return;
                }

                for (File file : files) {
                    // little delay
                    Thread.sleep(100);
                    if (!st.isRun()) {
                        break;
                    }

                    if (file.exists() && !file.isHidden()) {
                        try {
                            updateLog("Start importing: Record " + (st.getItemCurrent() + 1), 1);

                            List<SimpleContent> allContentFiles = new ArrayList<>();

                            // create digital document
                            Fileformat fileformat = new MetsMods(prefs);
                            DigitalDocument dd = new DigitalDocument();
                            fileformat.setDigitalDocument(dd);

                            // add the physical basics
                            DocStructType boundBookType = prefs.getDocStrctTypeByName("BoundBook");
                            if (boundBookType == null) {
                                throw new ImportException("The ruleset of process template '" + template.getTitel()
                                        + "' does not contain the required DocStruct type 'BoundBook'.");
                            }
                            DocStruct physical = dd.createDocStruct(boundBookType);
                            dd.setPhysicalDocStruct(physical);
                            MetadataType pathImageType = prefs.getMetadataTypeByName("pathimagefiles");
                            if (pathImageType == null) {
                                throw new ImportException("The ruleset of process template '" + template.getTitel()
                                        + "' does not contain the required metadata type 'pathimagefiles'.");
                            }
                            Metadata mdForPath = new Metadata(pathImageType);
                            mdForPath.setValue("/images/");
                            physical.addMetadata(mdForPath);

                            // read the xml file
                            SimpleImportObject sio;
                            try {
                                sio = GoobiXmlReader.readReordered(file, om);
                            } catch (Exception e) {
                                throw new ImportException("The import file is not valid XML or could not be parsed: "
                                        + e.getMessage());
                            }
                            if (sio.getData() == null) {
                                throw new ImportException("The import file has no <data> element.");
                            }
                            if (sio.getProcess() == null) {
                                throw new ImportException("The import file has no <process> element.");
                            }

                            // If there are any content values in process, add them to the first data object
                            for (SimpleContent sc : sio.getProcess().getContents()) {
                                sio.getData().add(sc);
                            }

                            // resolve wildcard content entries (e.g. "/path/to/folder/*" or "/path/*.tif")
                            // into concrete SimpleContent entries, one per matching file
                            expandGlobContents(sio.getData());

                            // add the logical basics
                            String publicationType = sio.getData().getType();
                            DocStructType logicalType = prefs.getDocStrctTypeByName(publicationType);
                            if (logicalType == null) {
                                throw new ImportException("The publication type '" + publicationType
                                        + "' does not exist within the ruleset. Process cannot be created.");
                            }
                            DocStruct logical = dd.createDocStruct(logicalType);
                            dd.setLogicalDocStruct(logical);

                            createMetadataAndAddFileReferences(allContentFiles, prefs, dd, physical, sio.getData(),
                                    logical);

                            // get process title
                            String processname = sio.getProcess().getTitle();
                            if (StringUtils.isBlank(processname)) {
                                throw new ImportException("The <process> element has no <title>.");
                            }
                            String regex = ConfigurationHelper.getInstance().getProcessTitleReplacementRegex();
                            processname = processname.replaceAll(regex, "_").trim();

                            // save the process
                            Process process = bhelp.createAndSaveNewProcess(template, processname, fileformat);

                            // add journal entries
                            for (SimpleJournalEntry j : sio.getProcess().getJournalentries()) {
                                LogType logType = j.getType() == null ? null : LogType.getByTitle(j.getType().toLowerCase());
                                if (logType == null) {
                                    updateLog("Unknown journal type '" + j.getType() + "', using INFO instead.", 2);
                                    logType = LogType.INFO;
                                }
                                JournalEntry entry = new JournalEntry(process.getId(), new Date(), "Plugin " + title,
                                        logType, j.getValue(), EntryType.PROCESS);
                                JournalManager.saveJournalEntry(entry);
                            }

                            // add properties
                            for (SimpleProperty spr : sio.getProcess().getProperties()) {
                                bhelp.EigenschaftHinzufuegen(process, spr.getName(), spr.getValue());
                            }

                            // if process shall be added to batch, find it out to add it
                            String batchName = sio.getProcess().getBatch();
                            if (StringUtils.isNotBlank(batchName)) {
                                List<Batch> allBatches = ProcessManager.getBatches(5000);
                                Batch myBatch = null;
                                for (Batch b : allBatches) {
                                    // if batch exists, reuse it
                                    if (b.getBatchName() != null && b.getBatchName().equals(batchName)) {
                                        myBatch = b;
                                        break;
                                    }
                                }

                                // if no matching batch was found, create one now
                                if (myBatch == null) {
                                    Batch newBatch = new Batch();
                                    newBatch.setBatchName(batchName);
                                    process.setBatch(newBatch);
                                } else {
                                    process.setBatch(myBatch);
                                }

                            }

                            // save the process
                            ProcessManager.saveProcess(process);

                            // copy goobi.xml file into source folder
                            updateLog("Copy goobi.xml file");
                            StorageProvider.getInstance().createDirectories(Paths.get(process.getSourceDirectory()));
                            StorageProvider.getInstance().copyFile(Paths.get(file.getAbsolutePath()),
                                    Paths.get(process.getSourceDirectory(), file.getName()));

                            // if media files are given, import these into the media folder of the process
                            updateLog("Start copying media files");

                            int fileCounter = 1;
                            String stringFormatSpecifier = "%" + String.format("%02d", (int) Math.log10(Math.abs(allContentFiles.size())) + 1) + "d";

                            for (SimpleContent con : allContentFiles) {
                                String folder = con.getFolder() == null ? "" : con.getFolder().trim();
                                if (folder.isEmpty()) {
                                    updateLog("Error: Content entry without a 'folder' attribute (source '"
                                            + con.getSource() + "') - skipped", 3);
                                    continue;
                                }
                                if (con.getSource() == null) {
                                    updateLog("Error: Content entry in folder '" + folder
                                            + "' has no source path - skipped", 3);
                                    continue;
                                }
                                String targetBase = process.getConfiguredImageFolder(folder);

                                // get the source folder
                                if (targetBase == null && "source".equals(folder)) {
                                    targetBase = process.getSourceDirectory();
                                }

                                // get the import folder
                                if (targetBase == null && "import".equals(folder)) {
                                    targetBase = process.getImportDirectory();
                                }

                                // if the target folder cannot be found
                                if (targetBase == null) {
                                    updateLog("Error: Target folder '" + folder + "' does not exist for file '"
                                            + con.getSource() + "'", 3);
                                } else {
                                    File contentfile = new File(con.getSource());
                                    if (contentfile.canRead()) {

                                		StorageProvider.getInstance().createDirectories(Paths.get(targetBase, con.getRelativePath()));
                                    	String originalName = contentfile.getName();
                                        String regexExpression = ConfigurationHelper.getInstance().getProcessTitleReplacementRegex();
                                        int dot = originalName.lastIndexOf('.');
                                        String baseName = dot < 0 ? originalName : originalName.substring(0, dot);
                                        String extension = dot < 0 ? "" : originalName.substring(dot);
                                        String normalizedName = String.format(stringFormatSpecifier, fileCounter++) + "_"
                                                + baseName.replaceAll(regexExpression, "_") + extension;

                                        StorageProvider.getInstance()
                                                .copyFile(Paths.get(contentfile.getAbsolutePath()),
                                                        Paths.get(targetBase, con.getRelativePath(), normalizedName));
                                    } else if (!contentfile.exists()) {
                                        throw new ImportException("Content file does not exist: '"
                                                + contentfile.getAbsolutePath() + "'");
                                    } else {
                                        throw new ImportException("No read access to content file: '"
                                                + contentfile.getAbsolutePath() + "'");
                                    }
                                }
                            }

                            // update database information for process
                            Step currentStep = process.getAktuellerSchritt();
                            HelperSchritte.updateMetadataIndex(currentStep);
                            try {
                                int numberOfFiles = StorageProvider.getInstance()
                                        .getNumberOfFiles(Paths.get(process.getImagesOrigDirectory(true)));
                                if (numberOfFiles == 0) {
                                    numberOfFiles = StorageProvider.getInstance()
                                            .getNumberOfFiles(Paths.get(process.getImagesTifDirectory(true)));
                                }
                                if (numberOfFiles > 0 && process.getSortHelperImages() != numberOfFiles) {
                                    ProcessManager.updateImages(numberOfFiles, process.getId());
                                }
                            } catch (Exception e) {
                                log.error("An exception occurred while closing a step for process with ID "
                                        + process.getId(), e);
                            }

                            // start any open automatic tasks for the created process
                            for (Step s : process.getSchritteList()) {
                                if (StepStatus.OPEN.equals(s.getBearbeitungsstatusEnum()) && s.isTypAutomatisch()) {
                                    ScriptThreadWithoutHibernate myThread = new ScriptThreadWithoutHibernate(s);
                                    myThread.startOrPutToQueue();
                                }
                            }

                            // if the files shall be cleaned up in the source folder, do it now for all
                            // successful processes
                            if (cleanup) {
                                StorageProvider.getInstance().deleteFile(Paths.get(file.getAbsolutePath()));
                                for (SimpleContent con : sio.getProcess().getContents()) {
                                    File contentfile = new File(con.getSource());
                                    if (contentfile.canWrite()) {
                                        StorageProvider.getInstance()
                                                .deleteFile(Paths.get(contentfile.getAbsolutePath()));
                                    }
                                }
                            }

                            fileformat.write(process.getMetadataFilePath());
                            updateLog("Process successfully created with ID: " + process.getId());

                        } catch (Exception e) {
                            log.error("Error while creating a process during the import for file "
                                    + file.getAbsolutePath(), e);
                            updateLog("Error while creating a process during the import for file "
                                    + file.getAbsolutePath() + ": " + e.getMessage(), 3);
                            Helper.setFehlerMeldung("Error while creating a process during the import for file "
                                    + file.getAbsolutePath() + ": " + e.getMessage());
                            if (pusher != null) {
                                pusher.send("error");
                            }
                        }

                    }
                    // recalculate progress
                    st.incrementItemCurrent();
                    st.setProgress(100 * st.getItemCurrent() / st.getItemsTotal());
                    updateLog("Processing of record done.");
                }

                // finally last push
                Thread.sleep(2000);
                updateLog("Import completed.");
            } catch (InterruptedException e) {
                Helper.setFehlerMeldung("Import was interrupted: " + e.getMessage());
                log.error("Import was interrupted", e);
                updateLog("Import was interrupted: " + e.getMessage(), 3);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // catch everything else so the worker thread never dies silently and leaves run stuck on true
                Helper.setFehlerMeldung("Import aborted: " + e.getMessage());
                log.error("Import aborted unexpectedly", e);
                updateLog("Import aborted: " + e.getMessage(), 3);
            } finally {
                st.setRun(false);
                // closing push, also for the early returns above: updateLog() only pushes once per
                // 500ms, so a message from a run that ends immediately would otherwise stay unsent
                if (pusher != null) {
                    pusher.send("update");
                }
            }

        };
        new Thread(runnable).start();

    }

    /**
     * Recursively walk the SimpleData tree and replace every SimpleContent whose source path uses a
     * glob in the last path segment (e.g. "/folder/*", "/folder/*.tif", "/folder/img_?.jpg") with one
     * concrete SimpleContent per matching file. Folder, relativePath and label are copied from the
     * original entry. Matches are sorted alphabetically by file name for deterministic numbering.
     * Hidden files and subdirectories are skipped.
     */
    private void expandGlobContents(SimpleData sd) {
        List<SimpleContent> expanded = new ArrayList<>();
        for (SimpleContent sc : sd.getContents()) {
            String source = sc.getSource();
            if (source == null) {
                expanded.add(sc);
                continue;
            }
            String trimmed = source.trim();
            Path sourcePath = Paths.get(trimmed);
            String lastSegment = sourcePath.getFileName() == null ? "" : sourcePath.getFileName().toString();
            if (!lastSegment.contains("*") && !lastSegment.contains("?")) {
                expanded.add(sc);
                continue;
            }

            Path parent = sourcePath.getParent();
            if (parent == null || !Files.isDirectory(parent)) {
                updateLog("Error: Parent folder does not exist for wildcard content '" + source + "'", 3);
                continue;
            }

            List<Path> matches = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent, lastSegment)) {
                for (Path p : stream) {
                    if (Files.isRegularFile(p) && !Files.isHidden(p)) {
                        matches.add(p);
                    }
                }
            } catch (IOException e) {
                updateLog("Error: Could not list wildcard content '" + source + "': " + e.getMessage(), 3);
                continue;
            }

            if (matches.isEmpty()) {
                updateLog("Warning: Wildcard content '" + source + "' did not match any file", 2);
                continue;
            }

            Collections.sort(matches, Comparator.comparing(p -> p.getFileName().toString()));
            for (Path match : matches) {
                SimpleContent copy = new SimpleContent();
                copy.setFolder(sc.getFolder());
                copy.setRelativePath(sc.getRelativePath());
                copy.setLabel(sc.getLabel());
                copy.setSource(match.toAbsolutePath().toString());
                expanded.add(copy);
            }
        }
        sd.getContents().clear();
        sd.getContents().addAll(expanded);

        for (SimpleData child : sd.getData()) {
            expandGlobContents(child);
        }
    }

    /**
     * Recursive function to add pages and metadata to the logical part
     *
     * @param allContentFiles
     * @param prefs
     * @param dd
     * @param physical
     * @param sd
     * @param ds
     * @throws MetadataTypeNotAllowedException
     * @throws TypeNotAllowedForParentException
     * @throws TypeNotAllowedAsChildException
     */
    private void createMetadataAndAddFileReferences(List<SimpleContent> allContentFiles, Prefs prefs,
            DigitalDocument dd, DocStruct physical, SimpleData sd, DocStruct ds)
            throws MetadataTypeNotAllowedException, TypeNotAllowedForParentException, TypeNotAllowedAsChildException {

        // create all metadata fields
        for (SimpleMetadata sm : sd.getMetadatas()) {
            Metadata metadata = getMetadata(prefs, sm);
            try {
                ds.addMetadata(metadata);
            } catch (MetadataTypeNotAllowedException e) {
                throw new ImportException("Metadata '" + metadata.getType().getName() + "' is not allowed for DocStruct '"
                        + ds.getType().getName()
                        + "' - either the type is not permitted here, or its allowed number is exceeded.");
            }
        }

        // create all corporates
        for (SimpleCorporate sc : sd.getCorporates()) {
            Corporate corporate = getCorporate(prefs, sc);
            try {
                ds.addCorporate(corporate);
            } catch (MetadataTypeNotAllowedException e) {
                throw new ImportException("Corporate '" + (sc.getRole() == null ? "" : sc.getRole().trim())
                        + "' is not allowed for DocStruct '" + ds.getType().getName()
                        + "' - either the type is not permitted here, or its allowed number is exceeded.");
            }
        }

        // create all metadata groups
        for (SimpleGroup sg : sd.getGroups()) {
            MetadataGroup group = getGroup(prefs, sg);
            try {
                ds.addMetadataGroup(group);
            } catch (MetadataTypeNotAllowedException e) {
                throw new ImportException("Metadata group '" + (sg.getType() == null ? "" : sg.getType().trim())
                        + "' is not allowed for DocStruct '" + ds.getType().getName()
                        + "' - either the type is not permitted here, or its allowed number is exceeded.");
            }
        }

        // create all persons
        for (SimplePerson sp : sd.getPersons()) {
            Person person = getPerson(prefs, sp);
            try {
                ds.addPerson(person);
            } catch (MetadataTypeNotAllowedException e) {
                throw new ImportException("Person '" + (sp.getRole() == null ? "" : sp.getRole().trim())
                        + "' is not allowed for DocStruct '" + ds.getType().getName()
                        + "' - either the type is not permitted here, or its allowed number is exceeded.");
            }
        }

        // add all file references
        for (SimpleContent con : sd.getContents()) {

            if (con.getSource() == null || con.getSource().trim().isEmpty()) {
                updateLog("Content entry without a source path - skipped", 2);
                continue;
            }

            if (assignPages) {
                DocStructType pageType = prefs.getDocStrctTypeByName("page");
                if (pageType == null) {
                    throw new ImportException("The ruleset does not contain the required DocStruct type 'page'.");
                }
                DocStruct dsPage = dd.createDocStruct(pageType);
                physical.addChild(dsPage);
                addMetadata("physPageNumber", String.valueOf(physical.getAllChildren().size()), prefs, dsPage);
                addMetadata("logicalPageNumber", con.getLabel(), prefs, dsPage);

                // Add page to parent object
                DocStruct currentDs = ds;
                while (currentDs != null) {
                    currentDs.addReferenceTo(dsPage, "logical_physical");
                    currentDs = currentDs.getParent();
                }

                ContentFile cf = new ContentFile();
                String sourcePath = con.getSource();

                File f = new File(sourcePath);
                String mimetype = NIOFileUtils.getMimeTypeFromFile(Paths.get(sourcePath));
                cf.setMimetype(mimetype);

                String originalName = f.getName();
                String regexExpression = ConfigurationHelper.getInstance().getProcessTitleReplacementRegex();
                int dot = originalName.lastIndexOf('.');
                String baseName = dot < 0 ? originalName : originalName.substring(0, dot);
                String extension = dot < 0 ? "" : originalName.substring(dot);
                String normalizedName = baseName.replaceAll(regexExpression, "_") + extension;

                cf.setLocation(normalizedName);
                dsPage.addContentFile(cf);
            }
            allContentFiles.add(con);
        }

        // Go through all children
        for (SimpleData sdchild : sd.getData()) {
            String childType = sdchild.getType() == null ? "" : sdchild.getType().trim();
            DocStructType childDsType = prefs.getDocStrctTypeByName(childType);
            if (childDsType == null) {
                throw new ImportException("The (child) publication type '" + childType
                        + "' does not exist within the ruleset.");
            }
            DocStruct dsChild = dd.createDocStruct(childDsType);
            ds.addChild(dsChild);
            createMetadataAndAddFileReferences(allContentFiles, prefs, dd, physical, sdchild, dsChild);
        }
    }

    /**
     * Generate a Corporate out of a {@link SimpleCorporate}
     *
     * @param prefs
     * @param sc
     * @return
     * @throws MetadataTypeNotAllowedException
     */
    private Corporate getCorporate(Prefs prefs, SimpleCorporate sc) throws MetadataTypeNotAllowedException {
        String role = sc.getRole() == null ? "" : sc.getRole().trim();
        MetadataType mdt = prefs.getMetadataTypeByName(role);
        if (mdt == null) {
            throw new ImportException("The corporate role '" + role + "' does not exist within the ruleset.");
        }
        Corporate c = new Corporate(mdt);
        c.setMainName(sc.getName());
        c.setAuthorityFile(sc.getAuthority(), sc.getAuthorityURI(), sc.getValueURI());
        c.setPartName(sc.getPartname());
        List<NamePart> subnames = new ArrayList<>();
        for (String s : sc.getSubnames()) {
            subnames.add(new NamePart(s, s));
        }
        c.setSubNames(subnames);
        return c;
    }

    /**
     * Generate a Metadata out of a {@link SimpleMetadata}
     *
     * @param prefs
     * @param sm
     * @return
     * @throws MetadataTypeNotAllowedException
     */
    private Metadata getMetadata(Prefs prefs, SimpleMetadata sm) throws MetadataTypeNotAllowedException {
        String type = sm.getType() == null ? "" : sm.getType().trim();
        if (type.isEmpty()) {
            throw new ImportException("A <metadata> element without a 'type' attribute was found.");
        }
        MetadataType mdt = prefs.getMetadataTypeByName(type);
        if (mdt == null) {
            throw new ImportException("The metadata type '" + type + "' does not exist within the ruleset.");
        }
        String value = sm.getValue() == null ? "" : sm.getValue().trim();
        if (value.isEmpty()) {
            throw new ImportException("The metadata of type '" + type + "' is empty.");
        }
        Metadata m = new Metadata(mdt);
        m.setValue(value);
        m.setAuthorityFile(sm.getAuthority(), sm.getAuthorityURI(), sm.getValueURI());
        return m;
    }

    /**
     * Generate a Person out of a {@link SimplePerson}
     *
     * @param prefs
     * @param sp
     * @return
     * @throws MetadataTypeNotAllowedException
     */
    private Person getPerson(Prefs prefs, SimplePerson sp) throws MetadataTypeNotAllowedException {
        String role = sp.getRole() == null ? "" : sp.getRole().trim();
        MetadataType mdt = prefs.getMetadataTypeByName(role);
        if (mdt == null) {
            throw new ImportException("The person role '" + role + "' does not exist within the ruleset.");
        }
        Person p = new Person(mdt);
        p.setFirstname(sp.getFirstname());
        p.setLastname(sp.getLastname());
        p.setAuthorityFile(sp.getAuthority(), sp.getAuthorityURI(), sp.getValueURI());
        return p;
    }

    /**
     * Generate a MetadataGroup out of a {@link SimpleGroup}
     *
     * @param prefs
     * @param sg
     * @return
     * @throws MetadataTypeNotAllowedException
     */
    private MetadataGroup getGroup(Prefs prefs, SimpleGroup sg) throws MetadataTypeNotAllowedException {
        String groupType = sg.getType() == null ? "" : sg.getType().trim();
        MetadataGroupType mdt = prefs.getMetadataGroupTypeByName(groupType);
        if (mdt == null) {
            throw new ImportException("The metadata group type '" + groupType + "' does not exist within the ruleset.");
        }
        MetadataGroup group = new MetadataGroup(mdt);

        // create all metadata fields
        for (SimpleMetadata sm : sg.getMetadatas()) {
            group.addMetadata(getMetadata(prefs, sm));
        }

        // create all corporates
        for (SimpleCorporate sc : sg.getCorporates()) {
            group.addCorporate(getCorporate(prefs, sc));
        }

        // create all metadata groups
        for (SimpleGroup ssg : sg.getGroups()) {
            group.addMetadataGroup(getGroup(prefs, ssg));
        }

        // create all persons
        for (SimplePerson sp : sg.getPersons()) {
            group.addPerson(getPerson(prefs, sp));
        }

        return group;
    }

    @Override
    public void setPushContext(PushContext pusher) {
        this.pusher = pusher;
    }

    /**
     * simple method to send status message to gui
     *
     * @param logmessage
     */
    private void updateLog(String logmessage) {
        updateLog(logmessage, 0);
    }

    /**
     * simple method to send status message with specific level to gui
     *
     * @param logmessage
     */
    private void updateLog(String logmessage, int level) {
        WuWmaImportState st = getState();
        if (level > 1) {
            st.incrementErrors();
        }

        st.addLog(new LogMessage(logmessage, level));
        log.debug(logmessage);
        if (pusher != null && System.currentTimeMillis() - st.getLastPush() > 500) {
            st.setLastPush(System.currentTimeMillis());
            pusher.send("update");
        }
    }

    @Data
    @AllArgsConstructor
    public class ImportSet implements IImportSet {
        private String title;
        private String source;
        private String workflow;
    }

    /**
     * simple helper to create a regular metadata
     * 
     * @param type
     * @param value
     * @param prefs
     * @param ds
     * @throws MetadataTypeNotAllowedException
     */
    private void addMetadata(String type, String value, Prefs prefs, DocStruct ds)
            throws MetadataTypeNotAllowedException {
        MetadataType mdt = prefs.getMetadataTypeByName(type);
        if (mdt == null) {
            throw new ImportException("The ruleset does not contain the required metadata type '" + type + "'.");
        }
        Metadata m = new Metadata(mdt);
        m.setValue(value == null ? "" : value);
        ds.addMetadata(m);
    }

    /**
     * Thrown for invalid import input or ruleset problems. Carries a clear, human-readable message
     * (unlike UGH's generic exceptions) and is unchecked so it surfaces via the per-record catch.
     */
    private static class ImportException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ImportException(String message) {
            super(message);
        }
    }
}

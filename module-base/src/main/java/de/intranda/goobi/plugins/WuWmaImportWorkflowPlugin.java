package de.intranda.goobi.plugins;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Queue;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.goobi.beans.Batch;
import org.goobi.beans.JournalEntry;
import org.goobi.beans.JournalEntry.EntryType;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.flow.statistics.hibernate.FilterHelper;
import org.goobi.production.plugin.interfaces.IPushPlugin;
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;
import org.omnifaces.cdi.PushContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.BeanHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.HelperSchritte;
import de.sub.goobi.helper.ScriptThreadWithoutHibernate;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.persistence.managers.JournalManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import io.goobi.workflow.importer.model.SimpleContent;
import io.goobi.workflow.importer.model.SimpleCorporate;
import io.goobi.workflow.importer.model.SimpleGroup;
import io.goobi.workflow.importer.model.SimpleImportObject;
import io.goobi.workflow.importer.model.SimpleJournalEntry;
import io.goobi.workflow.importer.model.SimpleMetadata;
import io.goobi.workflow.importer.model.SimplePerson;
import io.goobi.workflow.importer.model.SimpleProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.Corporate;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.MetadataGroupType;
import ugh.dl.MetadataType;
import ugh.dl.NamePart;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.fileformats.mets.MetsMods;

@PluginImplementation
@Log4j2
public class WuWmaImportWorkflowPlugin implements IWorkflowPlugin, IPushPlugin {

    private static final long serialVersionUID = 7807903481239517297L;

    @Getter
    private String id = "intranda_workflow_wu_wma_import";

    @Getter
    private String title = "intranda_workflow_wu_wma_import";
    
    private boolean cleanup = false;
    private long lastPush = System.currentTimeMillis();
    @Getter
    private transient List<ImportSet> importSets;
    private PushContext pusher;
    @Getter
    private boolean run = false;
    @Getter
    private int progress = -1;
    @Getter
    private int itemCurrent = 0;
    @Getter
    int itemsTotal = 0;
    @Getter
    private transient Queue<LogMessage> logQueue = new CircularFifoQueue<>(50000);
    @Getter
    private int errors = 0;

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
        updateLog("Start reading the configuration");

        errors = 0;

        // set specific title
        title = ConfigPlugins.getPluginConfig(id).getString("title");

        // check if content shall be cleaned up after successfull import
        cleanup = ConfigPlugins.getPluginConfig(id).getBoolean("cleanup", false);
        
        // read list of mapping configuration
        importSets = new ArrayList<>();
        List<HierarchicalConfiguration> mappings = ConfigPlugins.getPluginConfig(id).configurationsAt("importSet");
        for (HierarchicalConfiguration node : mappings) {
            String settitle = node.getString("[@title]", "-");
            String source = node.getString("[@source]", "-");
            String workflow = node.getString("[@workflow]", "-");
            importSets.add(new ImportSet(settitle, source, workflow));
        }

        // write a log into the UI
        updateLog("Configuration successfully read");
    }

    /**
     * cancel a running import
     */
    public void cancel() {
        run = false;
    }

    /**
     * main method to start the actual import
     * 
     * @param importConfiguration
     */
    public void startImport(ImportSet importset) {
        updateLog("Start import for: " + importset.getTitle());
        progress = 0;
        BeanHelper bhelp = new BeanHelper();
        //ObjectMapper om = new XmlMapper();
        XmlMapper om = new XmlMapper();
        
        // run the import in a separate thread to allow a dynamic progress bar
        run = true;
        Runnable runnable = () -> {

            // read input file
            try {
                updateLog("Run through all import files");
                // run through all goobi.xml files in the given folder
                String[] extensions = { "goobi.xml" };
                Collection<File> files = FileUtils.listFiles(new File(importset.source), extensions, true);
                files.removeIf(File::isHidden);
                itemsTotal = files.size();
                itemCurrent = 0;

                for (File file : files) {
                    // little delay
                    Thread.sleep(100);
                    if (!run) {
                        break;
                    }

                    if (file.exists() && !file.isHidden()) {
                        try {
                            updateLog("Start importing: Record " + (itemCurrent + 1), 1);

                            // get the correct workflow to use
                            Process template = ProcessManager.getProcessByExactTitle(importset.getWorkflow());
                            Prefs prefs = template.getRegelsatz().getPreferences();

                            // create digital document
                            Fileformat fileformat = new MetsMods(prefs);
                            DigitalDocument dd = new DigitalDocument();
                            fileformat.setDigitalDocument(dd);

                            // add the physical basics
                            DocStruct physical = dd.createDocStruct(prefs.getDocStrctTypeByName("BoundBook"));
                            dd.setPhysicalDocStruct(physical);
                            Metadata mdForPath = new Metadata(prefs.getMetadataTypeByName("pathimagefiles"));
                            mdForPath.setValue("/images/");
                            physical.addMetadata(mdForPath);

                            // read the xml file
                            SimpleImportObject sio = GoobiXmlReader.readReordered(file, om);

                            // add the logical basics
                            DocStruct logical = dd.createDocStruct(prefs.getDocStrctTypeByName(sio.getData().getType()));
                            if (logical.getType() == null) {
                                updateLog("The Publication type with name '" + sio.getData().getType()
                                        + "' does not exist within the ruleset. Process cannot be created.", 3);
                                throw new MetadataTypeNotAllowedException(
                                        "Publication type " + sio.getData().getType() + " does not exist. Process creation not possible.");
                            }
                            dd.setLogicalDocStruct(logical);

                            // create all metadata fields
                            for (SimpleMetadata sm : sio.getData().getMetadatas()) {
                                logical.addMetadata(getMetadata(prefs, sm));
                            }

                            // create all corporates
                            for (SimpleCorporate sc : sio.getData().getCorporates()) {
                                logical.addCorporate(getCorporate(prefs, sc));
                            }

                            // create all metadata groups
                            for (SimpleGroup sg : sio.getData().getGroups()) {
                                logical.addMetadataGroup(getGroup(prefs, sg));
                            }

                            // create all persons
                            for (SimplePerson sp : sio.getData().getPersons()) {
                                logical.addPerson(getPerson(prefs, sp));
                            }

                            // get process title
                            String processname = sio.getProcess().getTitle();
                            String regex = ConfigurationHelper.getInstance().getProcessTitleReplacementRegex();
                            processname = processname.replaceAll(regex, "_").trim();

                            // save the process
                            Process process = bhelp.createAndSaveNewProcess(template, processname, fileformat);

                            // add journal entries
                            for (SimpleJournalEntry j : sio.getProcess().getJournalentries()) {
                                JournalEntry entry = new JournalEntry(process.getId(), new Date(), "Plugin " + title,
                                        LogType.getByTitle(j.getType().toLowerCase()), j.getValue(), EntryType.PROCESS);
                                JournalManager.saveJournalEntry(entry);
                            }

                            // add properties
                            for (SimpleProperty spr : sio.getProcess().getProperties()) {
                                bhelp.EigenschaftHinzufuegen(process, spr.getName(), spr.getValue());
                            }

                            // if process shall be added to batch, find it out to add it
                            String batchName = sio.getProcess().getBatch();
                            if (StringUtils.isNotBlank(batchName)) {
                            	List<Batch> allBatches = ProcessManager
                                        .getBatches(5000);
                                Batch myBatch = null;
                            	for (Batch b : allBatches) {
									// if batch exists, reuse it
                                	if (b.getBatchName().equals(batchName)) {
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
                            
                            // if media files are given, import these into the media folder of the process
                            updateLog("Start copying media files");

                            for (SimpleContent con : sio.getProcess().getContents()) {
                                String targetBase = process.getConfiguredImageFolder(con.getFolder().trim());

                                // get the source folder
                                if (targetBase == null && "source".equals(con.getFolder().trim())) {
                                    targetBase = process.getSourceDirectory();
                                }

                                // get the import folder
                                if (targetBase == null && "import".equals(con.getFolder().trim())) {
                                    targetBase = process.getImportDirectory();
                                }

                                // if the target folder cannot be found
                                if (targetBase == null) {
                                    updateLog("Error: Target folder '" + con.getFolder() + "' does not exist for file '" + con.getSource() + "'", 3);
                                } else {
                                    File contentfile = new File(con.getSource());
                                    if (contentfile.canRead()) {
                                        StorageProvider.getInstance().createDirectories(Paths.get(targetBase));
                                        StorageProvider.getInstance()
                                                .copyFile(Paths.get(contentfile.getAbsolutePath()), Paths.get(targetBase, contentfile.getName()));
                                    }
                                }
                            }

                            // update database information for process
                            Step st = process.getAktuellerSchritt();
                            HelperSchritte.updateMetadataIndex(st);
                            try {
                                int numberOfFiles = StorageProvider.getInstance().getNumberOfFiles(Paths.get(process.getImagesOrigDirectory(true)));
                                if (numberOfFiles == 0) {
                                    numberOfFiles = StorageProvider.getInstance().getNumberOfFiles(Paths.get(process.getImagesTifDirectory(true)));
                                }
                                if (numberOfFiles > 0 && process.getSortHelperImages() != numberOfFiles) {
                                    ProcessManager.updateImages(numberOfFiles, process.getId());
                                }
                            } catch (Exception e) {
                                log.error("An exception occurred while closing a step for process with ID " + process.getId(), e);
                            }
                            
                            
                            // start any open automatic tasks for the created process
                            for (Step s : process.getSchritteList()) {
                                if (StepStatus.OPEN.equals(s.getBearbeitungsstatusEnum()) && s.isTypAutomatisch()) {
                                    ScriptThreadWithoutHibernate myThread = new ScriptThreadWithoutHibernate(s);
                                    myThread.startOrPutToQueue();
                                }
                            }
                            
                            // if the files shall be cleaned up in the source folder, do it now for all successful processes
                            if (cleanup) {
                            	StorageProvider.getInstance().deleteFile(Paths.get(file.getAbsolutePath()));
                            	for (SimpleContent con : sio.getProcess().getContents()) {
	                                File contentfile = new File(con.getSource());
	                                if (contentfile.canWrite()) {
	                                	StorageProvider.getInstance().deleteFile(Paths.get(contentfile.getAbsolutePath()));
	                                }
	                            }
                            }
                            
                            
                            updateLog("Process successfully created with ID: " + process.getId());

                        } catch (Exception e) {
                            log.error("Error while creating a process during the import for file " + file.getAbsolutePath(), e);
                            updateLog("Error while creating a process during the import for file " + file.getAbsolutePath() + ": " + e.getMessage(),
                                    3);
                            Helper.setFehlerMeldung(
                                    "Error while creating a process during the import for file " + file.getAbsolutePath() + ": " + e.getMessage());
                            pusher.send("error");
                        }

                    }
                    // recalculate progress
                    itemCurrent++;
                    progress = 100 * itemCurrent / itemsTotal;
                    updateLog("Processing of record done.");
                }

                // finally last push
                run = false;
                Thread.sleep(2000);
                updateLog("Import completed.");
            } catch (InterruptedException e) {
                Helper.setFehlerMeldung("Error while trying to execute the import: " + e.getMessage());
                log.error("Error while trying to execute the import", e);
                updateLog("Error while trying to execute the import: " + e.getMessage(), 3);
            }

        };
        new Thread(runnable).start();

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
        MetadataType mdt = prefs.getMetadataTypeByName(sc.getRole().trim());
        if (mdt == null) {
            updateLog("The metadata type with name '" + sc.getRole().trim()
                    + "' does not exist within the ruleset.", 3);
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
        MetadataType mdt = prefs.getMetadataTypeByName(sm.getType().trim());
        if (mdt == null) {
            updateLog("The metadata type with name '" + sm.getType().trim()
                    + "' does not exist within the ruleset.", 3);
        }
        if (StringUtils.isBlank(sm.getValue())) {
            updateLog("The metadata of type '" + sm.getType().trim()
                    + "' is empty.", 3);
        }
        Metadata m = new Metadata(mdt);
        m.setValue(sm.getValue().trim());
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
        MetadataType mdt = prefs.getMetadataTypeByName(sp.getRole().trim());
        if (mdt == null) {
            updateLog("The metadata type with name '" + sp.getRole().trim()
                    + "' does not exist within the ruleset.", 3);
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
        MetadataGroupType mdt = prefs.getMetadataGroupTypeByName(sg.getType().trim());
        if (mdt == null) {
            updateLog("The metadata group type with name '" + sg.getType().trim()
                    + "' does not exist within the ruleset.", 3);
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
        if (level > 1) {
            errors++;
        }

        logQueue.add(new LogMessage(logmessage, level));
        log.debug(logmessage);
        if (pusher != null && System.currentTimeMillis() - lastPush > 500) {
            lastPush = System.currentTimeMillis();
            pusher.send("update");
        }
    }

    @Data
    @AllArgsConstructor
    public class ImportSet {
        private String title;
        private String source;
        private String workflow;
    }

    @Data
    @AllArgsConstructor
    public class LogMessage {
        private String message;
        private int level = 0;
    }
}

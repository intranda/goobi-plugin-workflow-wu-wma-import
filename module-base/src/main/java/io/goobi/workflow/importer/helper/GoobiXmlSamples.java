package io.goobi.workflow.importer.helper;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import io.goobi.workflow.importer.model.SimpleContent;
import io.goobi.workflow.importer.model.SimpleCorporate;
import io.goobi.workflow.importer.model.SimpleData;
import io.goobi.workflow.importer.model.SimpleGroup;
import io.goobi.workflow.importer.model.SimpleImportObject;
import io.goobi.workflow.importer.model.SimpleJournalEntry;
import io.goobi.workflow.importer.model.SimpleProcess;
import io.goobi.workflow.importer.model.SimpleProperty;

public class GoobiXmlSamples {

	public static void main(String[] args) throws StreamReadException, DatabindException, IOException {
		GoobiXmlSamples gxs = new GoobiXmlSamples();
		gxs.xmlWrite();
		gxs.xmlRead();
	}
	
	/**
     * Write {@link SimpleImportObject} into xml file
     *
     * @throws StreamReadException
     * @throws DatabindException
     * @throws IOException
     */
    public void xmlWrite() throws StreamReadException, DatabindException, IOException {
    	System.out.println("\n=============== Write Java Objects to XML - Start ==================\n");

        // get a generated sample object
        SimpleImportObject sio = generateSimpleImportObject();

        // write object as xml file
        ObjectMapper om = new XmlMapper();
        om.setSerializationInclusion(Include.NON_EMPTY);
        om.enable(SerializationFeature.INDENT_OUTPUT);

        File file = new File("/opt/digiverso/goobi/tmp/output_simple_data.xml");
        om.writeValue(file, sio);

        // debugging
        System.out.println(om.writeValueAsString(sio));
        System.out.println("\n=============== Write Java Objects to XML - End ==================\n");
    }

    /**
     * read {@link SimpleImportObject} from xml file
     *
     * @throws StreamReadException
     * @throws DatabindException
     * @throws IOException
     */
     public void xmlRead() throws StreamReadException, DatabindException, IOException {
    	System.out.println("\n=============== Read XML to Java Objects - Start ==================\n");
    	
    	// read object from xml file
        File file = new File("/opt/digiverso/goobi/tmp/output_simple_data.xml");
        ObjectMapper om = new XmlMapper();
        SimpleImportObject sio = om.readValue(file, SimpleImportObject.class);

        // debugging
        System.out.println(sio.getProcess().getTitle());
        for (SimpleProperty sp : sio.getProcess().getProperties()) {
        	System.out.println(sp.getName() + ": " + sp.getValue());
        }
        System.out.println("\n=============== Read XML to Java Objects - End ==================\n");
    }

    /**
     * Generate a simple import object as reference
     *
     * @return
     */
    private SimpleImportObject generateSimpleImportObject() {
    	CommandHelper ch = new CommandHelper();
    	
    	// prepare sample object
        SimpleImportObject sio = new SimpleImportObject();

        // Process information
        SimpleProcess sp = new SimpleProcess();
        sp.setTitle("mein Vorgang");
        sio.setProcess(sp);

        // Journal entry 1
        SimpleJournalEntry sje1 = new SimpleJournalEntry();
        sje1.setCreator("Steffen");
        sje1.setType("info");
        sje1.setValue("erster Journaleintrag");
        sp.getJournalentries().add(sje1);

        // Journal entry 2
        SimpleJournalEntry sje2 = new SimpleJournalEntry();
        sje2.setCreator("Paul");
        sje2.setType("debug");
        sje2.setValue("zweiter Journaleintrag");
        sp.getJournalentries().add(sje2);

        // Property 1
        SimpleProperty spr1 = new SimpleProperty();
        spr1.setName("Auflösung");
        spr1.setValue("300dpi");
        sp.getProperties().add(spr1);

        // Property 2
        SimpleProperty spr2 = new SimpleProperty();
        spr2.setName("Schrifttyp");
        spr2.setValue("Antiqua");
        sp.getProperties().add(spr2);

        SimpleContent sconMedia1 = new SimpleContent();
        sconMedia1.setFolder("media");
        sconMedia1.setLabel("1r");
        sconMedia1.setSource("/opt/digiverso/import/marken/123/media/001.jpg");
        sp.getContents().add(sconMedia1);
        
        SimpleContent sconMedia2 = new SimpleContent();
        sconMedia2.setFolder("media");
        sconMedia2.setLabel("1v");
        sconMedia2.setSource("/opt/digiverso/import/marken/123/media/002.jpg");
        sp.getContents().add(sconMedia2);
        
        SimpleContent sconMedia3 = new SimpleContent();
        sconMedia3.setFolder("media");
        sconMedia3.setLabel("2r");
        sconMedia3.setSource("/opt/digiverso/import/marken/123/media/003.jpg");
        sp.getContents().add(sconMedia3);

        SimpleContent sconMaster1 = new SimpleContent();
        sconMaster1.setFolder("master");
        sconMaster1.setSource("/opt/digiverso/import/marken/123/master/001.tif");
        sp.getContents().add(sconMaster1);
        
        SimpleContent sconMaster2 = new SimpleContent();
        sconMaster2.setFolder("master");
        sconMaster2.setSource("/opt/digiverso/import/marken/123/master/002.tif");
        sp.getContents().add(sconMaster2);
        
        SimpleContent sconMaster3 = new SimpleContent();
        sconMaster3.setFolder("master");
        sconMaster3.setSource("/opt/digiverso/import/marken/123/master/003.tif");
        sp.getContents().add(sconMaster3);

        SimpleContent sconAlto1 = new SimpleContent();
        sconAlto1.setFolder("alto");
        sconAlto1.setSource("/opt/digiverso/import/marken/123/alto/001.xml");
        sp.getContents().add(sconAlto1);

        SimpleContent sconAlto2 = new SimpleContent();
        sconAlto2.setFolder("alto");
        sconAlto2.setSource("/opt/digiverso/import/marken/123/alto/002.xml");
        sp.getContents().add(sconAlto2);

        SimpleContent sconAlto3 = new SimpleContent();
        sconAlto3.setFolder("alto");
        sconAlto3.setSource("/opt/digiverso/import/marken/123/alto/003.xml");
        sp.getContents().add(sconAlto3);

        // Data information
        SimpleData sd = new SimpleData();
        sd.setType("Monograph");
        
        // add files directly to data object
        sd.getContents().add(sconMedia1);
        sd.getContents().add(sconMedia2);
        sd.getContents().add(sconMedia3);
        
        // Subelement 1
        SimpleData sd1 = new SimpleData();
        sd1.setType("Chapter");
        sd1.add(ch.createMetadata("Titel", "TitleDocMain", "Teil 1"));
        sd1.add(ch.createMetadata("Schlagwort", "Subject", "Beginn"));
        
        SimpleContent sconMedia4 = new SimpleContent();
        sconMedia4.setFolder("media");
        sconMedia4.setLabel("3v");
        sconMedia4.setSource("/opt/digiverso/import/marken/123/media/004.jpg");
        sd1.getContents().add(sconMedia4);
        
        SimpleContent sconMedia5 = new SimpleContent();
        sconMedia5.setFolder("media");
        sconMedia5.setLabel("4r");
        sconMedia5.setSource("/opt/digiverso/import/marken/123/media/005.jpg");
        sd1.getContents().add(sconMedia5);
        
        SimpleContent sconMedia6 = new SimpleContent();
        sconMedia6.setFolder("media");
        sconMedia6.setLabel("4v");
        sconMedia6.setSource("/opt/digiverso/import/marken/123/media/006.jpg");
        sd1.getContents().add(sconMedia6);
        
        sd.getData().add(sd1);
        
        // Subelement 2
        SimpleData sd2 = new SimpleData();
        sd2.setType("Chapter");
        sd2.add(ch.createMetadata("Titel", "TitleDocMain", "Teil 2"));
        sd2.add(ch.createMetadata("Schlagwort", "Subject", "Mitte"));
        
        SimpleContent sconMedia7 = new SimpleContent();
        sconMedia7.setFolder("media");
        sconMedia7.setLabel("5r");
        sconMedia7.setSource("/opt/digiverso/import/marken/123/media/007.jpg");
        sd2.getContents().add(sconMedia7);
        
        SimpleContent sconMedia8 = new SimpleContent();
        sconMedia8.setFolder("media");
        sconMedia8.setLabel("5v");
        sconMedia8.setSource("/opt/digiverso/import/marken/123/media/008.jpg");
        sd2.getContents().add(sconMedia8);
        
        SimpleContent sconMedia9 = new SimpleContent();
        sconMedia9.setFolder("media");
        sconMedia9.setLabel("6r");
        sconMedia9.setSource("/opt/digiverso/import/marken/123/media/009.jpg");
        sd2.getContents().add(sconMedia9);
        
        sd.getData().add(sd2);
        
        // Subelement 3
        SimpleData sd3 = new SimpleData();
        sd3.setType("Chapter");
        sd3.add(ch.createMetadata("Titel", "TitleDocMain", "Teil 3"));
        sd3.add(ch.createMetadata("Schlagwort", "Subject", "Ende"));
        
        SimpleContent sconMedia10 = new SimpleContent();
        sconMedia10.setFolder("media");
        sconMedia10.setLabel("6v");
        sconMedia10.setSource("/opt/digiverso/import/marken/123/media/010.jpg");
        sd3.getContents().add(sconMedia7);
        
        SimpleContent sconMedia11 = new SimpleContent();
        sconMedia11.setFolder("media");
        sconMedia11.setLabel("7r");
        sconMedia11.setSource("/opt/digiverso/import/marken/123/media/011.jpg");
        sd3.getContents().add(sconMedia11);
        
        SimpleContent sconMedia12 = new SimpleContent();
        sconMedia12.setFolder("media");
        sconMedia12.setLabel("7v");
        sconMedia12.setSource("/opt/digiverso/import/marken/123/media/012.jpg");
        sd3.getContents().add(sconMedia12);
        
        sd.getData().add(sd3);
        
        
        // Subsubelement a
        SimpleData sd3a = new SimpleData();
        sd3a.setType("Chapter");
        sd3a.add(ch.createMetadata("Titel", "TitleDocMain", "Erster Abschnitt"));
        
        SimpleContent sconMedia13 = new SimpleContent();
        sconMedia13.setFolder("media");
        sconMedia13.setLabel("8r");
        sconMedia13.setSource("/opt/digiverso/import/marken/123/media/013.jpg");
        sd3a.getContents().add(sconMedia7);
        
        SimpleContent sconMedia14 = new SimpleContent();
        sconMedia14.setFolder("media");
        sconMedia14.setLabel("8v");
        sconMedia14.setSource("/opt/digiverso/import/marken/123/media/014.jpg");
        sd3a.getContents().add(sconMedia11);
        
        SimpleContent sconMedia15 = new SimpleContent();
        sconMedia15.setFolder("media");
        sconMedia15.setLabel("9r");
        sconMedia15.setSource("/opt/digiverso/import/marken/123/media/015.jpg");
        sd3a.getContents().add(sconMedia12);
        
        sd3.getData().add(sd3a);
        
        // Subsubelement b
        SimpleData sd3b = new SimpleData();
        sd3b.setType("Chapter");
        sd3b.add(ch.createMetadata("Titel", "TitleDocMain", "Zweiter Abschnitt"));
        
        SimpleContent sconMedia16 = new SimpleContent();
        sconMedia16.setFolder("media");
        sconMedia16.setLabel("9v");
        sconMedia16.setSource("/opt/digiverso/import/marken/123/media/016.jpg");
        sd3b.getContents().add(sconMedia7);
        
        SimpleContent sconMedia17 = new SimpleContent();
        sconMedia17.setFolder("media");
        sconMedia17.setLabel("10r");
        sconMedia17.setSource("/opt/digiverso/import/marken/123/media/017.jpg");
        sd3b.getContents().add(sconMedia11);
        
        SimpleContent sconMedia18 = new SimpleContent();
        sconMedia18.setFolder("media");
        sconMedia18.setLabel("10v");
        sconMedia18.setSource("/opt/digiverso/import/marken/123/media/018.jpg");
        sd3b.getContents().add(sconMedia12);
        
        sd3.getData().add(sd3b);
        
        
        
        sio.setData(sd);

        // Metadata 1
        sd.add(ch.createMetadata("Titel", "TitleDocMain", "Das Tagebuch der Anne Frank"));

        // Metadata 2
        sd.add(ch.createMetadataAuth("Sammlung", "singleDigCollection", "Tagebücher", "gnd", "http://d-nb.info/gnd/", "12345678"));

        // Metadata 3
        sd.add(ch.createMetadataAuth("Schlagwort", "Subject", "Krieg", "http://d-nb.info/gnd/12345678"));

        // Person 1
        sd.add(ch.createPerson("Verfasser", "Author", "Anne", "Frank"));

        // Person 2
        sd.add(ch.createPersonAuth("Grafiker", "Illustrator", "Peter", "Müller", "gnd", "http://d-nb.info/gnd/", "12345678"));

        // Person 3
        sd.add(ch.createPersonAuth("Drucker", "Printer", "Günther", "Druck", "http://d-nb.info/gnd/12345678"));

        // Corporate 1
        sd.add(ch.createCorporate("Location", "Universität Göttingen", "EventLocation"));

        // Corporate 2
        SimpleCorporate sc2 = ch.createCorporate("Location", "Universität Göttingen", "EventLocation");
        sc2.setPartname("Haus 3");
        sd.add(sc2);

        // Corporate 3
        SimpleCorporate sc3 = ch.createCorporate("Location", "Universität Göttingen", "EventLocation");
        sc3.setPartname("Haus 3");
        sc3.getSubnames().add("Institut für Informatik");
        sc3.getSubnames().add("Lehrstuhl für Softwareentwicklung");
        sd.add(sc3);

        // Corporate 4
        SimpleCorporate sc4 =
                ch.createCorporateAuth("Location", "Universität Göttingen", "EventLocation", "gnd", "http://d-nb.info/gnd/", "12345678");
        sc4.getSubnames().add("Institut für Informatik");
        sc4.getSubnames().add("Lehrstuhl für Softwareentwicklung");
        sc4.setPartname("Haus 3");
        sd.add(sc4);

        // Corporate 5
        SimpleCorporate sc5 = ch.createCorporateAuth("Location", "Universität Göttingen", "EventLocation", "http://d-nb.info/gnd/12345678");
        sc5.getSubnames().add("Institut für Informatik");
        sc5.getSubnames().add("Lehrstuhl für Softwareentwicklung");
        sc5.setPartname("Haus 3");
        sd.add(sc5);

        // Group 1
        SimpleGroup sg1 = new SimpleGroup();
        sg1.setLabel("Provenienz");
        sg1.setType("provenence");

        sg1.add(ch.createMetadata("Titel", "TitleDocMain", "Das Tagebuch der Anne Frank"));
        sg1.add(ch.createMetadataAuth("Sammlung", "singleDigCollection", "Tagebücher", "gnd", "http://d-nb.info/gnd/", "12345678"));
        sg1.add(ch.createMetadataAuth("Schlagwort", "Subject", "Krieg", "http://d-nb.info/gnd/12345678"));
        sg1.add(ch.createPerson("Verfasser", "Author", "Anne", "Frank"));
        sg1.add(ch.createPersonAuth("Grafiker", "Illustrator", "Peter", "Müller", "gnd", "http://d-nb.info/gnd/", "12345678"));
        sg1.add(ch.createPersonAuth("Drucker", "Printer", "Günther", "Druck", "http://d-nb.info/gnd/12345678"));
        sg1.add(ch.createCorporate("Location", "Universität Göttingen", "EventLocation"));
        sg1.add(sc2);
        sg1.add(sc3);
        sg1.add(sc4);
        sg1.add(sc5);
        sd.add(sg1);

        // Group 2
        SimpleGroup sg2 = new SimpleGroup();
        sg2.setLabel("Provenienz");
        sg2.setType("provenence");
        sg2.add(ch.createMetadata("Titel", "TitleDocMain", "Das Tagebuch der Anne Frank"));
        sg2.add(ch.createMetadataAuth("Sammlung", "singleDigCollection", "Tagebücher", "gnd", "http://d-nb.info/gnd/", "12345678"));
        sg2.add(ch.createMetadataAuth("Schlagwort", "Subject", "Krieg", "http://d-nb.info/gnd/12345678"));
        sg2.add(ch.createPerson("Verfasser", "Author", "Anne", "Frank"));
        sg2.add(ch.createPersonAuth("Grafiker", "Illustrator", "Peter", "Müller", "gnd", "http://d-nb.info/gnd/", "12345678"));
        sg2.add(ch.createPersonAuth("Drucker", "Printer", "Günther", "Druck", "http://d-nb.info/gnd/12345678"));
        sg2.add(ch.createCorporate("Location", "Universität Göttingen", "EventLocation"));
        sg2.add(sc2);
        sg2.add(sc3);
        sg2.add(sc4);
        sg2.add(sc5);
        sd.add(sg2);

        // Group 3
        SimpleGroup sg3 = new SimpleGroup();
        sg3.setLabel("Provenienz");
        sg3.setType("provenence");
        sg3.add(ch.createMetadata("Titel", "TitleDocMain", "Das Tagebuch der Anne Frank"));
        sg3.add(ch.createMetadataAuth("Sammlung", "singleDigCollection", "Tagebücher", "gnd", "http://d-nb.info/gnd/", "12345678"));
        sg3.add(ch.createMetadataAuth("Schlagwort", "Subject", "Krieg", "http://d-nb.info/gnd/12345678"));
        sg3.add(ch.createPerson("Verfasser", "Author", "Anne", "Frank"));
        sg3.add(ch.createPersonAuth("Grafiker", "Illustrator", "Peter", "Müller", "gnd", "http://d-nb.info/gnd/", "12345678"));
        sg3.add(ch.createPersonAuth("Drucker", "Printer", "Günther", "Druck", "http://d-nb.info/gnd/12345678"));
        sg3.add(ch.createCorporate("Location", "Universität Göttingen", "EventLocation"));
        sg3.add(sc2);
        sg3.add(sc3);
        sg3.add(sc4);
        sg3.add(sc5);
        sd.add(sg3);

        // add sub-groups into group 1
        sg1.add(sg2);
        sg1.add(sg3);

        // pass back the entire object
        return sio;
    }
}

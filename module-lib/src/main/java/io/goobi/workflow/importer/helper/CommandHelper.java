package io.goobi.workflow.importer.helper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import io.goobi.workflow.importer.model.SimpleCorporate;
import io.goobi.workflow.importer.model.SimpleData;
import io.goobi.workflow.importer.model.SimpleMetadata;
import io.goobi.workflow.importer.model.SimplePerson;

public class CommandHelper {

    /**
     * This function edits the strings of the ids so they match the folder names
     *
     * @param String: id
     */
    public String getFolderId(String id) {
        if (id.contains("_")) {
            String result = id.substring(id.lastIndexOf("_") + 1);
            while (result.startsWith("0") && result.length() > 1) {
                result = result.substring(1);
            }
            return result;
        }
        return id;
    }

    /**
     * get the first sub-directory with the given name recursively
     *
     * @param directory
     * @param fileName
     * @return
     * @throws IOException
     */
    public Optional<Path> findFileByName(Path directory, String fileName) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .findFirst();
        }
    }

    /**
     * create {@link SimpleMetadata}
     * 
     * @param label
     * @param type
     * @param content
     * @return
     */
    public SimpleMetadata createMetadata(String label, String type, String content) {
        SimpleMetadata sm = new SimpleMetadata();
        sm.setLabel(label);
        sm.setType(type);
        sm.setValue(content);
        return sm;
    }

    /**
     * create {@link SimpleMetadata} with simplified authority information
     * 
     * @param label
     * @param type
     * @param content
     * @param authorityEntryUrl
     * @return
     */
    public SimpleMetadata createMetadataAuth(String label, String type, String content, String authorityEntryUrl) {
        SimpleMetadata sm = new SimpleMetadata();
        sm.setLabel(label);
        sm.setType(type);
        sm.setValue(content);
        sm.analyzeAuthority(authorityEntryUrl);
        return sm;
    }

    /**
     * 
     * create {@link SimpleMetadata} with detailed authority information
     * 
     * @param label
     * @param type
     * @param content
     * @param authority
     * @param authorityURI
     * @param valueURI
     * @return
     */
    public SimpleMetadata createMetadataAuth(String label, String type, String content, String authority, String authorityURI, String valueURI) {
        SimpleMetadata sm = new SimpleMetadata();
        sm.setLabel(label);
        sm.setType(type);
        sm.setValue(content);
        sm.setAuthority(authority);
        sm.setAuthorityURI(authorityURI);
        sm.setValueURI(valueURI);
        return sm;
    }

    /**
     * create {@link SimplePerson}
     * 
     * @param type
     * @param fullName
     * @return
     */
    public SimplePerson createPerson(String label, String type, String fullName) {
        SimplePerson person = new SimplePerson();
        person.setLabel(type);
        person.setRole(type);
        person.analyzeFullName(fullName);
        return person;
    }

    /**
     * create {@link SimplePerson}
     * 
     * @param type
     * @param fullName
     * @return
     */
    public SimplePerson createPerson(String label, String type, String firstName, String lastName) {
        SimplePerson person = new SimplePerson();
        person.setLabel(type);
        person.setRole(type);
        person.setFirstname(firstName);
        person.setLastname(lastName);
        return person;
    }

    /**
     * create {@link SimplePerson} with simplified authority information
     * 
     * @param type
     * @param fullName
     * @param authorityEntryUrl
     * @return
     */
    public SimplePerson createPersonAuth(String label, String type, String fullName, String authorityEntryUrl) {
        SimplePerson person = new SimplePerson();
        person.setLabel(type);
        person.setRole(type);
        person.analyzeFullName(fullName);
        person.analyzeAuthority(authorityEntryUrl);
        return person;
    }

    /**
     * create {@link SimplePerson} with simplified authority information
     * 
     * @param type
     * @param firstName
     * @param lastName
     * @param authorityEntryUrl
     * @return
     */
    public SimplePerson createPersonAuth(String label, String type, String firstName, String lastName, String authorityEntryUrl) {
        SimplePerson person = new SimplePerson();
        person.setLabel(type);
        person.setRole(type);
        person.setFirstname(firstName);
        person.setLastname(lastName);
        person.analyzeAuthority(authorityEntryUrl);
        return person;
    }

    /**
     * create {@link SimplePerson} with simplified authority information
     * 
     * @param type
     * @param firstName
     * @param lastName
     * @param authority
     * @param authorityURI
     * @param valueURI
     * @return
     */
    public SimplePerson createPersonAuth(String label, String type, String fullName, String authority, String authorityURI, String valueURI) {
        SimplePerson person = new SimplePerson();
        person.setLabel(type);
        person.setRole(type);
        person.analyzeFullName(fullName);
        person.setAuthority(authority);
        person.setAuthorityURI(authorityURI);
        person.setValueURI(valueURI);
        return person;
    }

    /**
     * create {@link SimplePerson} with simplified authority information
     * 
     * @param type
     * @param firstName
     * @param lastName
     * @param authority
     * @param authorityURI
     * @param valueURI
     * @return
     */
    public SimplePerson createPersonAuth(String label, String type, String firstName, String lastName, String authority, String authorityURI,
            String valueURI) {
        SimplePerson person = new SimplePerson();
        person.setLabel(type);
        person.setRole(type);
        person.setFirstname(firstName);
        person.setLastname(lastName);
        person.setAuthority(authority);
        person.setAuthorityURI(authorityURI);
        person.setValueURI(valueURI);
        return person;
    }

    /**
     * add a corporate to {@link SimpleData}
     *
     * @param sd
     * @param name
     * @param gndId
     * @param role
     * @param subnames
     */
    public SimpleCorporate createCorporate(String label, String role, String name) {
        SimpleCorporate sc = new SimpleCorporate();
        sc.setLabel(label);
        sc.setName(name);
        sc.setRole(role);
        return sc;
    }

    /**
     * add a corporate to {@link SimpleData} with authority
     *
     * @param sd
     * @param name
     * @param gndId
     * @param role
     * @param subnames
     */
    public SimpleCorporate createCorporateAuth(String label, String role, String name, String authorityEntryUrl) {
        SimpleCorporate sc = new SimpleCorporate();
        sc.setLabel(label);
        sc.setName(name);
        sc.setRole(role);
        sc.analyzeAuthority(authorityEntryUrl);
        return sc;
    }

    /**
     * add a corporate to {@link SimpleData} with authority
     *
     * @param sd
     * @param name
     * @param gndId
     * @param role
     * @param subnames
     */
    public SimpleCorporate createCorporateAuth(String label, String role, String name, String authority, String authorityURI,
            String valueURI) {
        SimpleCorporate sc = new SimpleCorporate();
        sc.setLabel(label);
        sc.setName(name);
        sc.setRole(role);
        sc.setAuthority(authority);
        sc.setAuthorityURI(authorityURI);
        sc.setValueURI(valueURI);
        return sc;
    }
}


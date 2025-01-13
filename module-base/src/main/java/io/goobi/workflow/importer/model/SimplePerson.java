package io.goobi.workflow.importer.model;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimplePerson {

    @JacksonXmlProperty(isAttribute = true)
    private String label;

    @JacksonXmlProperty(isAttribute = true)
    private String authority;

    @JacksonXmlProperty(isAttribute = true)
    private String authorityURI;

    @JacksonXmlProperty(isAttribute = true)
    private String valueURI;

    @JacksonXmlProperty(isAttribute = true)
    private String role;

    @JacksonXmlProperty(isAttribute = true)
    private String firstname;

    @JacksonXmlProperty(isAttribute = true)
    private String lastname;

    /**
     * split given fullname into first and lastname if the given string looks like "Lastname Firstname1 Firstname2" or like "Lastname, Firstname1
     * Firstname2"
     * 
     * @param fullName
     */
    public void analyzeFullName(String fullName) {
        firstname = "";
        lastname = fullName;

        int spaceIndex = fullName.indexOf(" ");
        if (spaceIndex != -1) {
            firstname = fullName.substring(spaceIndex + 1).trim();
            lastname = fullName.substring(0, spaceIndex).trim();
        }

        if (lastname.endsWith(",")) {
            lastname = lastname.substring(0, lastname.length() - 1).trim();
        }
    }

    /**
     * add authority information based on just one url
     * 
     * @param authorityEntryUrl
     */
    public void analyzeAuthority(String authorityEntryUrl) {
        authority = null;
        authorityURI = null;
        valueURI = null;

        // split authority information
        if (StringUtils.isNotBlank(authorityEntryUrl)) {
            if (authorityEntryUrl.contains("d-nb.info")) {
                authority = "gnd";
            }
            if (authorityEntryUrl.contains("/")) {
                authorityURI = authorityEntryUrl.substring(0, authorityEntryUrl.lastIndexOf("/") + 1);
                valueURI = authorityEntryUrl.substring(authorityEntryUrl.lastIndexOf("/") + 1);
            }
        }
    }

}
